package at.gregor.layermaxxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileTest {
    @Test
    fun thirdProfileIsSeparateAndRequiresTestConfirmation() {
        assertEquals(3, ServerProfile.entries.size)
        assertEquals("Gregor (Testserver)", ServerProfile.GREGOR_TEST.label)
        assertEquals("test", ServerProfile.GREGOR_TEST.expectedRole)
        assertEquals(ServerProfile.GREGOR_TEST, ServerProfile.fromKey("gregor_test"))
        assertTrue(ServerProfilePolicy.showTestWarning(ServerProfile.GREGOR_TEST, null))
        assertTrue(ServerProfile.GREGOR_TEST.baseUrl.contains("/test-gerfried/"))
        assertFalse(ServerProfile.GERFRIED.baseUrl.contains("/test-gerfried/"))
        val values = mutableMapOf<String, String>()
        var cleared = false
        val store = ServerProfileStore({ values[it] }, { k, v -> values[k] = v }, { cleared = true })
        assertEquals(ServerProfile.GREGOR_TEST, store.selected)
        assertTrue(store.warningConfirmationRequired)
        store.acknowledgeWarning()
        store.select(ServerProfile.GERFRIED)
        assertTrue(cleared)
        assertTrue(store.warningConfirmationRequired)
    }
    @Test
    fun profileStorePersistsSelectionAndClearsSessionOnChange() {
        val values = mutableMapOf<String, String>()
        var cleared = false
        val store = ServerProfileStore(
            read = { key -> values[key] },
            write = { key, value -> values[key] = value },
            clearSession = { cleared = true },
        )

        assertEquals(ServerProfile.GREGOR_TEST, store.selected)
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
