package at.gregor.layermaxxing

/**
 * 7.9.7: every function building has its own stages 0–3. Name, menu title/subtitle, sign, icon, painted
 * asset and preview all come from this one table, so menu, island sign, action bar, upgrade view and build
 * shelf always change together. Stage 0 is the natural starter (shown in the menu and as "vorher" in the
 * upgrade view; it does not stand on the island yet), stage 1 is what the build shelf places.
 * "Öffnen" always leads to the building's function ([function]); the stage changes look and name.
 * 7.9.14: every stage has its own style E island picture ([Stage.art]) and painted place ([Stage.scene]).
 */
internal object IslandStages {
    data class Stage(
        val building: IsleBuilding, val level: Int,
        /** Name on the sign, in the menu and in the action bar. */
        val name: String,
        /** What it looks like at this stage (upgrade comparison). */
        val look: String,
        /**
         * The 7.9.13 picture this stage had: kept ONLY for the grid_version 5 contract (server/app/geometry/island_masks.json
         * was measured from these pictures; IslandMasksPixelTest) and for islands of servers that still run rule 5 or older.
         */
        val res: Int, val accent: Int? = null,
        /** Line icon (menu entry, Öffnen button). */
        val icon: Int,
    ) {
        /** What "Öffnen" leads to (the same function on every stage). */
        val function: String get() = IslandStages.function(building)
        /** Menu subtitle: the function plus the stage. */
        val subtitle: String get() = if (level == 0) "$function · Start" else "$function · Stufe $level"
        /** 7.9.14: the style E picture of this stage (island, header, upgrade preview, build shelf): [StyleEArt]. */
        val art: Int get() = StyleEArt.of(building, level).res
        /** 7.9.14: the stage's painted place ([StageScenes]). */
        val scene: Int get() = StageScenes.of(building, level).picture
    }

    fun function(b: IsleBuilding) = when (b) {
        IsleBuilding.HOUSE -> "Profil & Einstellungen"
        IsleBuilding.POST -> "Briefe"
        IsleBuilding.LIGHTHOUSE -> "Freunde & Anfragen"
        IsleBuilding.LIBRARY -> "Wörterbuch"
        IsleBuilding.CAMPFIRE -> "Gruppen"
        IsleBuilding.HARBOUR -> "Quests & Themen"
    }

