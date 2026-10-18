package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 7.9.11: every building × stage 0..3 (24 combinations) has its own stage configuration. The names and functions of
 * [IslandStages] stay; the look is not a title or colour change: backdrop, hero, props and the data holder follow the
 * stage, and only the final stage keeps the painted room.
 */
class StageScenesTest {
    /** Painted island/scene assets (no icons, no emoji, no new mock-ups). */
    private val painted = setOf(
        R.drawable.n_tree, R.drawable.n_rocks, R.drawable.n_flowerbush, R.drawable.n_bush, R.drawable.n_pine, R.drawable.n_crates, R.drawable.n_rowboat,
        R.drawable.p_env, R.drawable.p_cubby, R.drawable.p_book, R.drawable.p_books, R.drawable.p_quill, R.drawable.p_stool, R.drawable.p_table,
        R.drawable.p_bottle, R.drawable.fief_env_sealed, R.drawable.fief_env_ready, R.drawable.fief_signpost, R.drawable.fief_cy_board,
        R.drawable.fief_cy_chest, R.drawable.decor_lantern, R.drawable.decor_flowers, R.drawable.decor_bench, R.drawable.decor_palm,
        R.drawable.decor_campfire, R.drawable.boat_row, R.drawable.boat_sail, R.drawable.b_board,
    )

    @Test fun twentyFourScenesOnePerBuildingAndStage() {
        val all = StageScenes.all
        assertEquals(24, all.size)
        assertEquals(IsleBuilding.entries.flatMap { b -> (0..3).map { b to it } }.toSet(), all.map { it.building to it.level }.toSet())
        for (b in IsleBuilding.entries) for (l in 0..3) assertEquals(StageScenes.of(b, l), all.single { it.building == b && it.level == l })
    }

    @Test fun namesAndFunctionsOfTheStagesStayAndTheHeroIsTheIslandPiece() {
        val names = mapOf(
            IsleBuilding.POST to listOf("Briefbaum", "Postkasten", "Postfächer", "Postamt"),
            IsleBuilding.LIGHTHOUSE to listOf("Aussichtspalme", "Ausguck", "Leuchtfeuer", "Leuchtturm"),
            IsleBuilding.LIBRARY to listOf("Wörter am Treibholz", "Lesedach", "Bücherstube", "Bibliothek"),
            IsleBuilding.CAMPFIRE to listOf("Feuerkreis", "Treffhütte", "Gemeindehaus", "Festhalle"),
            IsleBuilding.HARBOUR to listOf("Strandtreff", "Bootssteg", "Anleger", "Hafen"),
            IsleBuilding.HOUSE to listOf("Schlafplatz", "Holzhütte", "Häuschen", "Landhaus"),
        )
        for ((b, list) in names) for (l in 0..3) {
            val s = StageScenes.of(b, l)
            assertEquals(list[l], s.stage.name)
            assertEquals(IslandStages.function(b), s.stage.function)
            assertEquals(IslandStages.of(b, l).res, s.hero)
        }
    }

    @Test fun onlyTheFinalStageKeepsThePaintedRoom() {
        for (b in IsleBuilding.entries) {
            assertEquals(b.background(), StageScenes.of(b, 3).interior)
            assertEquals(StageScenes.Backdrop.INTERIOR, StageScenes.of(b, 3).backdrop)
            for (l in 0..2) {
                val s = StageScenes.of(b, l)
                assertNull("$b $l", s.interior)
                assertTrue("$b $l", s.outdoor && s.backdrop != StageScenes.Backdrop.INTERIOR)
                // a real stage from painted assets: ground and at least two props
                assertTrue("$b $l", s.ground == R.drawable.terrain_sand || s.ground == R.drawable.terrain_home)
                assertTrue("$b $l", s.props.size >= 2 && s.props.all { it.res in painted })
                // props stand in the scene above the data, inside the screen
                assertTrue("$b $l", s.props.all { it.x in 0f..1f && it.y <= StageScenes.CONTENT_TOP && it.width in .04f..0.2f })
            }
        }
    }

    @Test fun everyStageLooksDifferentFromTheOthersOfItsBuilding() {
        for (b in IsleBuilding.entries) {
            val scenes = (0..3).map { StageScenes.of(b, it) }
            assertEquals("$b", 4, scenes.map { it.signature }.toSet().size)
            assertEquals("$b", 4, scenes.map { it.hero to it.interior }.toSet().size)
            // the way the data is held changes from the natural start to the final building
            assertNotEquals("$b", scenes[0].holder, scenes[3].holder)
            assertTrue("$b", scenes.map { it.caption }.toSet().size == 4 && scenes.all { it.caption.isNotBlank() })
        }
    }

    @Test fun theLetterTreeHasNoFinishedPostOfficeCubbies() {
        assertEquals(StageScenes.Holder.PINNED_ENVELOPE, StageScenes.of(IsleBuilding.POST, 0).holder)
        assertEquals(StageScenes.Holder.MAILBOX, StageScenes.of(IsleBuilding.POST, 1).holder)
        assertEquals(StageScenes.Holder.CUBBY, StageScenes.of(IsleBuilding.POST, 2).holder)
        assertEquals(R.drawable.n_tree, StageScenes.of(IsleBuilding.POST, 0).hero)
        assertTrue(StageScenes.of(IsleBuilding.POST, 0).props.none { it.res == R.drawable.p_cubby })
        // no light before there is a lamp; the palm sees boats, the beacon has portholes
        assertEquals(StageScenes.Holder.ROWBOAT, StageScenes.of(IsleBuilding.LIGHTHOUSE, 0).holder)
        assertEquals(StageScenes.Holder.SAILBOAT, StageScenes.of(IsleBuilding.LIGHTHOUSE, 1).holder)
        assertEquals(StageScenes.Backdrop.NIGHT_SEA, StageScenes.of(IsleBuilding.LIGHTHOUSE, 2).backdrop)
        assertEquals(StageScenes.Holder.FIRE_RING, StageScenes.of(IsleBuilding.CAMPFIRE, 0).holder)
        assertEquals(StageScenes.Holder.PARCHMENT, StageScenes.of(IsleBuilding.LIBRARY, 0).holder)
        assertEquals(StageScenes.Holder.NOTES_ON_POST, StageScenes.of(IsleBuilding.HARBOUR, 0).holder)
        assertEquals(StageScenes.Holder.KEEPSAKES, StageScenes.of(IsleBuilding.HOUSE, 0).holder)
    }
}
