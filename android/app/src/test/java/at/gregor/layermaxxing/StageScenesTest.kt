package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 7.9.14 (style E): every building × stage 0..3 (24 combinations) is its own painted place (the generated detail picture
 * of that stage, e_<b>_<s>_scene) with the surfaces the data lives on; no old background, no set piece, no props over
 * the picture. Names follow the approved stories (Nachtlager, Briefstube, Signalstation, Schreibplatz, Lagerfeuer,
 * Anlegestelle …). The surfaces' positions on the rendered views are checked in StageSceneAnchorTest.
 */
class StageScenesTest {
    /** The 7.9.13 backgrounds and set pieces that must not come back as a stage's place. */
    private val old = setOf(R.drawable.bg_post, R.drawable.bg_hall, R.drawable.bg_house, R.drawable.bg_library, R.drawable.bg_lighthouse,
        R.drawable.bg_harbour, R.drawable.fief_build_closeup, R.drawable.fief_treasury_closeup, R.drawable.n_tree, R.drawable.lm_post_ruin,
        R.drawable.decor_hammock, R.drawable.decor_palm, R.drawable.n_crates, R.drawable.lm_campfire, R.drawable.p_table, R.drawable.fief_signpost,
        R.drawable.fief_jetty, R.drawable.b_board)

    private val scenePictures = mapOf(
        IsleBuilding.HOUSE to listOf(R.drawable.e_house_0_scene, R.drawable.e_house_1_scene, R.drawable.e_house_2_scene, R.drawable.e_house_3_scene),
        IsleBuilding.POST to listOf(R.drawable.e_post_0_scene, R.drawable.e_post_1_scene, R.drawable.e_post_2_scene, R.drawable.e_post_3_scene),
        IsleBuilding.LIGHTHOUSE to listOf(R.drawable.e_lighthouse_0_scene, R.drawable.e_lighthouse_1_scene, R.drawable.e_lighthouse_2_scene, R.drawable.e_lighthouse_3_scene),
        IsleBuilding.LIBRARY to listOf(R.drawable.e_library_0_scene, R.drawable.e_library_1_scene, R.drawable.e_library_2_scene, R.drawable.e_library_3_scene),
        IsleBuilding.CAMPFIRE to listOf(R.drawable.e_campfire_0_scene, R.drawable.e_campfire_1_scene, R.drawable.e_campfire_2_scene, R.drawable.e_campfire_3_scene),
        IsleBuilding.HARBOUR to listOf(R.drawable.e_harbour_0_scene, R.drawable.e_harbour_1_scene, R.drawable.e_harbour_2_scene, R.drawable.e_harbour_3_scene),
    )
    private val sprites = mapOf(
        IsleBuilding.HOUSE to listOf(R.drawable.e_house_0, R.drawable.e_house_1, R.drawable.e_house_2, R.drawable.e_house_3),
        IsleBuilding.POST to listOf(R.drawable.e_post_0, R.drawable.e_post_1, R.drawable.e_post_2, R.drawable.e_post_3),
        IsleBuilding.LIGHTHOUSE to listOf(R.drawable.e_lighthouse_0, R.drawable.e_lighthouse_1, R.drawable.e_lighthouse_2, R.drawable.e_lighthouse_3),
        IsleBuilding.LIBRARY to listOf(R.drawable.e_library_0, R.drawable.e_library_1, R.drawable.e_library_2, R.drawable.e_library_3),
        IsleBuilding.CAMPFIRE to listOf(R.drawable.e_campfire_0, R.drawable.e_campfire_1, R.drawable.e_campfire_2, R.drawable.e_campfire_3),
        IsleBuilding.HARBOUR to listOf(R.drawable.e_harbour_0, R.drawable.e_harbour_1, R.drawable.e_harbour_2, R.drawable.e_harbour_3),
    )

    @Test fun twentyFourScenesOnePerBuildingAndStage() {
        val all = StageScenes.all
        assertEquals(24, all.size)
        assertEquals(IsleBuilding.entries.flatMap { b -> (0..3).map { b to it } }.toSet(), all.map { it.building to it.level }.toSet())
        for (b in IsleBuilding.entries) for (l in 0..3) assertEquals(StageScenes.of(b, l), all.single { it.building == b && it.level == l })
    }

    @Test fun theApprovedStageNamesAndTheAssetIdentityOfEveryStage() {
        val names = mapOf(
            IsleBuilding.HOUSE to listOf("Nachtlager", "Unterschlupf", "Holzhütte", "Landhaus"),
            IsleBuilding.POST to listOf("Briefbaum", "Postkasten", "Briefstube", "Postamt"),
            IsleBuilding.LIGHTHOUSE to listOf("Aussichtspalme", "Ausguck", "Signalstation", "Leuchtturm"),
            IsleBuilding.LIBRARY to listOf("Schreibplatz", "Lesedach", "Schreibstube", "Bibliothek"),
            IsleBuilding.CAMPFIRE to listOf("Lagerfeuer", "Treffplatz", "Versammlungshaus", "Glashaus"),
            IsleBuilding.HARBOUR to listOf("Anlegestelle", "Bootssteg", "Anleger", "Hafen"),
        )
        for ((b, list) in names) for (l in 0..3) {
            val s = StageScenes.of(b, l)
            val st = IslandStages.of(b, l)
            assertEquals(list[l], st.name)
            assertEquals(IslandStages.function(b), st.function)
            // one picture family per stage: the island sprite, the header picture and the painted place
            assertEquals("$b $l sprite", sprites.getValue(b)[l], st.art)
            assertEquals("$b $l header", st.art, s.hero)
            assertEquals("$b $l scene", scenePictures.getValue(b)[l], s.picture)
            assertEquals(s.picture, st.scene)
            assertEquals("$b $l frame", st.art, StyleEArt.of(b, l).res)
        }
        assertEquals(IslandEvolution.primitiveName(IsleBuilding.HOUSE), "Nachtlager")
        // the night camp has no hammock anywhere (name, look, story); the 7.9.13 picture stays only for the version 5 contract
        val camp = IslandStages.of(IsleBuilding.HOUSE, 0)
        assertFalse(camp.look.contains("Hängematte"))
        assertFalse(StageStory.of(IsleBuilding.HOUSE, 0).core.contains("Hängematte"))
        assertNotEquals(R.drawable.decor_hammock, camp.art)
        assertEquals("v5 masks were measured from it", R.drawable.decor_hammock, camp.res)
    }

