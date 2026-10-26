package at.gregor.layermaxxing

import android.app.Activity
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShakeReportLifecycleTest {
    @Test fun defaultOffResumedAuthenticatedGatePauseDisableAndAccountInvalidation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSharedPreferences("shake-reports", Context.MODE_PRIVATE).edit().clear().commit()
        val session = SessionStore(activity)
        session.selectServerProfile(ServerProfile.GERFRIED)
        session.name = "synthetic"; session.token = "synthetic-token"
        val controller = ShakeReports(activity)
        try {
            assertFalse(controller.enabled); assertFalse(controller.autoUpload)
            controller.resume()
            controller.bind(session.token, false, ServerProfile.GERFRIED, "test", ApiClient())
            controller.enableCapture(true)
            assertFalse(controller.captureAvailable)
            controller.bind(session.token, true, ServerProfile.GERFRIED, "test", ApiClient())
            assertTrue(controller.captureAvailable)
            controller.pause(); assertFalse(controller.captureAvailable)
            controller.resume(); assertTrue(controller.captureAvailable)
            controller.setUploadConfirmed(true); assertTrue(controller.autoUpload)
            controller.enableCapture(false)
            assertFalse(controller.autoUpload); assertFalse(controller.captureAvailable)
            controller.enableCapture(true)
            session.token = "different-account-token"
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse(controller.captureAvailable)
            controller.unbind(); assertFalse(controller.captureAvailable)
        } finally { controller.destroy(); activity.finish() }
    }
}
