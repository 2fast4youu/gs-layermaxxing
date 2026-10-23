package at.gregor.layermaxxing

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The complete existing opening/writing/attachments/crypto/gate/dedup/reduced-motion contract,
 * inherited unchanged and run against each production stage, with real paging to off-page letters.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class Post1EndToEndTest : LetterTreeEndToEndTest() { override val postLevel = 1 }
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class Post2EndToEndTest : LetterTreeEndToEndTest() { override val postLevel = 2 }
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-hdpi")
class Post3EndToEndTest : LetterTreeEndToEndTest() { override val postLevel = 3 }

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class Post1RecipientButtonsTest : CodexRecipientButtonsTest() { override val postLevel = 1 }
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class Post2RecipientButtonsTest : CodexRecipientButtonsTest() { override val postLevel = 2 }
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class Post3RecipientButtonsTest : CodexRecipientButtonsTest() { override val postLevel = 3 }
