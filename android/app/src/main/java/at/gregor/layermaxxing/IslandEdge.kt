package at.gregor.layermaxxing

import org.json.JSONObject

/** Rule 7 adds coast contact for newly placed/moved LIGHTHOUSE ground; rule 6 geometry is unchanged.
 * Read the exact server fixture via the existing geometry Java-resource source set. Inland rock pockets
 * are deliberately absent: only non-meadow connected to the open sea defines this edge. */
internal object IslandEdge {
    const val RESOURCE = "island_edge.json"
    const val ERROR = "Die Ausschau der Freunde steht immer am Inselrand – mit Blick aufs Meer"
    private val data by lazy {
        val root = (IslandEdge::class.java.classLoader?.getResourceAsStream(RESOURCE)
            ?: error("$RESOURCE fehlt im Paket")).use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        require(root.getInt("version") == PlacementRule.EDGE)
        val edge = root.getJSONObject("edge")
        List(6) { land -> edge.getJSONArray(land.toString()).let { a ->
            (0 until a.length()).map { i -> a.getJSONArray(i).let { listOf(it.getInt(0), it.getInt(1)) } }.toSet()
        } }
    }
    fun cells(land: Int): Set<List<Int>> = data[land.coerceIn(0, 5)]
    fun problem(c: ApiClient.IslandConstruction, t: IslandTarget, p: List<Int>): String? {
        if (c.gridVersion != PlacementRule.EDGE || t != IslandTarget.Building(IsleBuilding.LIGHTHOUSE)) return null
        return if (IslandGround.ground(IslandLayout.key(c, t), c.land, p).any { it in cells(c.land) }) null else ERROR
    }
}
