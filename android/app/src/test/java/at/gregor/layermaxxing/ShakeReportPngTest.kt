package at.gregor.layermaxxing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShakeReportPngTest {
    @Test fun realLosslessPixelsEmbeddedUtf8AndCorruptCrc() {
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.RED)
        val raw = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val diagnostics = "Shake report v1\nApp 83\nDiagnose für Gerät\n"
        val png = ShakePng.embed(raw, diagnostics)
        assertEquals(diagnostics, ShakePng.extract(png))
        val decoded = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertEquals(image.getPixel(0, 0), decoded.getPixel(0, 0))
        val bad = png.clone(); bad[bad.size - 1] = (bad.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { ShakePng.extract(bad) }
        assertThrows(IllegalArgumentException::class.java) { ShakePng.embed(raw, "Shake report v1\n" + "x".repeat(65536)) }
        assertThrows(IllegalArgumentException::class.java) { ShakePng.extract(png + byteArrayOf(0)) }
        image.recycle(); decoded.recycle()
    }
}
