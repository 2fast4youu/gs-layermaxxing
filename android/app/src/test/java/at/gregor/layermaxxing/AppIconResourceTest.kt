package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The launcher icon, checked as the resources it actually ships as.
 *
 * A Compose unit test cannot inflate a launcher icon, so this reads the res
 * tree: every icon path the manifest names has to resolve, the adaptive icon
 * has to carry a monochrome layer, and the mark itself has to be the two Gs
 * — two ring paths of identical geometry, no text and no lock glyph.
 */
class AppIconResourceTest {

    private val res = File("src/main/res")
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun resource(path: String) = File(res, path)

    /**
     * The centre of a G's ring, recovered from its path.
     *
     * The ring starts and ends on the same vertical, half a chord above and
     * below the centre line, so the centre sits one sagitta to the left of that
     * vertical — the same construction the drawable is written with.
     */
    private fun ringCentre(pathData: String): Pair<Double, Double> {
        val (startX, startY) = pathData.removePrefix("M").substringBefore(" ")
            .split(",").map { it.toDouble() }
        val (_, endY) = pathData.substringAfter("0 1 0 ").substringBefore(" L")
            .split(",").map { it.toDouble() }
        val centreY = (startY + endY) / 2
        val halfChord = kotlin.math.abs(endY - startY) / 2
        return startX - kotlin.math.sqrt(RADIUS * RADIUS - halfChord * halfChord) to centreY
    }

    private companion object {
        const val RADIUS = 17.0
        /** The same radius as it is written in the path data. */
        const val RADIUS_TEXT = "17"
    }

    @Test
    fun manifestPointsAtIconsThatExist() {
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        listOf("mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml").forEach {
            assertTrue("$it must exist", resource(it).isFile)
        }
    }

    @Test
    fun bothAdaptiveIconsCarryForegroundBackgroundAndMonochrome() {
        listOf("mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml").forEach { path ->
            val xml = resource(path).readText()
            assertTrue("$path needs a background", xml.contains("<background"))
            assertTrue("$path needs a foreground", xml.contains("@drawable/ic_launcher_foreground"))
            assertTrue("$path needs a themed layer", xml.contains("@drawable/ic_launcher_monochrome"))
        }
        // Every drawable the adaptive icons reference has to be present.
        listOf("drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml").forEach {
            assertTrue("$it must exist", resource(it).isFile)
        }
        assertTrue(resource("values/colors.xml").readText().contains("launcher_background"))
    }

    @Test
    fun theMarkIsTwoInterlockingGsAndNothingElse() {
        val foreground = resource("drawable/ic_launcher_foreground.xml").readText()
        // Two ring-and-bar paths, one per G, with the same radius — and nothing
        // else: no third stroke that could be mistaken for a letter.
        val paths = Regex("""android:pathData="([^"]+)"""").findAll(foreground)
            .map { it.groupValues[1] }.toList()
        assertEquals("the mark must be exactly two Gs", 2, paths.size)
        paths.forEach { data ->
            assertTrue("each G is one 300° ring: $data", data.contains("A$RADIUS_TEXT,$RADIUS_TEXT 0 1 0"))
            // The spur and the inward bar are what make a ring a G rather than an O.
            assertEquals("each G needs a spur and a bar: $data", 2, data.split(" L").size - 1)
        }
        // The two rings have to hook into each other, or this is two letters
        // standing next to each other rather than two letters interlocking.
        val (backX, backY) = ringCentre(paths[0])
        val (frontX, frontY) = ringCentre(paths[1])
        val distance = kotlin.math.hypot(backX - frontX, backY - frontY)
        assertTrue("the rings must overlap, got $distance", distance < 2 * RADIUS)
        assertTrue("the rings must not collapse onto each other", distance > RADIUS)
        // Diagonal, not side by side: a horizontal pair fuses both bars into one
        // stroke and stops reading as two letters.
        assertTrue("the two Gs must be offset vertically", kotlin.math.abs(backY - frontY) > 8.0)
        // No text and no generic padlock, in any icon layer.
        listOf("drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml", "drawable/brand_mark.xml")
            .forEach { path ->
                val xml = resource(path).readText()
                assertFalse("$path must not contain text", xml.contains("<text", ignoreCase = true))
                assertFalse("$path must not contain a lock glyph", xml.contains("🔒"))
            }
    }

    @Test
    fun theOldRasterIconIsGoneSoOnlyTheNewMarkShips() {
        // minSdk 26 resolves the adaptive icon on every device, so a leftover
        // ic_launcher.png would only ship the previous mark inside the APK.
        val strays = res.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("mipmap") }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.extension == "png" }
        assertTrue("stale raster launcher icons: $strays", strays.isEmpty())
    }

    @Test
    fun theInAppMarkIsTheSameSignAsTheLauncherIcon() {
        val foreground = resource("drawable/ic_launcher_foreground.xml").readText()
        val brand = resource("drawable/brand_mark.xml").readText()
        val letters = Regex("""android:pathData="([^"]*A$RADIUS_TEXT,$RADIUS_TEXT[^"]*)"""").findAll(foreground)
            .map { it.groupValues[1] }.toList()
        assertEquals(2, letters.size)
        letters.forEach { data -> assertTrue("brand_mark must carry $data", brand.contains(data)) }
    }
}
