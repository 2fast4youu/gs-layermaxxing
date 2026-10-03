package at.gregor.layermaxxing

import org.junit.Assert.*
import org.junit.Test

class VillageSceneTest {
    @Test fun everyServiceHasUniqueBuildingAndWorksWithoutFriends() {
        val scene = VillageScenes.valley(null)
        assertEquals(ValleyDestination.entries.size + 2, scene.sprites.size)
        assertEquals(scene.sprites.size, scene.sprites.map { it.id }.toSet().size)
        ValleyDestination.entries.forEach { d -> assertTrue(scene.sprites.any { it.id == d.name && it.interactive && it.hitPoint == d.anchor }) }
        assertEquals(VillageScenes.PLATE, scene.plate)
        assertTrue(scene.sprites.all { it.asset == VillageScenes.HIT && it.hitPoint.x in 0f..1f && it.hitPoint.y in 0f..1f })
    }
    @Test fun buildingCentersResolveCorrectlyAtPhoneAndLandscapeSizes() {
        val scene = VillageScenes.valley("Bea")
        listOf(MapSize(360f, 520f), MapSize(320f, 400f), MapSize(800f, 320f)).forEach { viewport ->
            listOf(1f, 1.6f, 2.5f).forEach { zoom ->
                scene.sprites.forEach { sprite ->
                    val pan = FiefMap.focusPan(sprite.hitPoint, viewport, scene.image, zoom)
                    val center = FiefMap.screenPoint(sprite.hitPoint, viewport, scene.image, zoom, pan)
                    assertEquals(sprite.id, FiefMap.hitTest(center, viewport, scene.image, zoom, pan, scene.sprites, 48f)?.id)
                }
            }
        }
    }
    @Test fun courtKeepsPostTreasuryAndBuildingSite() {
        assertEquals(setOf("cy-board", "cy-chest", "cy-build"), VillageScenes.courtyard().sprites.map { it.id }.toSet())
        assertEquals(VillageScenes.PLATE, VillageScenes.courtyard().plate)
    }
}
