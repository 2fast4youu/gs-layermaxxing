package at.gregor.layermaxxing

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Opt-in production composable evidence. CSV records actual paused-clock draw times and request counts.
 * Synthetic data only; these captures are not phone or release verification. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-hdpi")
class PostalPhoneFeedbackReview {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private fun record(level: Int, small: Boolean = false) {
        val destination = System.getenv("VILLAGE_SHOTS")?.takeIf { it.isNotBlank() }
        assumeTrue("opt-in: VILLAGE_SHOTS", destination != null)
        val out = File(requireNotNull(destination), "phone1253-post$level-${if (small) "small15" else "normal"}")
        require(!out.exists()) { "Use a fresh output directory" }; check(out.mkdirs())
        val h = CodexMotionHarness(rule)
        val csv = StringBuilder("file,clock_ms,phase,event,open_requests,locked_callbacks,camera,crack,lift,time_page,visible_real_ids\n")
        fun save(event: String) {
            h.frames()
            val file = "${csv.lines().size - 2}-$event.png"
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            var time = 0L
            rule.runOnIdle { view.draw(android.graphics.Canvas(bmp)); time = rule.mainClock.currentTime }
            File(out, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle()
            val motion = h.sample()
            csv.append("$file,$time,${h.phase()},$event,${h.calls},${h.locked.size},${motion.camera},${motion.crack},${motion.lift},${h.timePage()},${h.visibleMailIds().joinToString(";")}\n")
        }
        fun blank() { rule.onRoot().performTouchInput { click(Offset(width - 3f, 40f)) }; h.flushPointerState(); h.frames() }
        try {
            h.start(letters = PostPilotFixtures.letters, level = level, fontScale = if (small) 1.5f else 1f)
            val ordered = TreeOrder.order(PostPilotFixtures.letters, h.opened)
            val pageSize = if (level == 3) 9 else PostRegistration.of(level).sheets.size
            fun expected(page: Int) = PostalMailGroups.faces(TreeOrder.page(ordered, page, pageSize), level).map { it.id }
            check(h.timePage() == 0 && h.visibleMailIds() == expected(0))
            val currentIds = h.visibleMailIds()
            save("rest-controls")
            h.tag("post-time-rail").performTouchInput { swipeLeft(durationMillis = 350) }
            h.flushPointerState(); h.frames(20)
            val olderPage = h.timePage()
            check(olderPage > 0 && h.visibleMailIds() == expected(olderPage) && h.visibleMailIds() != currentIds) { "Older swipe must change the actual mail page and show its exact real IDs" }
            save("older-time")
            h.tag("post-time-rail").performTouchInput { swipeRight(durationMillis = 350) }
            h.flushPointerState(); h.frames(20)
            check(h.timePage() == 0 && h.visibleMailIds() == currentIds) { "Current swipe must restore the real mail" }
            save("current-time")
            h.touch("post-person"); save("person-search"); blank()
            h.touch("post-status"); save("status-counts"); blank()
            h.touch("post-mail"); save("real-mail-selection")
            h.scrollSelectorTo("post-select-3"); h.touch("post-select-3"); h.frames()
            h.tag("post-arrival").assertExists(); check(h.calls == 0 && h.locked.isEmpty())
            save("arrival"); blank()
            h.touch("post-mail"); h.frames(); h.scrollSelectorTo("post-select-1"); h.touch("post-select-1")
            h.frames(6); save("approach-mid-motion"); blank(); save("approach-skipped")
            h.touch("tree-envelope"); save("server-waiting"); check(h.phase() == ScenePhase.WAITING && h.calls == 1)
            h.back(); h.frames(5); save("cancel-return-mid-motion"); blank(); save("cancel-return-completed")
            rule.runOnIdle { h.answer.complete(Unit) }; h.frames(30); save("late-answer-stays-rest"); check(h.phase() == ScenePhase.REST && h.calls == 1)
            h.touch("post-mail"); h.frames(); h.scrollSelectorTo("post-select-6"); h.touch("post-select-6")
            h.until(ScenePhase.SEALED); h.touch("tree-envelope"); h.until(ScenePhase.UNSEALING)
            h.frames(8); save("unseal-mid-motion"); blank(); save("unseal-skipped-reading")
            check(h.phase() == ScenePhase.READING && h.calls == 2)
            h.tag("tree-lifted").performTouchInput { click(Offset(width - 6f, 10f)) }; h.flushPointerState(); h.frames()
            check(h.phase() == ScenePhase.READING && h.calls == 2)
            save("inside-paper-stays-reading")
            h.outside(); h.flushPointerState(); h.until(ScenePhase.REST); h.frames(20)
            check(h.timePage() == 0 && h.calls == 2 && h.locked.isEmpty())
            h.tag("post-controls").assertIsDisplayed(); h.tag("tree-lifted").assertDoesNotExist()
            save("outside-paper-full-rest")
        } finally {
            File(out, "frames.csv").writeText(csv.toString())
            File(out, "metadata.txt").writeText("Production PostOffice / LetterTreePlace; synthetic PostPilotFixtures. level=$level; fontScale=${if (small) 1.5 else 1.0}. CSV time_page is the actual zero-based page; visible_real_ids are the displayed envelope representatives (not every member accessible in the selector). Actual Compose mainClock times at native draw, no relabelled or generated frames. Partial sequences remain partial if a test fails. No backend traffic.")
        }
    }
    @Test fun post0() = record(0)
    @Test fun post1() = record(1)
    @Test fun post2() = record(2)
    @Test fun post3() = record(3)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun post0Small15() = record(0, true)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun post1Small15() = record(1, true)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun post2Small15() = record(2, true)
    @Test @Config(qualifiers = "w360dp-h640dp-hdpi") fun post3Small15() = record(3, true)
}
