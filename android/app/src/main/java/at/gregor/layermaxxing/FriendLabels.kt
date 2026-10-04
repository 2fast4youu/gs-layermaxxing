package at.gregor.layermaxxing

import android.content.Context
import org.json.JSONObject

/**
 * Personal names for friends ("Verlobte", "Schwester", "Allerbester Freund").
 * Only the user sees them, so they stay on the device – no server round-trip.
 */
internal object FriendLabels {
    val suggestions = listOf("Allerbester Freund", "Beste Freundin", "Verlobte", "Partner", "Schwester", "Bruder", "Schwesterherz", "Süßer", "Familie", "Kollege")
    private const val MAX = 28

    fun clean(raw: String?): String? = raw?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX)?.ifBlank { null }

    fun parse(json: String?): Map<Long, String> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { id -> clean(o.optString(k))?.let { id to it } } }.toMap()
    }.getOrDefault(emptyMap())

    fun encode(map: Map<Long, String>): String = JSONObject().apply { map.forEach { (k, v) -> put(k.toString(), v) } }.toString()

    /** Text under a friend's island: the personal name wins over the level name. */
    fun display(label: String?, level: Int): String = label ?: Isle.levelName(level)

    private fun prefs(c: Context) = c.getSharedPreferences("layermaxxing_friend_labels", Context.MODE_PRIVATE)
    fun load(c: Context, account: String): Map<Long, String> = parse(prefs(c).getString("labels_$account", null))
    fun save(c: Context, account: String, map: Map<Long, String>) { prefs(c).edit().putString("labels_$account", encode(map)).apply() }
}
