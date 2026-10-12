package at.gregor.layermaxxing

/**
 * 7.9.7 "Ausbau": the former life places ("Bauplätze") are special decoration - expensive, placed
 * freely like any decoration from the build bar, at most [LIMIT] per island (the server enforces it).
 * Old places were migrated by the server into the construction ledger with their names.
 */
internal object SpecialDecor {
    const val LIMIT = 3
    data class Item(val key: String, val label: String, val res: Int, val hint: String)

    val items = listOf(
        Item("place_home", "Zuhause", R.drawable.place_home, "Wo du wohnst"),
        Item("place_bude", "Bude", R.drawable.place_bude, "WG, Studentenbude"),
        Item("place_uni", "Uni", R.drawable.place_uni, "Hörsaal, Lernen"),
        Item("place_hut", "Hütte", R.drawable.place_hut, "Berghütte"),
        Item("place_club", "Verein", R.drawable.place_club, "Sport, Training"),
        Item("place_station", "Bahnhof", R.drawable.place_station, "Unterwegs, Pendeln"),
        Item("place_city", "Stadt", R.drawable.place_city, "Altstadt, Ausgehen"),
        Item("place_desk", "Am Berg coden", R.drawable.place_desk, "Schreibtisch mit Aussicht"),
    )
    private val byKey = items.associateBy { it.key }
    val defaultCosts: Map<String, Int> = mapOf(
        "place_home" to 120, "place_bude" to 120, "place_uni" to 160, "place_hut" to 160,
        "place_club" to 160, "place_station" to 200, "place_city" to 200, "place_desk" to 200,
    )

    fun isSpecial(item: String) = item.startsWith("place_")
    fun item(key: String): Item? = byKey[key]
    /** Sprite; old kinds without an own picture (work, cafe) borrow the closest one. */
    fun res(key: String): Int? = byKey[key]?.res ?: when (key) {
        "place_work" -> R.drawable.place_desk
        "place_cafe" -> R.drawable.place_city
        else -> null
    }
    fun label(key: String): String = byKey[key]?.label ?: when (key) { "place_work" -> "Arbeit"; "place_cafe" -> "Café"; else -> "Ort" }
    fun count(c: ApiClient.IslandConstruction) = c.decor.count { isSpecial(it.item) }
    /** No new special piece once [ApiClient.IslandConstruction.specialLimit] stand (old extra ones stay). */
    fun full(c: ApiClient.IslandConstruction) = count(c) >= c.specialLimit
    fun cost(c: ApiClient.IslandConstruction, key: String) = c.specialCosts[key] ?: defaultCosts[key] ?: 0
    fun affordable(c: ApiClient.IslandConstruction, key: String) = c.mode == "creative" || c.available >= cost(c, key)

    /** Old plot cells (server ISLAND_PLOT_POSITIONS), only to show places of a server without the ledger. */
    private val plotCells = listOf(listOf(10, 6), listOf(6, 8), listOf(14, 8), listOf(5, 13), listOf(15, 13), listOf(7, 4), listOf(13, 4), listOf(8, 15), listOf(12, 15))
    fun legacyPlaces(places: Map<Int, ApiClient.LifePlace>): List<ApiClient.IslandDecor> = places.mapNotNull { (plot, p) ->
        plotCells.getOrNull(plot)?.let { ApiClient.IslandDecor(200 + plot, "place_" + p.kind, it, name = p.name) }
    }
}
