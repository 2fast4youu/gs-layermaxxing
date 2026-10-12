package at.gregor.layermaxxing

/**
 * 7.9.7: every function building has its own stages 0–3. Name, menu title/subtitle, sign, icon, painted
 * asset and preview all come from this one table, so menu, island sign, action bar, upgrade view and build
 * shelf always change together. Stage 0 is the natural starter (shown in the menu and as "vorher" in the
 * upgrade view; it does not stand on the island yet), stage 1 is what the build shelf places.
 * "Öffnen" always leads to the building's function ([function]); the stage changes look and name.
 * Only existing painted assets are used (docs/insel-ausbau.md lists the table and the asset gaps).
 */
internal object IslandStages {
    data class Stage(
        val building: IsleBuilding, val level: Int,
        /** Name on the sign, in the menu and in the action bar. */
        val name: String,
        /** What it looks like at this stage (upgrade comparison). */
        val look: String,
        val res: Int, val accent: Int? = null,
        /** Line icon (menu entry, Öffnen button). */
        val icon: Int,
    ) {
        /** What "Öffnen" leads to (the same function on every stage). */
        val function: String get() = IslandStages.function(building)
        /** Menu subtitle: the function plus the stage. */
        val subtitle: String get() = if (level == 0) "$function · Start" else "$function · Stufe $level"
    }

    fun function(b: IsleBuilding) = when (b) {
        IsleBuilding.HOUSE -> "Profil & Einstellungen"
        IsleBuilding.POST -> "Briefe"
        IsleBuilding.LIGHTHOUSE -> "Freunde & Anfragen"
        IsleBuilding.LIBRARY -> "Wörterbuch"
        IsleBuilding.CAMPFIRE -> "Gruppen"
        IsleBuilding.HARBOUR -> "Quests & Themen"
    }

