package at.gregor.layermaxxing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStyleTest {
    @Test fun loneEmojiBecomeStickers() {
        assertTrue(ChatTools.isEmojiOnly("👍"))
        assertTrue(ChatTools.isEmojiOnly(" 😂😂 "))
        assertTrue(ChatTools.isEmojiOnly("👍🏽"))
        assertTrue(ChatTools.isEmojiOnly("👨‍👩‍👧"))
    }

    @Test fun textStaysInBubbles() {
        assertFalse(ChatTools.isEmojiOnly(""))
        assertFalse(ChatTools.isEmojiOnly("ok 👍"))
        assertFalse(ChatTools.isEmojiOnly("8"))
        assertFalse(ChatTools.isEmojiOnly("?!"))
        assertFalse(ChatTools.isEmojiOnly("😀😀😀😀"))
    }
}
