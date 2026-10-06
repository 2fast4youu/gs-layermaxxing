package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class IslandHarboursTest {
    @Test fun docksSitOnEllipticalCoastInEveryDirection() {
        val center = Offset(100f, 100f)
        listOf(Offset(300f,100f), Offset(100f,300f), Offset(-100f,100f), Offset(100f,-100f), Offset(300f,300f)).forEach { target ->
            val p = IslandHarbours.shore(center, target, 100f) - center
            assertEquals(1f, p.x*p.x/(43f*43f)+p.y*p.y/(31f*31f), .001f)
            assertTrue((p.x * (target.x-center.x)+p.y * (target.y-center.y)) > 0f)
        }
    }
    @Test fun zeroDirectionIsFinite() {
        val p = IslandHarbours.shore(Offset.Zero, Offset.Zero, 100f)
        assertEquals(Offset(0f,31f),p)
    }
}