    @Test fun everyStageIsItsOwnPaintedPlaceNotAnOldBackgroundWithProps() {
        val pictures = StageScenes.all.map { it.picture }
        assertEquals("24 different painted places", 24, pictures.toSet().size)
        for (s in StageScenes.all) {
            val msg = "${s.building} ${s.level}"
            assertTrue("$msg reuses an old picture", s.picture !in old)
            assertTrue(msg, s.caption.isNotBlank())
            assertTrue(msg, s.surfaces.isNotEmpty())
            s.surfaces.forEach { (k, r) ->
                assertTrue("$msg $k inside the picture $r", r.left >= 0f && r.top >= 0f && r.right <= 1f && r.bottom <= 1f)
                assertTrue("$msg $k has an area $r", r.width > .05f && r.height > .03f)
                // the calm header zone of the picture (top 9 %) is the header's
                assertTrue("$msg $k under the header zone", r.top >= .09f)
            }
        }
    }

    @Test fun everyBuildingKeepsItsSurfacesOnEveryStage() {
        val need = mapOf(
            IsleBuilding.POST to setOf(StageScenes.Surface.TABS, StageScenes.Surface.PEOPLE, StageScenes.Surface.LETTERS),
            IsleBuilding.HOUSE to setOf(StageScenes.Surface.AVATAR, StageScenes.Surface.NAME, StageScenes.Surface.NOTES, StageScenes.Surface.COUNTERS),
            IsleBuilding.LIGHTHOUSE to setOf(StageScenes.Surface.SEA),
            IsleBuilding.LIBRARY to setOf(StageScenes.Surface.BOOK, StageScenes.Surface.REGISTER),
            IsleBuilding.CAMPFIRE to setOf(StageScenes.Surface.SIGN, StageScenes.Surface.PICKER),
            IsleBuilding.HARBOUR to setOf(StageScenes.Surface.BOARD),
        )
        for ((b, set) in need) for (l in 0..3) {
            val s = StageScenes.of(b, l)
            assertTrue("$b $l ${s.surfaces.keys}", s.surfaces.keys.containsAll(set))
        }
        // the groups sit around a real centre (fire / table) on every stage
        for (l in 0..3) assertNotNull(StageScenes.of(IsleBuilding.CAMPFIRE, l).centre)
    }

    @Test fun everyStageLooksAndHoldsDifferentlyFromTheOthersOfItsBuilding() {
        for (b in IsleBuilding.entries) {
            val scenes = (0..3).map { StageScenes.of(b, it) }
            assertEquals("$b", 4, scenes.map { it.signature }.toSet().size)
            assertEquals("$b", 4, scenes.map { it.holder }.toSet().size)
            assertTrue("$b", scenes.map { it.caption }.toSet().size == 4)
        }
    }

    @Test fun theStoriesFollowTheApprovedConcepts() {
        // groups: cosy round tables, no compartments, no courtyard houses
        for (l in 0..3) StageScenes.of(IsleBuilding.CAMPFIRE, l).caption.let { c ->
            assertFalse(c, c.contains("Abteil") || c.contains("Hofh") || c.contains("Gemeinschaftshof"))
        }
        assertTrue(StageScenes.of(IsleBuilding.CAMPFIRE, 2).caption.contains("Kamin"))
        assertTrue(StageScenes.of(IsleBuilding.CAMPFIRE, 3).caption.contains("Glashaus"))
        // the harbour: always a landing that starts on the shore; boats are letter boats only, quests stay notes
        for (l in 0..3) {
            val s = StageScenes.of(IsleBuilding.HARBOUR, l)
            assertTrue(s.caption, s.caption.contains("Steg") || s.caption.contains("Kai") || s.caption.contains("Anleger"))
            StageStory.of(IsleBuilding.HARBOUR, l).let { ch ->
                assertFalse(ch.core, ch.core.contains("Aufgabe") && ch.core.contains("Boot"))
                ch.items.filter { it.act == StageStory.Act.ARRIVALS }.forEach { assertTrue(it.label, it.label.contains("Brief")) }
            }
        }
        // the post: the letters are nailed to the trunk at the start, a real drawer follows
        assertTrue(StageScenes.of(IsleBuilding.POST, 0).caption.contains("genagelt"))
        assertEquals(StageScenes.Holder.MAIL_DRAWER, StageScenes.of(IsleBuilding.POST, 1).holder)
        // friends: always at the island's edge, looking out to sea
        for (l in 0..3) assertNotNull(StageScenes.of(IsleBuilding.LIGHTHOUSE, l).surface(StageScenes.Surface.SEA))
    }
}
