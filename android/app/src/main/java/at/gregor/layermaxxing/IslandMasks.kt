package at.gregor.layermaxxing

import org.json.JSONObject

/**
 * 7.9.12: THE graphic occupancy of every island piece, read from the very file the server validates with
 * (server/app/geometry/island_masks.json, packaged as a Java resource, generated from the painted WebP alpha by
 * server/tools/island_masks.py). For every building × stage × land stage and every decoration item it holds:
 *  - size: the sprite box side as a fraction of the island width; the renderer draws exactly this size,
 *  - mask: the lattice cells (offsets from the spot = the sprite's foot) that hold opaque sprite pixels,
 *  - base: the ground part, which must lie on the build grid (the harbour: coast spot, water in front).
 * Transparent margins are no space; two pieces whose masks share no cell never share a painted pixel
 * (IslandMasksPixelTest checks that on the decoded sprites). Drawing, targets, green/✓ and the server use this.
 */
internal object IslandMasks {
    data class Shape(val size: Float, val mask: List<List<Int>>, val base: List<List<Int>>) {
        /** Rows and columns it spans (for the touch reach of a held piece). */
        val reach: Int get() = mask.maxOf { maxOf(kotlin.math.abs(it[0]), kotlin.math.abs(it[1])) }
    }
    const val RESOURCE = "island_masks.json"
    /** Where a ship berths in front of the harbour spot: one cell towards the viewer (the water). */
    val BERTH = listOf(0, 1)

    private val pieces: Map<String, List<Shape>> by lazy { parse(load()) }

    private fun load(): String =
        (IslandMasks::class.java.classLoader?.getResourceAsStream(RESOURCE) ?: error("$RESOURCE fehlt im Paket"))
            .use { it.readBytes().toString(Charsets.UTF_8) }

    fun parse(text: String): Map<String, List<Shape>> {
        val root = JSONObject(text)
        val all = root.getJSONObject("pieces")
        fun cells(a: org.json.JSONArray) = (0 until a.length()).map { a.getJSONArray(it).let { c -> listOf(c.getInt(0), c.getInt(1)) } }
        return all.keys().asSequence().associateWith { key ->
            val lands = all.getJSONObject(key).getJSONArray("lands")
            (0 until lands.length()).map { l ->
                val e = lands.getJSONObject(l)
                Shape(e.getDouble("size").toFloat(), cells(e.getJSONArray("mask")), cells(e.getJSONArray("base")))
            }
        }
    }

    fun version(): Int = JSONObject(load()).getInt("version")
    fun keys(): Set<String> = pieces.keys

    fun buildingKey(b: IsleBuilding, stage: Int) = "building:${b.name}:${stage.coerceIn(0, 3)}"
    /** An item this app has no picture for is drawn (and counted) as the signpost, like the server does. */
    fun decorKey(item: String): String = when {
        "decor:$item" in pieces -> "decor:$item"
        SpecialDecor.isSpecial(item) -> "decor:?special"
        else -> "decor:?"
    }
    fun shape(key: String, land: Int): Shape = pieces.getValue(key)[land.coerceIn(0, 5)]
    fun building(b: IsleBuilding, stage: Int, land: Int) = shape(buildingKey(b, stage), land)
    fun decor(item: String, land: Int) = shape(decorKey(item), land)

    /** The cells of [key]'s mask (or its ground [base]) standing at [spot] on [land]. */
    fun cells(key: String, land: Int, spot: List<Int>, base: Boolean = false): Set<List<Int>> =
        shape(key, land).let { s -> (if (base) s.base else s.mask).mapTo(HashSet()) { listOf(spot[0] + it[0], spot[1] + it[1]) } }

    /**
     * Does the piece's ground fit the land at [spot]? null = yes, else why not. The harbour stands on a coast cell
     * with open water in front (where its pier points and ships berth) and its pier ground behind on the land.
     */
    fun landProblem(key: String, land: Int, spot: List<Int>, harbour: Boolean): String? {
        if (!IslandGrid.buildable(spot, land)) return "Außerhalb der Insel"
        val base = cells(key, land, spot, base = true)
        if (harbour) {
            if (!IslandGrid.coast(spot, land)) return "Hafen nur an der Küste"
            if (IslandGrid.buildable(spot[0], spot[1] + 1, land)) return "Hier zeigt der Steg aufs Land – Hafen an eine Küste mit Wasser davor"
            if (base.any { it[1] < spot[1] && !IslandGrid.buildable(it, land) }) return "Der Steg passt hier nicht an die Küste"
            return null
        }
        if (base.any { !IslandGrid.buildable(it, land) }) return "Passt hier nicht ganz auf die Insel"
        return null
    }

    /** Every coast cell where a harbour of this stage fits with its pier (ignoring other pieces): its snap spots. */
    fun harbourSpots(stage: Int, land: Int): List<List<Int>> {
        val key = buildingKey(IsleBuilding.HARBOUR, stage)
        return IslandGrid.coastCells(land).filter { landProblem(key, land, it, harbour = true) == null }
    }
}
