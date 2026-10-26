package at.gregor.layermaxxing

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 7.9.11-raster: the placement rule is the server's (construction.grid_version) and the grid draws only the
 * real targets of the held piece. For every rule the app knows (2, 3, 4), every land stage and every kind of piece:
 * [IslandLayout.ruleProblem] ⇔ the server rule (re-implemented here independently from server/app/island_grid.json),
 * kept historically exact. 7.9.12: the drawn targets ⇔ [IslandLayout.valid] ⇔ that rule AND the graphic fit
 * (a subset, never more than the old server accepts); rule 5 (the masks) ⇔ a reference computed from
 * server/app/geometry/island_masks.json. Unknown or missing versions never offer a spot.
 */
class IslandRasterTest {
    private val json by lazy {
        JSONObject(listOf("../../server/app/island_grid.json", "server/app/island_grid.json", "../server/app/island_grid.json")
            .map(::File).first { it.exists() }.readText())
    }
    private val cellCache = HashMap<String, Set<List<Int>>>()
    private fun cells(key: String, land: Int): Set<List<Int>> = cellCache.getOrPut("$key$land") {
        json.getJSONObject(key).getJSONArray("$land").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { c -> listOf(c.getInt(0), c.getInt(1)) } }.toSet() }
    }
    private val ringCache = HashMap<String, List<Int>>()
    private fun rings(version: Int, key: String): List<Int> = ringCache.getOrPut("$version$key") {
        json.getJSONObject(if (version == 3) "footprints_v3" else "footprints_v4").getJSONArray(key).let { a -> (0 until a.length()).map { a.getInt(it) } }
    }

    // ---------------------------------------------------------------- the server rules, from the fixture

    private fun diamond(spot: List<Int>, r: Int): Set<List<Int>> =
        buildSet { for (dx in -r..r) for (dy in -r..r) if (kotlin.math.abs(dx) + kotlin.math.abs(dy) <= r) add(listOf(spot[0] + dx, spot[1] - r + dy)) }

    /** One piece as the server stores it: kind "big" (stage 1..3), "starter", "special" or "decor". */
    private data class Piece(val key: String, val kind: String, val spot: List<Int>)

    private fun pieces(c: ApiClient.IslandConstruction): List<Piece> =
        IsleBuilding.entries.filter { c.standing(it) }.map { b ->
            Piece("b:${b.name}", if (b.name in c.starters && (c.buildings[b.name] ?: 0) == 0) "starter" else "big", c.positions[b.name] ?: IslandLayout.defaultPosition(b))
        } + c.decor.map { d -> Piece("d:${d.id}", if (SpecialDecor.isSpecial(d.item)) "special" else "decor", d.position) }

    /** server placement_blocked, version 3/4: footprint diamonds share a cell. */
    private fun blockedDiamonds(version: Int, land: Int, held: Piece, others: List<Piece>): Boolean {
        fun r(kind: String) = rings(version, if (kind == "special") "big" else kind)[land]
        return others.any { diamond(held.spot, r(held.kind)).intersect(diamond(it.spot, r(it.kind))).isNotEmpty() }
    }

    /** server 7.9.7–7.9.9 placement_blocked (version 2): squared distance below the clearance / land factor. */
    private fun blockedCircles(land: Int, held: Piece, others: List<Piece>): Boolean {
        val v2 = json.getJSONObject("legacy_v2")
        val f = v2.getJSONArray("land_factor").getDouble(land)
        fun sq(x: Double) = (x / f) * (x / f)
        val big = sq(v2.getDouble("big")); val gapB = sq(v2.getDouble("decor_building")); val gapD = sq(v2.getDouble("decor_decor"))
        val kindBuilding = held.kind != "decor"
        val small = held.kind == "starter"
        return others.any { o ->
            val d = ((held.spot[0] - o.spot[0]) * (held.spot[0] - o.spot[0]) + (held.spot[1] - o.spot[1]) * (held.spot[1] - o.spot[1])).toDouble()
            val gap = when (o.kind) {
                "big", "starter" -> if (!kindBuilding || small || o.kind == "starter") gapB else big
                "special" -> if (small) gapB else if (kindBuilding) big else gapB
                else -> if (kindBuilding) gapB else gapD
            }
            d < gap
        }
    }

    /** The reference: may the held piece stand at [p]? (grid, coast for the harbour, no blocking by any OTHER piece) */
    private fun reference(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>): Boolean {
        val land = c.land
        if (p !in cells("cells", land)) return false
        if (t == IslandTarget.Building(IsleBuilding.HARBOUR) && p !in cells("coast", land)) return false
        val all = pieces(c)
        val held = when (t) {
            is IslandTarget.Building -> Piece("b:${t.building.name}", if (!c.standing(t.building) || (t.building.name in c.starters && c.level(t.building) == 0)) "starter" else "big", p)
            is IslandTarget.Decor -> Piece("d:${t.id}", if (SpecialDecor.isSpecial(t.item)) "special" else "decor", p)
        }
        val others = all.filter { it.key != held.key }
        return !when (c.gridVersion) {
            2 -> blockedCircles(land, held, others)
            3, 4 -> blockedDiamonds(c.gridVersion, land, held, others)
            else -> true
        }
    }

    // ---------------------------------------------------------------- rule 5 (7.9.12), from the masks fixture

    private val masks by lazy {
        JSONObject(listOf("../../server/app/geometry/island_masks.json", "server/app/geometry/island_masks.json", "../server/app/geometry/island_masks.json")
            .map(::File).first { it.exists() }.readText()).getJSONObject("pieces")
    }
    private fun maskKey(c: ApiClient.IslandConstruction, t: IslandTarget): String = when (t) {
        is IslandTarget.Building -> "building:${t.building.name}:${if (c.standing(t.building)) c.level(t.building) else 0}"
        is IslandTarget.Decor -> if (masks.has("decor:${t.item}")) "decor:${t.item}" else if (t.item.startsWith("place_")) "decor:?special" else "decor:?"
    }
    private fun maskCells(key: String, land: Int, spot: List<Int>, part: String = "mask"): Set<List<Int>> =
        masks.getJSONObject(key).getJSONArray("lands").getJSONObject(land).getJSONArray(part).let { a ->
            (0 until a.length()).map { a.getJSONArray(it).let { o -> listOf(spot[0] + o.getInt(0), spot[1] + o.getInt(1)) } }.toSet()
        }
    /** The graphic fit, from the fixtures alone: ground on the land (harbour: coast, water in front, pier on land), no shared cell. */
    private fun fits(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>): Boolean {
        val land = c.land
        val key = maskKey(c, t)
        val base = maskCells(key, land, p, "base")
        val ok = if (t == IslandTarget.Building(IsleBuilding.HARBOUR)) p in cells("coast", land) && listOf(p[0], p[1] + 1) !in cells("cells", land) &&
            base.filter { it[1] < p[1] }.all { it in cells("cells", land) } else base.all { it in cells("cells", land) }
        if (!ok || p !in cells("cells", land)) return false
        val mine = maskCells(key, land, p)
        val others = IsleBuilding.entries.filter { c.standing(it) && IslandTarget.Building(it) != t }.map { b ->
            maskCells(maskKey(c, IslandTarget.Building(b)), land, c.positions[b.name] ?: IslandLayout.defaultPosition(b))
        } + c.decor.filter { !(t is IslandTarget.Decor && t.id != null && t.id == it.id) }.map { d ->
            maskCells(maskKey(c, IslandTarget.Decor(d.item, d.id)), land, d.position)
        }
        return others.none { o -> o.any { it in mine } }
    }

    // ---------------------------------------------------------------- fixtures

    /** Every kind of piece on one island: big buildings, stage-0 pieces, harbour, decoration, special and unknown legacy decoration. */
    private fun populated(land: Int, version: Int): ApiClient.IslandConstruction {
        val coast = cells("coast", land).sortedWith(compareBy({ it[0] }, { it[1] }))
        return ApiClient.IslandConstruction(land = land, gridVersion = version,
            buildings = mapOf("HOUSE" to 2, "LIBRARY" to 1, "HARBOUR" to 1), starters = setOf("POST", "CAMPFIRE"),
            positions = mapOf("HOUSE" to listOf(10, 6), "LIBRARY" to listOf(12, 12), "POST" to listOf(7, 9), "CAMPFIRE" to listOf(9, 12),
                "HARBOUR" to coast[coast.size / 2]),
            decor = listOf(ApiClient.IslandDecor(100, "bench", listOf(11, 9)), ApiClient.IslandDecor(101, "place_hut", listOf(13, 8), name = "Hütte"),
                ApiClient.IslandDecor(102, "gnome_legacy", listOf(8, 11))))
    }
    private val held = listOf(
        IslandTarget.Building(IsleBuilding.POST), IslandTarget.Building(IsleBuilding.HOUSE), IslandTarget.Building(IsleBuilding.HARBOUR),
        IslandTarget.Building(IsleBuilding.LIGHTHOUSE), // not standing: a new stage-0 piece
        IslandTarget.Decor("bench", 100), IslandTarget.Decor("place_hut", 101), IslandTarget.Decor("lantern"), IslandTarget.Decor("place_uni"),
    )

    /** 7.9.12: the same kinds of pieces packed (greedy, from the fixtures) so that no two pictures overlap. */
    private fun populatedFit(land: Int, version: Int = PlacementRule.MASKS): ApiClient.IslandConstruction {
        var c = ApiClient.IslandConstruction(land = land, gridVersion = version, buildings = mapOf("HOUSE" to 2, "LIBRARY" to 1, "HARBOUR" to 1),
            starters = setOf("POST", "CAMPFIRE"))
        val order: List<IslandTarget> = listOf(IslandTarget.Building(IsleBuilding.HARBOUR), IslandTarget.Building(IsleBuilding.HOUSE),
            IslandTarget.Building(IsleBuilding.LIBRARY), IslandTarget.Decor("place_hut", 101), IslandTarget.Building(IsleBuilding.POST),
            IslandTarget.Building(IsleBuilding.CAMPFIRE), IslandTarget.Decor("bench", 100), IslandTarget.Decor("gnome_legacy", 102))
        // pieces not placed yet do not count: start with the buildings only once they have a spot
        var placed = ApiClient.IslandConstruction(land = land, gridVersion = version)
        for (t in order) {
            val candidates = (if (t == IslandTarget.Building(IsleBuilding.HARBOUR)) cells("coast", land) else cells("cells", land)).sortedWith(compareBy({ it[1] }, { it[0] }))
            val probe = when (t) {
                is IslandTarget.Building -> placed.copy(buildings = placed.buildings + (c.buildings[t.building.name]?.let { mapOf(t.building.name to it) } ?: emptyMap()),
                    starters = placed.starters + (if (t.building.name in c.starters) setOf(t.building.name) else emptySet()))
                is IslandTarget.Decor -> placed
            }
            val spot = candidates.first { p ->
                fits(when (t) { is IslandTarget.Building -> probe.copy(positions = probe.positions + (t.building.name to p)); else -> probe }, t, p)
            }
            placed = when (t) {
                is IslandTarget.Building -> probe.copy(positions = probe.positions + (t.building.name to spot))
                is IslandTarget.Decor -> probe.copy(decor = probe.decor + ApiClient.IslandDecor(t.id!!, t.item, spot, name = if (t.id == 101) "Hütte" else ""))
            }
        }
        return placed
    }

    // ---------------------------------------------------------------- version contract

    @Test fun theVersionComesFromTheServerAndAMissingFieldIsLegacyNeverTheNewest() {
        val api = ApiClient("https://example.invalid/")
        fun parse(extra: String) = api.parseHomeIsland(JSONObject("""{"user_id":1,"decor":{},"score":0,"items":[],"construction":{"land":2$extra}}""")).construction.gridVersion
        assertEquals(2, parse(""","grid_version":2"""))
        assertEquals(3, parse(""","grid_version":3"""))
        assertEquals(4, parse(""","grid_version":4"""))
        assertEquals(9, parse(""","grid_version":9"""))
        assertEquals(PlacementRule.LEGACY, parse(""))
        assertEquals(PlacementRule.LEGACY, parse(""","grid_version":null"""))
        assertEquals(PlacementRule.INVALID, parse(""","grid_version":"v4""""))
        assertFalse(PlacementRule.known(parse(""","grid_version":"v4"""")))
        // no construction block at all (very old server)
        assertEquals(PlacementRule.LEGACY, api.parseHomeIsland(JSONObject("""{"user_id":1,"decor":{},"score":0,"items":[]}""")).construction.gridVersion)
        // local fixtures (constructor default) use the current rule on purpose
        assertEquals(PlacementRule.CURRENT, ApiClient.IslandConstruction().gridVersion)
        // 7.9.12: rule 5, the graphic masks; 7.9.13: the newest rule is 7, adding lighthouse coast contact
        assertEquals(7, PlacementRule.CURRENT)
        assertEquals(6, parse(""","grid_version":6"""))
        assertEquals(5, parse(""","grid_version":5"""))
    }

    @Test fun onlyAPlainJsonIntegerIsAVersionNothingIsCoercedOntoAKnownRule() {
        val api = ApiClient("https://example.invalid/")
        fun parse(raw: String) = api.parseHomeIsland(JSONObject("""{"user_id":1,"decor":{},"score":0,"items":[],"construction":{"land":2,"grid_version":$raw}}""")).construction.gridVersion
        // exact integers stay exactly what they are
        assertEquals(2, parse("2")); assertEquals(3, parse("3")); assertEquals(4, parse("4"))
        // a string, a fraction (also 4.0 and 3.9, never rounded or cut), an overflow, a boolean: not supported
        for (raw in listOf("\"4\"", "\"3\"", "4.9", "4.0", "3.9", "2.5", "4e0", "2147483652", "9223372036854775807", "99999999999999999999", "true", "[4]", "{\"v\":4}")) {
            val v = parse(raw)
            assertEquals(raw, PlacementRule.INVALID, v)
            assertFalse(raw, PlacementRule.known(v))
            assertNotNull(raw, PlacementRule.problem(v))
            val c = populated(5, 4).copy(gridVersion = v)
            assertTrue(raw, IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)).isEmpty())
        }
        // the direct reader: missing / null = no version
        assertNull(PlacementRule.read(JSONObject("""{}""")))
        assertNull(PlacementRule.read(JSONObject("""{"grid_version":null}""")))
        assertEquals(4, PlacementRule.read(JSONObject("""{"grid_version":4}""")))
        assertEquals(PlacementRule.INVALID, PlacementRule.read(JSONObject("""{"grid_version":4.9}""")))
    }

    @Test fun serverInfoCarriesTheGridVersionWhenThereIsOne() {
        var reply = """{"name":"T","role":"test","version":"4","warning":"","features":["build_grid_v2","build_grid_v4"],"grid_version":4}"""
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("ok")
                .body(reply.toResponseBody("application/json".toMediaTypeOrNull())).build()
        }.build()
        val api = ApiClient("https://example.invalid/", client)
        kotlinx.coroutines.runBlocking {
            assertEquals(4, api.serverInfo().gridVersion)
            // the running 7.9.9 test server: only build_grid_v2, no number
            reply = """{"name":"T","role":"test","version":"4","warning":"","features":["build_grid_v2"]}"""
            assertNull(api.serverInfo().gridVersion)
            // not an integer: never read as 4
            for (raw in listOf("4.9", "\"4\"", "4.0")) {
                reply = """{"name":"T","role":"test","version":"4","warning":"","features":["build_grid_v4"],"grid_version":$raw}"""
                assertEquals(raw, PlacementRule.INVALID, api.serverInfo().gridVersion)
            }
        }
    }

    @Test fun legacyV2ConstantsAreTheFixture() {
        val v2 = json.getJSONObject("legacy_v2")
        assertEquals(PlacementRule.V2_BIG, v2.getDouble("big"), 0.0)
        assertEquals(PlacementRule.V2_DECOR_BUILDING, v2.getDouble("decor_building"), 0.0)
        assertEquals(PlacementRule.V2_DECOR_DECOR, v2.getDouble("decor_decor"), 0.0)
        assertEquals((0..5).map { v2.getJSONArray("land_factor").getDouble(it) }, PlacementRule.LAND_FACTOR)
        // island_grid.json pins the build grid and the rules up to 5 (version 6: geometry/island_ground.json)
        assertEquals(json.getInt("version"), PlacementRule.MASKS)
    }

    // ---------------------------------------------------------------- drawn target <=> valid <=> server rule

    @Test fun drawnTargetsAreExactlyTheValidCellsAndTheServerRuleForEveryVersionLandAndPiece() {
        for (version in listOf(2, 3, 4)) for (land in 0..5) {
            val c = populated(land, version)
            val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
            for (t in held) {
                val targets = IslandLayout.targets(c, t).toSet()
                val rule = IslandLayout.ruleTargets(c, t).toSet()
                for (i in -1..21) for (j in -1..21) {
                    val p = listOf(i, j)
                    val ref = reference(c, t, p)
                    val msg = "v$version land $land $t $p"
                    // the historic server rule, exactly
                    assertEquals(msg, ref, p in rule)
                    assertEquals(msg, ref, IslandLayout.ruleProblem(c, t, p) == null)
                    when (t) {
                        is IslandTarget.Building -> assertEquals(msg, ref, IslandLayout.ruleValid(c, t.building, p))
                        is IslandTarget.Decor -> assertEquals(msg, ref, IslandLayout.ruleValidDecor(c, p, t.id, SpecialDecor.isSpecial(t.item), t.item))
                    }
                    // what the app draws, makes green and confirms: that rule AND the graphic fit (7.9.12)
                    val app = ref && fits(c, t, p)
                    assertEquals(msg, app, p in targets)
                    assertEquals(msg, app, IslandLayout.reason(h, IslandDraft(t, p)) == null)
                    when (t) {
                        is IslandTarget.Building -> assertEquals(msg, app, IslandLayout.valid(c, t.building, p))
                        is IslandTarget.Decor -> assertEquals(msg, app, IslandLayout.validDecor(c, p, t.id, SpecialDecor.isSpecial(t.item), t.item))
                    }
                }
                // occupied cells and cells outside the land are never targets; never more than the old server accepts
                assertTrue(targets.none { it !in cells("cells", land) })
                assertTrue(rule.containsAll(targets))
            }
        }
    }

    @Test fun rule5TargetsAreExactlyTheMaskReferenceOnEveryLand() {
        for (land in 1..5) {
            val c = populatedFit(land)
            assertTrue("land $land", IslandLayout.overlaps(c).isEmpty())
            val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
            for (t in held) {
                val targets = IslandLayout.targets(c, t).toSet()
                assertEquals(targets, IslandLayout.ruleTargets(c, t).toSet())
                for (i in -1..21) for (j in -1..21) {
                    val p = listOf(i, j)
                    val ref = (t != IslandTarget.Building(IsleBuilding.HARBOUR) || p in cells("coast", land)) && fits(c, t, p)
                    val msg = "v5 land $land $t $p"
                    assertEquals(msg, ref, p in targets)
                    assertEquals(msg, ref, IslandLayout.reason(h, IslandDraft(t, p)) == null)
                }
                // a crowded small island may have no room left for one more big picture; the full land always has
                if (land >= 3) assertTrue("land $land $t", targets.isNotEmpty())
            }
        }
    }

    @Test fun theHarbourIsOfferedOnlyOnFreeCoastCells() {
        for (version in listOf(2, 3, 4)) for (land in 0..5) {
            val c = populated(land, version)
            val t = IslandTarget.Building(IsleBuilding.HARBOUR)
            val rule = IslandLayout.ruleTargets(c, t)
            val targets = IslandLayout.targets(c, t)
            val coast = cells("coast", land)
            assertTrue("v$version land $land", rule.all { it in coast } && targets.all { it in coast })
            // version 2's wide clearances leave no coast cell free on this crowded small island; every other case offers some
            if (version != 2 || land >= 2) assertTrue("v$version land $land", rule.isNotEmpty())
            assertEquals(coast.filter { reference(c, t, it) }.toSet(), rule.toSet())
            // 7.9.12: offered = that rule AND the whole pier fits (water in front, no other sprite on it)
            assertEquals(coast.filter { reference(c, t, it) && fits(c, t, it) }.toSet(), targets.toSet())
            val taken = IslandLayout.obstacles(c, t).flatMap { it.cells }.toSet()
            assertTrue(targets.none { it in taken })
        }
        for (land in 1..5) {
            val c = populatedFit(land)
            val t = IslandTarget.Building(IsleBuilding.HARBOUR)
            val targets = IslandLayout.targets(c, t).toSet()
            assertEquals(cells("coast", land).filter { fits(c, t, it) }.toSet(), targets)
            // a free coast anchor alone is not enough: coast cells with land in front or a pier over the rocks are refused
            assertTrue(targets.all { listOf(it[0], it[1] + 1) !in cells("cells", land) })
            assertTrue(c.positions.getValue("HARBOUR") in targets)
        }
    }

    /**
     * Eigene alte Position: the moved piece never blocks itself, under every rule, on every land, for every kind of
     * held and other piece. (A layout that is legal under all three rules: the held piece on the western coast, the
     * other one on the far eastern cell, 10+ cells apart, more than version 2's widest clearance on land 0.)
     */
    @Test fun theOwnOldSpotIsAlwaysATargetForTheMovedPiece() {
        // the historic rules (2, 3, 4) exactly: [IslandLayout.ruleTargets]; rule 5 below with spots where the pictures fit
        for (version in listOf(2, 3, 4)) for (land in 0..5) {
            val spot = IslandGrid.coastCells(land).sortedWith(compareBy({ it[0] }, { it[1] })).first()
            val far = IslandGrid.buildableCells(land).sortedWith(compareBy({ it[0] }, { it[1] })).last()
            for (heldKind in listOf("big", "starter", "harbour", "decor", "special")) for (otherKind in listOf("big", "starter", "decor", "special")) {
                var c = ApiClient.IslandConstruction(land = land, gridVersion = version)
                val t: IslandTarget = when (heldKind) {
                    "big" -> { c = c.copy(buildings = mapOf("POST" to 2), positions = mapOf("POST" to spot)); IslandTarget.Building(IsleBuilding.POST) }
                    "starter" -> { c = c.copy(starters = setOf("POST"), positions = mapOf("POST" to spot)); IslandTarget.Building(IsleBuilding.POST) }
                    "harbour" -> { c = c.copy(buildings = mapOf("HARBOUR" to 1), positions = mapOf("HARBOUR" to spot)); IslandTarget.Building(IsleBuilding.HARBOUR) }
                    "decor" -> { c = c.copy(decor = listOf(ApiClient.IslandDecor(100, "bench", spot))); IslandTarget.Decor("bench", 100) }
                    else -> { c = c.copy(decor = listOf(ApiClient.IslandDecor(101, "place_hut", spot))); IslandTarget.Decor("place_hut", 101) }
                }
                c = when (otherKind) {
                    "big" -> c.copy(buildings = c.buildings + ("HOUSE" to 3), positions = c.positions + ("HOUSE" to far))
                    "starter" -> c.copy(starters = c.starters + "HOUSE", positions = c.positions + ("HOUSE" to far))
                    "decor" -> c.copy(decor = c.decor + ApiClient.IslandDecor(200, "flowers", far))
                    else -> c.copy(decor = c.decor + ApiClient.IslandDecor(201, "place_uni", far))
                }
                val targets = IslandLayout.ruleTargets(c, t)
                val msg = "v$version land $land $heldKind vs $otherKind"
                assertTrue(msg, spot in targets)
                assertFalse(msg, far in targets)
            }
        }
        // rule 5: every piece of a packed island keeps its own spot as a target, and its spot is no target for the others
        for (land in 1..5) {
            val c = populatedFit(land)
            val pieces: List<Pair<IslandTarget, List<Int>>> = IsleBuilding.entries.filter { c.standing(it) }.map { IslandTarget.Building(it) to c.positions.getValue(it.name) } +
                c.decor.map { IslandTarget.Decor(it.item, it.id) to it.position }
            for ((t, spot) in pieces) {
                assertTrue("land $land $t", spot in IslandLayout.targets(c, t))
                for ((o, _) in pieces) if (o != t && o != IslandTarget.Building(IsleBuilding.HARBOUR)) assertFalse("land $land $o on $t", spot in IslandLayout.targets(c, o))
            }
        }
    }

    @Test fun unknownAndMissingVersionsOfferNothingAndSayWhy() {
        for (version in listOf(PlacementRule.LEGACY, 1, 9, 99, -3)) {
            val c = populated(5, 4).copy(gridVersion = version)
            val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
            for (t in held) {
                assertTrue("v$version $t", IslandLayout.targets(c, t).isEmpty())
                for (p in IslandGrid.buildableCells(5)) {
                    val why = IslandLayout.reason(h, IslandDraft(t, p))
                    assertEquals(PlacementRule.problem(version), why)
                    assertNotNull(why)
                }
            }
            assertFalse(IslandLayout.valid(c, IsleBuilding.POST, listOf(10, 10)))
            assertFalse(IslandLayout.validDecor(c, listOf(10, 10)))
        }
        // 7.9.13: version 6 (ground) is known; the next unknown one fails closed and asks for an app update
        assertTrue(PlacementRule.problem(9)!!.contains("App aktualisieren"))
        assertTrue(PlacementRule.problem(PlacementRule.LEGACY)!!.contains("Server"))
        for (v in listOf(2, 3, 4, 5, 6)) assertNull(PlacementRule.problem(v))
    }

    // ---------------------------------------------------------------- compact (version 4)

    @Test fun compactNeighboursFitWhereVersion3RefusedThem() {
        for (land in 2..5) {
            val c = ApiClient.IslandConstruction(land = land, gridVersion = 4, buildings = mapOf("HOUSE" to 3), positions = mapOf("HOUSE" to listOf(10, 10)))
            // right beside, in front and behind: free under version 4 (one ground cell each) ...
            for (p in listOf(listOf(11, 10), listOf(9, 10), listOf(10, 11), listOf(10, 9)))
                assertTrue("land $land $p", IslandLayout.ruleValid(c.copy(buildings = c.buildings + ("POST" to 2)), IsleBuilding.POST, p))
            // ... the house's own cell never
            assertFalse(IslandLayout.ruleValid(c, IsleBuilding.POST, listOf(10, 10)))
            // version 3 refused the neighbour behind the house (one ring each)
            assertFalse(IslandLayout.ruleValid(c.copy(gridVersion = 3), IsleBuilding.POST, listOf(10, 9)))
            // 7.9.12: those pictures stand inside each other, so the app offers none of them (any rule), and rule 5 refuses them
            for (p in listOf(listOf(11, 10), listOf(9, 10), listOf(10, 11), listOf(10, 9))) for (v in listOf(4, 5))
                assertFalse("land $land $p v$v", IslandLayout.valid(c.copy(gridVersion = v, buildings = c.buildings + ("POST" to 2)), IsleBuilding.POST, p))
        }
        // the starter island: six stage-0 pieces side by side (the historic rule 4)
        var c = ApiClient.IslandConstruction(land = 0, gridVersion = 4)
        IsleBuilding.entries.filter { it != IsleBuilding.HARBOUR }.forEachIndexed { i, b ->
            val p = listOf(6 + i, 9)
            assertTrue("$b $p", IslandLayout.ruleValid(c, b, p))
            c = c.copy(starters = c.starters + b.name, positions = c.positions + (b.name to p))
        }
        assertTrue(IslandLayout.ruleTargets(c, IslandTarget.Building(IsleBuilding.HARBOUR)).isNotEmpty())
    }

    @Test fun version4IsNeverStricterThanVersion3OrVersion2() {
        // (the app's offered targets add the same graphic fit to every rule, so the order holds for them too)
        for (land in 0..5) for (version in listOf(2, 3)) {
            val old = populated(land, version)
            val now = old.copy(gridVersion = 4)
            for (t in held) {
                val before = IslandLayout.targets(old, t).toSet()
                val after = IslandLayout.targets(now, t).toSet()
                assertTrue("land $land v$version $t lost ${before - after}", after.containsAll(before))
            }
        }
    }

    // ---------------------------------------------------------------- legacy and conflicts

    @Test fun unknownLegacyDecorationIsVisibleAndStillAnObstacle() {
        val c = populated(5, 4)
        val gnome = c.decor.single { it.item == "gnome_legacy" }
        val pieces = decorPieces(emptyMap(), ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c), 1f)
        val shown = pieces.single { it.first.id == gnome.id }.second
        assertEquals(IsleDecor.UNKNOWN_RES, shown.res)
        assertEquals("Unbekannte Deko (gnome_legacy)", shown.name)
        assertEquals(c.decor.size, pieces.size)
        // it blocks its cell exactly like the server counts it
        assertFalse(gnome.position in IslandLayout.targets(c, IslandTarget.Building(IsleBuilding.POST)))
        assertTrue(IslandLayout.obstacles(c, IslandTarget.Building(IsleBuilding.POST)).any { it.target == IslandTarget.Decor("gnome_legacy", 102) })
    }

    @Test fun aMovedPieceThatWasRemovedMeanwhileIsNeverGreenAndOffersNothing() {
        // 7.9.12: a layout whose pictures do not overlap (rule 5), so the own spots are really free
        val c = populatedFit(5)
        fun h(c: ApiClient.IslandConstruction) = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
        // the bench (100) and the lifted house still stand: their own old spot is free for them
        val bench = IslandDraft(IslandTarget.Decor("bench", 100), c.decor.single { it.id == 100 }.position, lifted = true)
        val house = IslandDraft(IslandTarget.Building(IsleBuilding.HOUSE), c.positions.getValue("HOUSE"), lifted = true)
        for (d in listOf(bench, house)) {
            assertFalse(IslandLayout.gone(c, d)); assertNull(IslandLayout.reason(h(c), d)); assertTrue(d.position in IslandLayout.targets(c, d))
        }
        // removed on another device: no green spot anywhere, the hint says why
        val noBench = c.copy(decor = c.decor.filter { it.id != 100 })
        val noHouse = c.copy(buildings = c.buildings - "HOUSE")
        for ((island, d) in listOf(noBench to bench, noHouse to house)) {
            assertTrue(IslandLayout.gone(island, d))
            assertTrue(IslandLayout.targets(island, d).isEmpty())
            for (p in IslandGrid.buildableCells(5)) assertEquals(IslandLayout.GONE, IslandLayout.reason(h(island), d.copy(position = p)))
        }
        // a NEW building from the build bar (not lifted) that does not stand yet is not "gone": it is built at stage 0
        val fresh = IslandDraft(IslandTarget.Building(IsleBuilding.LIGHTHOUSE), listOf(9, 10))
        assertFalse(IslandLayout.gone(c, fresh))
        // a new decoration (no id) is never gone either
        assertFalse(IslandLayout.gone(c, IslandDraft(IslandTarget.Decor("lantern"), listOf(9, 10))))
        // an unknown rule keeps its own message first
        assertEquals(PlacementRule.problem(9), IslandLayout.reason(h(noBench.copy(gridVersion = 9)), bench))
    }

    @Test fun aSpotTheServerRefusedIsNeverOfferedAgainForThatDraft() {
        val c = populatedFit(5)
        val t = IslandTarget.Building(IsleBuilding.POST)
        val spot = IslandLayout.targets(c, t).first()
        val draft = IslandDraft(t, spot, rejected = setOf(spot))
        val h = ApiClient.HomeIsland(1, emptyMap(), 0, emptyList(), construction = c)
        assertEquals(IslandLayout.REJECTED, IslandLayout.reason(h, draft))
        assertFalse(spot in IslandLayout.targets(c, t, draft.rejected))
        assertEquals(IslandLayout.targets(c, t).size - 1, IslandLayout.targets(c, t, draft.rejected).size)
    }
}
