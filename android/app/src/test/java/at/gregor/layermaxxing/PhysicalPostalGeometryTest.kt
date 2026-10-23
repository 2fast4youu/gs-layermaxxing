package at.gregor.layermaxxing

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class PhysicalPostalGeometryTest {
    @Test fun sourceFacesAndWoodMasksUseTheSameOriginalCanvas() {
        assertEquals(listOf(3, 6, 3), (1..3).map { PhysicalPostalStorage.slots(it).size })
        for (level in 1..3) for (slot in PhysicalPostalStorage.slots(level)) {
            assertEquals(4, slot.face.size); assertTrue(slot.lip.size >= 4)
            for (p in slot.face + slot.lip) assertTrue(p.x in 0f..1024f && p.y in 0f..1536f)
            assertTrue(slot.ambient in .4f..<1f)
            assertTrue("slight lift must not pull a letter through the shelf above", slot.lift in 16f..64f)
        }
    }
    @Test fun exposedTapRectanglesDoNotChooseTheLetterInFront() {
        for (level in 1..3) {
            val size = PhysicalPostalStorage.slots(level).size
            for (occupied in 1..size) for (i in 0 until occupied) {
                val b = PhysicalPostalStorage.hitBox(level, i, occupied)
                assertTrue(b.l < b.r && b.t < b.b)
                for (fx in listOf(.05f,.5f,.95f)) for (fy in listOf(.05f,.5f,.95f)) {
                    val p = Offset((b.l + (b.r-b.l)*fx)*1024f, (b.t+(b.b-b.t)*fy)*1536f)
                    assertTrue("level=$level slot=$i occupied=$occupied point=$p", PhysicalPostalStorage.visible(level,i,occupied,p))
                }
            }
        }
    }
    @Test fun liftClearsBeforeRotationAndCameraAdvance() {
        for (level in 1..3) for (index in PhysicalPostalStorage.slots(level).indices) {
            val start = PhysicalPostalStorage.frame(level,index,0f)!!
            assertEquals(start.slot.face,start.corners)
            for (i in 0..42) {
                val p = i/100f;val frame=PhysicalPostalStorage.frame(level,index,p)!!
                assertEquals(0f,frame.turn,0f);assertEquals(0f,PostalStorageMotion.camera(p),0f)
            }
            val raised=PhysicalPostalStorage.frame(level,index,.42f)!!
            assertEquals(1f,raised.clearance,0f);assertEquals(-raised.slot.lift,raised.dy,0f)
            assertEquals(raised.slot.ambient,raised.light,0f)
        }
    }
    @Test fun sourceProjectionAndCavityLightingReverseWithoutASecondPath() {
        for (level in 1..3) for (index in PhysicalPostalStorage.slots(level).indices) {
            var previous = PhysicalPostalStorage.frame(level,index,0f)!!
            for (i in 0..100) {
                val p=i/100f;val frame=PhysicalPostalStorage.frame(level,index,p)!!
                assertTrue(frame.clearance >= previous.clearance && frame.turn >= previous.turn && frame.light >= previous.light)
                assertTrue(frame.light in frame.slot.ambient..1f)
                assertEquals(frame.corners,PhysicalPostalStorage.frame(level,index,p)!!.corners)
                previous=frame
            }
            assertEquals(1f,previous.turn,0f);assertEquals(1f,previous.light,0f)
            val c=previous.corners
            assertEquals(c[0].y,c[1].y,0f);assertEquals(c[2].y,c[3].y,0f)
            assertEquals(c[0].x,c[3].x,0f);assertEquals(c[1].x,c[2].x,0f)
        }
    }
}
