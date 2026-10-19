package at.gregor.layermaxxing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 7.9.13: whose island a loaded state is. Server (profile key + base URL), account and the real
 * user id together: two servers may both have a user 1, and their islands must never mix.
 */
data class IslandOwner(val scope: String, val userId: Long)

/**
 * 7.9.13: device storage for [IslandCache], so the last real island also shows at once after the app process was
 * restarted. One entry per owner; only the island state the server sent, never a token.
 */
interface IslandDisk {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>
}

/**
 * The last island the server really sent, in memory for the running app and (with [disk]) on the device, so
 * navigation, rotation and a process restart show it at once. Only island state is kept: no token, no letters, no logs.
 */
object IslandCache {
    private val islands = ConcurrentHashMap<IslandOwner, ApiClient.HomeIsland>()
    @Volatile var disk: IslandDisk? = null

    fun get(owner: IslandOwner?): ApiClient.HomeIsland? {
        val o = owner ?: return null
        islands[o]?.takeIf { it.userId == o.userId }?.let { return it }
        val stored = disk?.let { d -> d.read(key(o))?.let { raw -> IslandCodec.decode(raw, o).also { if (it == null) d.remove(key(o)) } } }
        return stored?.also { islands[o] = it }
    }

    /** Only ever called with a server answer; one for another user is never filed under this owner. */
    fun put(owner: IslandOwner?, island: ApiClient.HomeIsland) {
        if (owner == null || island.userId != owner.userId) return
        islands[owner] = island
        disk?.write(key(owner), IslandCodec.encode(owner, island))
    }

    /** Logout or a removed account: its islands are gone from this device's memory and storage. */
    fun forget(scope: String) {
        islands.keys.removeAll { it.scope == scope }
        disk?.let { d -> d.keys().filter { it.startsWith(PREFIX) && it.substring(PREFIX.length).substringBeforeLast(SEP) == scope }.forEach(d::remove) }
    }

    fun clear() {
        islands.clear()
        disk?.let { d -> d.keys().filter { it.startsWith(PREFIX) }.forEach(d::remove) }
    }

    /** Drops the memory copy only, as a process restart does (tests). */
    internal fun dropMemory() = islands.clear()

    fun scope(profileKey: String, baseUrl: String, account: String) = "$profileKey|$baseUrl|$account"

    internal fun key(owner: IslandOwner) = PREFIX + owner.scope + SEP + owner.userId
    private const val PREFIX = "island_v1|"
    private const val SEP = "#"
}

/** The server's own island format (as [ApiClient.parseHome] reads it), wrapped with the owner it belongs to. */
object IslandCodec {
    private const val VERSION = 1

    fun encode(owner: IslandOwner, island: ApiClient.HomeIsland): String =
        JSONObject().put("v", VERSION).put("scope", owner.scope).put("user_id", owner.userId).put("island", json(island)).toString()

    /** null for anything that is not exactly this owner's stored island (other scope/user, old format, broken). */
    fun decode(raw: String, owner: IslandOwner): ApiClient.HomeIsland? = runCatching {
        val j = JSONObject(raw)
        if (j.optInt("v") != VERSION || j.optString("scope") != owner.scope || j.optLong("user_id") != owner.userId) return null
        ApiClient.parseHome(j.getJSONObject("island")).takeIf { it.userId == owner.userId }
    }.getOrNull()

