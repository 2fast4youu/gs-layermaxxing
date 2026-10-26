package at.gregor.layermaxxing

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Actual ApiClient parser and request writer, entirely intercepted in memory before DNS/socket access.
 * Metadata and island responses are synthetic, with grid_version=7 from the public contract in TASK.md. */
class IslandEdgeApiTest {
    private class Transport(val version: Int = 7) {
        val writes = mutableListOf<JSONObject>()
        val api = ApiClient("https://grid7-synthetic.invalid/", OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> = error("Grid7 tests must never resolve DNS")
            })
            .addInterceptor { chain ->
                val request = chain.request()
                val json = when (request.url.encodedPath) {
                    "/api/server-info" -> JSONObject("""{"name":"Synthetic grid7","role":"test","version":"synthetic","warning":"","features":["build_grid_v7"],"grid_version":7}""")
                    "/api/island/1" -> Grid7Contract.homeJson(version = version.toString())
                    "/api/island/construction" -> {
                        check(request.method == "POST")
                        val buffer = Buffer(); request.body!!.writeTo(buffer)
                        writes += JSONObject(buffer.readUtf8())
                        Grid7Contract.homeJson(version = version.toString())
                    }
                    else -> error("Unexpected synthetic request: ${request.url.encodedPath}")
                }
                // Never chain.proceed: no real transport, authentication or storage.
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic")
                    .body(json.toString().toResponseBody("application/json".toMediaType())).build()
            }.build())
    }

    @Test fun realApiParses7AndSerializesExactBuildMoveAndUpgradeRequests() = runBlocking {
        val t = Transport(); val api = t.api
        val metadata = api.serverInfo()
        assertEquals(7, metadata.gridVersion); assertTrue("build_grid_v7" in metadata.features)
        assertNull(PlacementRule.problem(metadata.gridVersion!!))
        val h = api.island("synthetic-test-token", 1)
        val c = h.construction
        assertEquals(7, c.gridVersion); assertTrue(t.writes.isEmpty())
        val empty = c.copy(buildings = emptyMap(), positions = emptyMap())
        val actions = listOf(
            empty to ApiClient.IslandRequest("build", "LIGHTHOUSE", position = Grid7Contract.spots(0, 5).first()),
            c to ApiClient.IslandRequest("move", "LIGHTHOUSE", position = Grid7Contract.spots(1, 5).first()),
            c to ApiClient.IslandRequest("upgrade", "LIGHTHOUSE"), // saved inland position, no relocation
            c to ApiClient.IslandRequest("upgrade", "LIGHTHOUSE", position = Grid7Contract.spots(2, 5).first()))
        for ((state, req) in actions) {
            api.islandAction("synthetic-test-token", state, req)
            val wire = t.writes.last()
            assertEquals(req.action, wire.getString("action")); assertEquals("LIGHTHOUSE", wire.getString("building"))
            assertEquals(state.revision, wire.getInt("expected_revision")); assertEquals(state.mode, wire.getString("mode"))
            val p = wire.getJSONArray("position").let { listOf(it.getInt(0), it.getInt(1)) }
            assertEquals(req.position ?: state.positions.getValue("LIGHTHOUSE"), p)
            val stage = when (req.action) { "build" -> 0; "upgrade" -> 2; else -> 1 }
            assertTrue(Grid7Contract.fits("building:LIGHTHOUSE:$stage", 5, p))
            if (req.action != "upgrade" || p != state.positions["LIGHTHOUSE"]) assertTrue(p in Grid7Contract.spots(stage, 5))
        }
        assertEquals(4, t.writes.size)
        assertEquals(listOf(10,10), c.positions["LIGHTHOUSE"]); assertEquals(1, c.buildings["LIGHTHOUSE"])
    }

    @Test fun canonicalApiRejectsInlandMovesRelocatedUpgradesOccupiedAndUnknownBeforeTransport() = runBlocking {
        val t = Transport(); val c = Grid7Contract.home().construction
        val inland = listOf(10,10); val other = listOf(11,10)
        val edge = Grid7Contract.spots(1, 5).first()
        val empty = c.copy(buildings = emptyMap(), positions = emptyMap())
        val attempts = listOf(
            c to ApiClient.IslandRequest("move", "LIGHTHOUSE", position = inland),
            c to ApiClient.IslandRequest("upgrade", "LIGHTHOUSE", position = other),
            empty to ApiClient.IslandRequest("build", "LIGHTHOUSE", position = inland),
            c.copy(decor = listOf(ApiClient.IslandDecor(100, "bench", edge))) to ApiClient.IslandRequest("move", "LIGHTHOUSE", position = edge)) +
            listOf("9", "99", "\"7\"", "7.0", "true", "null").map { raw ->
                Grid7Contract.home(version = raw).construction to ApiClient.IslandRequest("move", "LIGHTHOUSE", position = edge)
            }
        for ((state, req) in attempts) {
            val failure = runCatching { t.api.islandAction("synthetic-test-token", state, req) }.exceptionOrNull()
            assertTrue("${state.gridVersion} $req should fail locally, got $failure", failure is IllegalArgumentException)
            assertTrue("invalid placement reached the writer", t.writes.isEmpty())
        }
    }
    @Test fun version8ParsesAndWritesEveryUpgradeInPlaceWithoutClearingLegacyNeighbours() = runBlocking {
        val t = Transport(8)
        val parsed = t.api.island("synthetic-test-token", 1)
        assertEquals(8, parsed.construction.gridVersion)
        for (b in IsleBuilding.entries) for (stage in 0..2) {
            val p = listOf(0, 0) // saved out-of-zone legacy position is intentionally preserved
            val c = parsed.construction.copy(buildings = if (stage == 0) emptyMap() else mapOf(b.name to stage),
                starters = if (stage == 0) setOf(b.name) else emptySet(), positions = mapOf(b.name to p),
                decor = listOf(ApiClient.IslandDecor(100, "lantern", p)))
            val count = t.writes.size
            t.api.islandAction("synthetic-test-token", c, ApiClient.IslandRequest("upgrade", b.name))
            assertEquals(count + 1, t.writes.size)
            val wire = t.writes.last()
            assertEquals("upgrade", wire.getString("action"))
            assertEquals(b.name, wire.getString("building"))
            assertEquals("[0,0]", wire.getJSONArray("position").toString())
            assertEquals(c.revision, wire.getInt("expected_revision"))
            // A move still validates terrain, even when its destination equals the saved position.
            val error = runCatching { t.api.islandAction("synthetic-test-token", c,
                ApiClient.IslandRequest("move", b.name, position = p)) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertEquals(count + 1, t.writes.size)
            assertEquals(p, c.positions[b.name]); assertEquals(1, c.decor.size)
        }
        assertEquals(IsleBuilding.entries.size * 3, t.writes.size)
    }

}
