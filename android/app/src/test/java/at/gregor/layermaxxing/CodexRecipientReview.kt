package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Opt-in native captures of the production scene, reached by real taps. TESTDATEN: PostPilotFixtures. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CodexRecipientReview {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = System.getenv("VILLAGE_SHOTS")?.let(::File)
    private fun save(name: String) {
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(android.graphics.Canvas(bmp))
            out!!.mkdirs()
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bmp.recycle() }
    }
    private fun render(prefix: String, fontScale: Float) {
        assumeTrue("Set VILLAGE_SHOTS to render recipient buttons", out != null)
        val scene = CodexRecipientScenario(rule)
        scene.start(fontScale)
        save("$prefix-01-empfaenger")
        scene.tap("tree-to-40"); scene.tap("tree-write-to"); scene.tap("tree-to-41")
        scene.selected("tree-to-40", true); scene.selected("tree-to-41", true)
        save("$prefix-02-personen-gewaehlt")
        scene.tap("tree-to-group-70"); scene.selected("tree-to-group-70", true)
        save("$prefix-03-gruppe-gewaehlt")
    }
    @Test fun recipientsNormal() = render("codex-recipient-normal", 1f)
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    @Test fun recipientsSmall15() = render("codex-recipient-klein15", 1.5f)
}