    private val table: Map<IsleBuilding, List<Stage>> = mapOf(
        IsleBuilding.POST to listOf(
            Stage(IsleBuilding.POST, 0, "Briefbaum", "Briefe am Baumstamm angeschlagen", R.drawable.n_tree, R.drawable.p_env, R.drawable.ico_pin),
            Stage(IsleBuilding.POST, 1, "Postkasten", "Ein Holzkasten mit Dach für deine Briefe", R.drawable.lm_post_ruin, null, R.drawable.ico_letter),
            Stage(IsleBuilding.POST, 2, "Postfächer", "Kleine Poststube mit Fächern", R.drawable.lm_post, null, R.drawable.ico_letter_ready),
            Stage(IsleBuilding.POST, 3, "Postamt", "Großes Postamt mit Fahne", R.drawable.b_post, null, R.drawable.ico_seal),
        ),
        IsleBuilding.LIGHTHOUSE to listOf(
            Stage(IsleBuilding.LIGHTHOUSE, 0, "Aussichtspalme", "Von der Palme aus nach Freunden Ausschau halten", R.drawable.decor_palm, null, R.drawable.ico_friend),
            Stage(IsleBuilding.LIGHTHOUSE, 1, "Ausguck", "Hölzerner Ausguck", R.drawable.fief_tower_small, null, R.drawable.ico_people),
            Stage(IsleBuilding.LIGHTHOUSE, 2, "Leuchtfeuer", "Kleiner Leuchtturm auf den Felsen", R.drawable.lm_lighthouse, null, R.drawable.ico_lighthouse),
            Stage(IsleBuilding.LIGHTHOUSE, 3, "Leuchtturm", "Großer Leuchtturm mit Fahne", R.drawable.b_lighthouse, null, R.drawable.ico_lighthouse),
        ),
        IsleBuilding.LIBRARY to listOf(
            Stage(IsleBuilding.LIBRARY, 0, "Wörter am Treibholz", "Ein Buch auf angeschwemmten Kisten", R.drawable.n_crates, R.drawable.p_book, R.drawable.ico_manual),
            Stage(IsleBuilding.LIBRARY, 1, "Lesedach", "Ein Dach aus Zweigen über dem Bücherregal", R.drawable.lm_library_ruin, null, R.drawable.ico_glossary),
            Stage(IsleBuilding.LIBRARY, 2, "Bücherstube", "Kleine Stube mit Regal und Lesesessel", R.drawable.lm_library, null, R.drawable.ico_glossary),
            Stage(IsleBuilding.LIBRARY, 3, "Bibliothek", "Steinerne Bibliothek mit Turm und Fahne", R.drawable.b_library, null, R.drawable.ico_library),
        ),
        IsleBuilding.CAMPFIRE to listOf(
            Stage(IsleBuilding.CAMPFIRE, 0, "Feuerkreis", "Steine um ein Lagerfeuer", R.drawable.lm_campfire, null, R.drawable.ico_campfire),
            Stage(IsleBuilding.CAMPFIRE, 1, "Treffhütte", "Holzhütte für eure Treffen", R.drawable.b_workshop, null, R.drawable.ico_group),
            Stage(IsleBuilding.CAMPFIRE, 2, "Gemeindehaus", "Weißes Gemeindehaus", R.drawable.b_hall, null, R.drawable.ico_groups),
            Stage(IsleBuilding.CAMPFIRE, 3, "Festhalle", "Gemeindehaus mit Festfahne", R.drawable.b_hall, null, R.drawable.ico_groups),
        ),
        IsleBuilding.HARBOUR to listOf(
            Stage(IsleBuilding.HARBOUR, 0, "Strandtreff", "Ruderboot mit Wimpel am Strand", R.drawable.n_rowboat, R.drawable.decor_flag, R.drawable.ico_boat),
            Stage(IsleBuilding.HARBOUR, 1, "Bootssteg", "Einfacher Steg aus Treibholz", R.drawable.lm_jetty_ruin, null, R.drawable.ico_harbour),
            Stage(IsleBuilding.HARBOUR, 2, "Anleger", "Fester Anleger mit Pollern", R.drawable.b_jetty, null, R.drawable.ico_harbour),
            Stage(IsleBuilding.HARBOUR, 3, "Hafen", "Hafen mit Laterne und Fahne", R.drawable.lm_jetty, null, R.drawable.ico_compass),
        ),
        IsleBuilding.HOUSE to listOf(
            Stage(IsleBuilding.HOUSE, 0, "Schlafplatz", "Eine Hängematte zwischen zwei Pfosten", R.drawable.decor_hammock, null, R.drawable.ico_home),
            Stage(IsleBuilding.HOUSE, 1, "Holzhütte", "Kleine Blockhütte", R.drawable.fief_hut_stage1, null, R.drawable.ico_house),
            Stage(IsleBuilding.HOUSE, 2, "Häuschen", "Häuschen mit rotem Dach", R.drawable.b_house, null, R.drawable.ico_house),
            Stage(IsleBuilding.HOUSE, 3, "Landhaus", "Steinhaus mit Turm und Fahne", R.drawable.fief_hut_stage3, null, R.drawable.ico_home),
        ),
    )

    fun of(b: IsleBuilding, level: Int): Stage = table.getValue(b)[level.coerceIn(0, 3)]
    fun all(b: IsleBuilding): List<Stage> = table.getValue(b)
    /** Short stage label: "Start" for stage 0, otherwise "Stufe n". */
    fun stageLabel(level: Int) = if (level <= 0) "Start" else "Stufe $level"
}

/** 7.9.9: a function stands on the island when it is upgraded (1..3) or built at its free stage 0. */
internal fun ApiClient.IslandConstruction.standing(b: IsleBuilding): Boolean = (buildings[b.name] ?: 0) > 0 || b.name in starters
/** Current stage of a function (0 = stage 0, standing or not). */
internal fun ApiClient.IslandConstruction.level(b: IsleBuilding): Int = buildings[b.name] ?: 0
