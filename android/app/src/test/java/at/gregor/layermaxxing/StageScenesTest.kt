package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 7.9.11 → 7.9.12: every building × stage 0..3 (24 combinations) is its own FUNCTION STAGE. The names and functions
 * of [IslandStages] stay. The 7.9.11 template (the island piece as a hero at the top, a universal parchment card with
 * the data below a fixed line) is gone: every stage has a big painted place (set piece or room) and the data objects
 * sit at anchors in that place (slots, the ring around a fire / table, the book's pages). The anchors' positions in
 * the painted geometry are checked on the decoded pictures in StageSceneRenderTest.
 */
class StageScenesTest {
    private val finished = setOf(R.drawable.b_post, R.drawable.b_house, R.drawable.b_hall, R.drawable.b_library, R.drawable.b_lighthouse,
        R.drawable.b_workshop, R.drawable.b_board, R.drawable.isle_manor, R.drawable.lm_post, R.drawable.lm_library, R.drawable.lm_jetty,
        R.drawable.bg_post, R.drawable.bg_hall, R.drawable.bg_house, R.drawable.bg_library, R.drawable.bg_lighthouse, R.drawable.bg_harbour)

    @Test fun twentyFourScenesOnePerBuildingAndStage() {
        val all = StageScenes.all
        assertEquals(24, all.size)
        assertEquals(IsleBuilding.entries.flatMap { b -> (0..3).map { b to it } }.toSet(), all.map { it.building to it.level }.toSet())
        for (b in IsleBuilding.entries) for (l in 0..3) assertEquals(StageScenes.of(b, l), all.single { it.building == b && it.level == l })
    }

    @Test fun namesAndFunctionsOfTheStagesStayAndTheHeaderShowsTheIslandPiece() {
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

    @Test fun everyStageIsAPlaceWithDataAnchorsNotAHeroAboveACard() {
        for (s in StageScenes.all) {
            val msg = "${s.building} ${s.level}"
            // a big painted place: a set piece or a painted room (7.9.12: no composed plank/polygon rooms any more)
            assertTrue(msg, s.piece != null || s.room != null)
            if (s.backdrop == StageScenes.Backdrop.INTERIOR) assertNotNull(msg, s.room)
            s.piece?.let { assertTrue("$msg set piece is big", it.width >= .4f) }
            // where the data lives in it
            val anchors = s.slots + s.detailSlots + listOfNotNull(s.centre)
            assertTrue(msg, anchors.isNotEmpty() || s.book != null)
            anchors.forEach { assertTrue("$msg $it", it.x in 0f..1f && it.y in 0f..1f) }
            s.book?.let { assertTrue(msg, it.bottom - it.top >= .45f && it.right - it.left >= .85f) }
            // anchors in a picture's own space need that picture
            if (s.space == StageScenes.Space.PIECE) assertNotNull(msg, s.piece)
            if (s.space == StageScenes.Space.ROOM) assertNotNull(msg, s.room)
        }
    }

    @Test fun naturalStartsAreNotFinishedRoomsAndTheFinalStagesKeepTheirPaintedRoom() {
        for (b in IsleBuilding.entries) {
            val start = StageScenes.of(b, 0)
            assertNull("$b 0", start.room)
            assertTrue("$b 0", start.outdoor && start.backdrop != StageScenes.Backdrop.INTERIOR)
            assertTrue("$b 0", (listOfNotNull(start.piece?.res) + start.props.map { it.res }).none { it in finished })
            assertEquals(b.background(), StageScenes.of(b, 3).room)
        }
    }

    @Test fun theFireRingHasNoHouseOrHammockAndTheHutIsAnInterior() {
        val foreign = setOf(R.drawable.decor_hammock, R.drawable.b_house, R.drawable.fief_hut_stage1, R.drawable.isle_manor, R.drawable.place_home)
        for (l in 0..3) {
            val s = StageScenes.of(IsleBuilding.CAMPFIRE, l)
            assertTrue("$l", (listOfNotNull(s.piece?.res, s.room) + s.props.map { it.res }).none { it in foreign })
        }
        val fire = StageScenes.of(IsleBuilding.CAMPFIRE, 0)
        assertEquals(R.drawable.lm_campfire, fire.piece!!.res); assertNotNull(fire.centre)
        val hut = StageScenes.of(IsleBuilding.CAMPFIRE, 1)
        assertEquals(StageScenes.Backdrop.INTERIOR, hut.backdrop); assertEquals(R.drawable.p_table, hut.piece!!.res); assertNotNull(hut.centre)
        // the meeting hut is a painted hut interior, not a composed plank room
        assertEquals(R.drawable.fief_build_closeup, hut.room)
        // the cottage stage is its living corner inside, not the outside view with things in front of it
        StageScenes.of(IsleBuilding.HOUSE, 2).let { assertEquals(StageScenes.Backdrop.INTERIOR, it.backdrop); assertNotNull(it.room); assertNull(it.piece) }
        // the house's hut stage is inside the hut, not its outside view
        assertEquals(R.drawable.fief_treasury_closeup, StageScenes.of(IsleBuilding.HOUSE, 1).room)
        assertEquals(StageScenes.Backdrop.INTERIOR, StageScenes.of(IsleBuilding.POST, 2).backdrop)
    }

    @Test fun everyStageLooksAndHoldsDifferentlyFromTheOthersOfItsBuilding() {
        for (b in IsleBuilding.entries) {
            val scenes = (0..3).map { StageScenes.of(b, it) }
            assertEquals("$b", 4, scenes.map { it.signature }.toSet().size)
            assertEquals("$b", 4, scenes.map { it.holder }.toSet().size)
            assertTrue("$b", scenes.map { it.caption }.toSet().size == 4 && scenes.all { it.caption.isNotBlank() })
            assertNotEquals("$b", scenes[0].holder, scenes[3].holder)
        }
    }

    @Test fun theDataHoldersFollowTheStage() {
        assertEquals(StageScenes.Holder.PINNED_ENVELOPE, StageScenes.of(IsleBuilding.POST, 0).holder)
        assertEquals(R.drawable.n_tree, StageScenes.of(IsleBuilding.POST, 0).piece!!.res)
        assertEquals(StageScenes.Holder.MAILBOX, StageScenes.of(IsleBuilding.POST, 1).holder)
        assertEquals(R.drawable.lm_post_ruin, StageScenes.of(IsleBuilding.POST, 1).piece!!.res)
        assertEquals(StageScenes.Holder.CUBBY, StageScenes.of(IsleBuilding.POST, 2).holder)
        assertEquals(StageScenes.Holder.CUBBY_WALL, StageScenes.of(IsleBuilding.POST, 3).holder)
        assertTrue(StageScenes.of(IsleBuilding.POST, 3).slots.size > StageScenes.of(IsleBuilding.POST, 2).slots.size)
        assertEquals(StageScenes.Holder.ROWBOAT, StageScenes.of(IsleBuilding.LIGHTHOUSE, 0).holder)
        assertEquals(StageScenes.Holder.SAILBOAT, StageScenes.of(IsleBuilding.LIGHTHOUSE, 1).holder)
        assertEquals(StageScenes.Backdrop.NIGHT_SEA, StageScenes.of(IsleBuilding.LIGHTHOUSE, 2).backdrop)
        assertEquals(StageScenes.Holder.FIRE_RING, StageScenes.of(IsleBuilding.CAMPFIRE, 0).holder)
        assertEquals(StageScenes.Holder.PARCHMENT, StageScenes.of(IsleBuilding.LIBRARY, 0).holder)
        assertEquals(StageScenes.Holder.NOTES_ON_POST, StageScenes.of(IsleBuilding.HARBOUR, 0).holder)
        assertEquals(StageScenes.Holder.KEEPSAKES, StageScenes.of(IsleBuilding.HOUSE, 0).holder)
        assertEquals(R.drawable.decor_hammock, StageScenes.of(IsleBuilding.HOUSE, 0).piece!!.res)
    }
}