    /** 7.9.14: the approved stage stories (LAYERMAXXING-DESIGNVORGABEN-NEU.md, Nutzerfreigabe der Stil-E-Reihen). */
    private val table: Map<IsleBuilding, List<Stage>> = mapOf(
        IsleBuilding.POST to listOf(
            Stage(IsleBuilding.POST, 0, "Briefbaum", "Briefe an den Stamm der alten Buche genagelt", R.drawable.n_tree, R.drawable.p_env, R.drawable.ico_pin),
            Stage(IsleBuilding.POST, 1, "Postkasten", "Kasten am Baum mit Briefschub und Markenbogen", R.drawable.lm_post_ruin, null, R.drawable.ico_letter),
            Stage(IsleBuilding.POST, 2, "Briefstube", "Stube im Baum: Fächer für jeden Freund, Pult mit Schub", R.drawable.lm_post, null, R.drawable.ico_letter_ready),
            Stage(IsleBuilding.POST, 3, "Postamt", "Postamt mit Schalter, Fächerwand und Fahne", R.drawable.b_post, null, R.drawable.ico_seal),
        ),
        IsleBuilding.LIGHTHOUSE to listOf(
            Stage(IsleBuilding.LIGHTHOUSE, 0, "Aussichtspalme", "Von der Palme am Rand aufs Meer schauen", R.drawable.decor_palm, null, R.drawable.ico_friend),
            Stage(IsleBuilding.LIGHTHOUSE, 1, "Ausguck", "Holzplattform mit Fernrohr an der Küste", R.drawable.fief_tower_small, null, R.drawable.ico_people),
            Stage(IsleBuilding.LIGHTHOUSE, 2, "Signalstation", "Steinerne Station mit Signalmast und Flaggen", R.drawable.lm_lighthouse, null, R.drawable.ico_lighthouse),
            Stage(IsleBuilding.LIGHTHOUSE, 3, "Leuchtturm", "Leuchtturm, dessen Licht alle Freunde erreicht", R.drawable.b_lighthouse, null, R.drawable.ico_lighthouse),
        ),
        IsleBuilding.LIBRARY to listOf(
            Stage(IsleBuilding.LIBRARY, 0, "Schreibplatz", "Ein offenes Buch auf dem Baumstumpf", R.drawable.n_crates, R.drawable.p_book, R.drawable.ico_manual),
            Stage(IsleBuilding.LIBRARY, 1, "Lesedach", "Schreibtisch unter einem Rindendach mit Laterne", R.drawable.lm_library_ruin, null, R.drawable.ico_glossary),
            Stage(IsleBuilding.LIBRARY, 2, "Schreibstube", "Fachwerkstube mit Karteischrank am Fenster", R.drawable.lm_library, null, R.drawable.ico_glossary),
            Stage(IsleBuilding.LIBRARY, 3, "Bibliothek", "Steinerne Bibliothek mit Kuppel und Bannern", R.drawable.b_library, null, R.drawable.ico_library),
        ),
        IsleBuilding.CAMPFIRE to listOf(
            Stage(IsleBuilding.CAMPFIRE, 0, "Lagerfeuer", "Feuer auf der Lichtung mit Stämmen und Wimpel", R.drawable.lm_campfire, null, R.drawable.ico_campfire),
            Stage(IsleBuilding.CAMPFIRE, 1, "Treffplatz", "Offenes Dach über einem runden Tisch", R.drawable.b_workshop, null, R.drawable.ico_group),
            Stage(IsleBuilding.CAMPFIRE, 2, "Versammlungshaus", "Holzhaus mit Kamin und runden Tischen", R.drawable.b_hall, null, R.drawable.ico_groups),
            Stage(IsleBuilding.CAMPFIRE, 3, "Glashaus", "Glashaus um den Baum am ersten Feuer", R.drawable.b_hall, null, R.drawable.ico_groups),
        ),
        IsleBuilding.HARBOUR to listOf(
            Stage(IsleBuilding.HARBOUR, 0, "Anlegestelle", "Kurzer Steg vom Kiesufer ins Wasser", R.drawable.n_rowboat, R.drawable.decor_flag, R.drawable.ico_boat),
            Stage(IsleBuilding.HARBOUR, 1, "Bootssteg", "Steg mit überdachter Anschlagtafel", R.drawable.lm_jetty_ruin, null, R.drawable.ico_harbour),
            Stage(IsleBuilding.HARBOUR, 2, "Anleger", "Fester Anleger mit Schuppen und Laterne", R.drawable.b_jetty, null, R.drawable.ico_harbour),
            Stage(IsleBuilding.HARBOUR, 3, "Hafen", "Steinkai mit Korktafel und Laternenpoller", R.drawable.lm_jetty, null, R.drawable.ico_compass),
        ),
        IsleBuilding.HOUSE to listOf(
            Stage(IsleBuilding.HOUSE, 0, "Nachtlager", "Decke, Tasche und Laterne unter der alten Buche", R.drawable.decor_hammock, null, R.drawable.ico_home),
            Stage(IsleBuilding.HOUSE, 1, "Unterschlupf", "Schräge Stämme mit Dach an der Buche", R.drawable.fief_hut_stage1, null, R.drawable.ico_house),
            Stage(IsleBuilding.HOUSE, 2, "Holzhütte", "Blockhütte mit Ofen an der Buche", R.drawable.b_house, null, R.drawable.ico_house),
            Stage(IsleBuilding.HOUSE, 3, "Landhaus", "Fachwerkhaus mit Garten und Schreibtisch", R.drawable.isle_manor, null, R.drawable.ico_home),
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
