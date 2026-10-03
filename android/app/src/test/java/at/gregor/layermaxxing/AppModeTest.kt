package at.gregor.layermaxxing

import org.junit.Assert.*
import org.junit.Test

class AppModeTest {
    @Test fun messengerNeverOffersGameTab() {
        assertEquals(MainTab.CHATS, AppMode.MESSENGER.startTab)
        assertFalse(AppMode.MESSENGER.showsValley)
        assertEquals(listOf(MainTab.CHATS, MainTab.MORE), AppMode.MESSENGER.tabs())
    }
    @Test fun gameStartsAndStaysInGameNavigation() {
        assertEquals(MainTab.CASTLES, AppMode.GAME.startTab)
        assertTrue(AppMode.GAME.showsValley)
        assertEquals(listOf(MainTab.CASTLES, MainTab.MORE), AppMode.GAME.tabs())
    }
    @Test fun combinedKeepsAllDestinations() {
        assertEquals(MainTab.CHATS, AppMode.BOTH.startTab)
        assertEquals(listOf(MainTab.CHATS, MainTab.CASTLES, MainTab.MORE), AppMode.BOTH.tabs())
    }
    @Test fun storedKeysRoundTripAndOverrideLegacyFlag() {
        AppMode.entries.forEach { assertEquals(it, AppMode.fromKey(it.key, !it.showsValley)) }
    }
    @Test fun upgradePreservesPreviousGameVisibility() {
        assertEquals(AppMode.MESSENGER, AppMode.fromKey(null, false))
        assertEquals(AppMode.BOTH, AppMode.fromKey(null, true))
        assertEquals(AppMode.MESSENGER, AppMode.fromKey("unknown", false))
    }
}