    fun json(h: ApiClient.HomeIsland): JSONObject {
        val j = JSONObject().put("user_id", h.userId).put("score", h.score).put("plots", h.plots)
            .put("decor", JSONObject().apply { h.decor.forEach { (k, v) -> put(k.toString(), v) } })
            .put("items", JSONArray().apply { h.items.forEach { put(JSONObject().put("key", it.key).put("unlock_at", it.unlockAt).put("unlocked", it.unlocked)) } })
            .put("places", JSONObject().apply { h.places.forEach { (k, v) -> put(k.toString(), JSONObject().put("kind", v.kind).put("name", v.name)) } })
            .put("plot_unlocks", JSONArray(h.plotUnlocks))
        h.here?.let { j.put("here", JSONObject().put("plot", it.plot ?: JSONObject.NULL).put("status", it.status)) }
        val c = h.construction
        if (c.supported) {
            val cj = JSONObject().put("mode", c.mode).put("buildings", JSONObject(c.buildings as Map<*, *>))
                .put("land", c.land).put("spent", c.spent).put("revision", c.revision).put("available", c.available).put("age_days", c.ageDays)
                .put("build_costs", JSONArray(c.buildCosts)).put("build_days", JSONArray(c.buildDays))
                .put("land_costs", JSONArray(c.landCosts)).put("land_days", JSONArray(c.landDays))
                .put("positions", JSONObject().apply { c.positions.forEach { (k, v) -> put(k, JSONArray(v)) } })
                .put("decor", JSONArray().apply { c.decor.forEach { d ->
                    put(JSONObject().put("id", d.id).put("item", d.item).put("position", JSONArray(d.position)).put("slot", d.slot ?: JSONObject.NULL).put("name", d.name))
                } })
                .put("decor_limits", JSONArray(c.decorLimits))
                .put("special_costs", JSONObject(c.specialCosts as Map<*, *>)).put("special_limit", c.specialLimit)
                .put("starters", JSONArray(c.starters.sorted()))
            // A missing rule stays missing (LEGACY); never stored as a newer rule than the server named.
            if (c.gridVersion != PlacementRule.LEGACY) cj.put("grid_version", c.gridVersion)
            j.put("construction", cj)
        }
        return j
    }
}

/** Where loading the own island stands; never a made-up island. */
data class IslandLoad(
    val loading: Boolean = false,
    /** A fresh answer for this owner arrived in this app session: only then writes are allowed. */
    val confirmed: Boolean = false,
    val error: String? = null,
    val failures: Int = 0,
)

object IslandSync {
    /** One answer may take this long; then the attempt counts as failed (no endless spinner). */
    const val TIMEOUT_MS = 15_000L
    /** Short, bounded retries after a failure, then the normal calm refresh. */
    val RETRY_MS = listOf(2_000L, 5_000L, 10_000L, 20_000L)
    const val REFRESH_MS = 60_000L
    const val NOT_LOADED = "Noch kein Stand geladen"
    const val NOT_CONFIRMED = "Die Insel wird gerade abgeglichen – Bauen geht gleich wieder."

    fun retryDelay(failures: Int): Long = if (failures <= 0) REFRESH_MS else RETRY_MS.getOrElse(failures - 1) { REFRESH_MS }

    /**
     * One bounded load. Cancellation of the caller is passed on, only the own timeout becomes a failure.
     * A missing answer (null) is a failure too: it never turns into an empty island.
     */
    suspend fun attempt(timeoutMs: Long = TIMEOUT_MS, load: suspend () -> ApiClient.HomeIsland?): Result<ApiClient.HomeIsland> =
        try {
            val loaded = withTimeout(timeoutMs) { load() }
            if (loaded != null) Result.success(loaded) else Result.failure(IllegalStateException(EMPTY))
        } catch (e: TimeoutCancellationException) {
            Result.failure(IslandTimeout())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun message(error: Throwable): String = when {
        error is IslandTimeout -> "Die Insel antwortet gerade nicht (Zeitüberschreitung)."
        error.message == EMPTY -> "Der Server hat keine Insel geliefert."
        ApiErrors.isOffline(error.message) -> "Offline: " + ApiErrors.friendly(error.message)
        else -> ApiErrors.friendly(error.message)
    }

    /** The line under "Meine Insel": honest about what is shown and how fresh it is. */
    fun status(hasIsland: Boolean, ownerKnown: Boolean, load: IslandLoad): String? = when {
        !hasIsland && !ownerKnown -> if (load.error != null) "Konto nicht erreichbar · $NOT_LOADED" else "Konto wird geladen…"
        !hasIsland && load.error != null -> "$NOT_LOADED · ${load.error}"
        !hasIsland -> "$NOT_LOADED · Insel wird geladen…"
        load.error != null -> "Letzter Stand · nicht aktuell"
        load.loading -> "Aktualisiert…"
        else -> null
    }

    private const val EMPTY = "island-empty"
}

class IslandTimeout : Exception("[LM-NET-TIMEOUT] Zeitüberschreitung beim Laden der Insel.")
