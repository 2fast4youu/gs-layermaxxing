package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 7.9.11 opt-in renders of all 24 building × stage views (production composables, fixture data) for a human review.
 * Skipped unless VILLAGE_SHOTS points to an output directory. Run with the filter '*StageSceneVisualReview*'.
 * The PNGs are the real Android window drawn on Robolectric (JVM), not device screenshots and not repainted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class StageSceneVisualReview {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = System.getenv("VILLAGE_SHOTS")?.let(::File)

    private fun shot(b: IsleBuilding, level: Int) {
        assumeTrue(out != null)
        rule.mainClock.autoAdvance = false
        rule.setContent { StageFixtures.Place(b, level) }
        repeat(40) { rule.mainClock.advanceTimeByFrame() }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val scene = StageScenes.of(b, level)
        rule.onAllNodesWithContentDescription("Ort: ${scene.stage.name}")[0].assertExists()
        rule.onAllNodesWithContentDescription("Bühne: ${scene.caption}")[0].assertExists()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        out!!.mkdirs()
        val name = "stufe-${b.name.lowercase()}-$level-" + scene.stage.name.lowercase()
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replace(Regex("[^a-z0-9]+"), "-").trim('-')
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun post0() = shot(IsleBuilding.POST, 0)
    @Test fun post1() = shot(IsleBuilding.POST, 1)
    @Test fun post2() = shot(IsleBuilding.POST, 2)
    @Test fun post3() = shot(IsleBuilding.POST, 3)
    @Test fun lighthouse0() = shot(IsleBuilding.LIGHTHOUSE, 0)
    @Test fun lighthouse1() = shot(IsleBuilding.LIGHTHOUSE, 1)
    @Test fun lighthouse2() = shot(IsleBuilding.LIGHTHOUSE, 2)
    @Test fun lighthouse3() = shot(IsleBuilding.LIGHTHOUSE, 3)
    @Test fun library0() = shot(IsleBuilding.LIBRARY, 0)
    @Test fun library1() = shot(IsleBuilding.LIBRARY, 1)
    @Test fun library2() = shot(IsleBuilding.LIBRARY, 2)
    @Test fun library3() = shot(IsleBuilding.LIBRARY, 3)
    @Test fun campfire0() = shot(IsleBuilding.CAMPFIRE, 0)
    @Test fun campfire1() = shot(IsleBuilding.CAMPFIRE, 1)
    @Test fun campfire2() = shot(IsleBuilding.CAMPFIRE, 2)
    @Test fun campfire3() = shot(IsleBuilding.CAMPFIRE, 3)
    @Test fun harbour0() = shot(IsleBuilding.HARBOUR, 0)
    @Test fun harbour1() = shot(IsleBuilding.HARBOUR, 1)
    @Test fun harbour2() = shot(IsleBuilding.HARBOUR, 2)
    @Test fun harbour3() = shot(IsleBuilding.HARBOUR, 3)
    @Test fun house0() = shot(IsleBuilding.HOUSE, 0)
    @Test fun house1() = shot(IsleBuilding.HOUSE, 1)
    @Test fun house2() = shot(IsleBuilding.HOUSE, 2)
    @Test fun house3() = shot(IsleBuilding.HOUSE, 3)
}
