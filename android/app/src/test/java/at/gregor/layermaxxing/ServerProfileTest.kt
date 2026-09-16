package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileTest {
    @Test
    fun profileStorePersistsSelectionAndClearsSessionOnChange() {
        val values = mutableMapOf<String, String>()
        var cleared = false
        val store = ServerProfileStore(
            read = { key -> values[key] },
            write = { key, value -> values[key] = value },
            clearSession = { cleared = true },
        )

        assertEquals(ServerProfile.GERFRIED, store.selected)
        assertTrue(store.warningConfirmationRequired)
        store.acknowledgeWarning()
        assertFalse(store.warningConfirmationRequired)

        store.select(ServerProfile.GREGOR)
        assertTrue(cleared)
        assertEquals(ServerProfile.GREGOR, store.selected)
        assertFalse(store.warningConfirmationRequired)
        cleared = false
        store.select(ServerProfile.GREGOR)
        assertFalse(cleared)

        store.select(ServerProfile.GERFRIED)
        assertTrue(store.warningConfirmationRequired)
    }

    @Test
    fun productionPlaceholderIsNotConfiguredAndRolesControlWarning() {
        assertFalse(ServerProfile.GREGOR.isConfigured("https://example.invalid/"))
        assertTrue(ServerProfile.GERFRIED.isConfigured("https://headless.tail586ff8.ts.net/layermaxxing/"))
        assertTrue(ServerProfilePolicy.showTestWarning(ServerProfile.GERFRIED, null))
        assertTrue(ServerProfilePolicy.showTestWarning(ServerProfile.GREGOR, "test"))
        assertFalse(ServerProfilePolicy.showTestWarning(ServerProfile.GREGOR, "production"))
        assertFalse(ServerProfilePolicy.showTestWarning(ServerProfile.GERFRIED, "production"))
        // Unverified: the test profile keeps warning, the production profile does not.
        assertTrue(ServerProfilePolicy.showTestWarning(ServerProfile.GERFRIED, ""))
        assertFalse(ServerProfilePolicy.showTestWarning(ServerProfile.GREGOR, null))
        assertNull(ServerProfile.fromKey("unknown"))
    }
}
