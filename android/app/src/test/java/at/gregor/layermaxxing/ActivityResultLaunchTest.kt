package at.gregor.layermaxxing

import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: with androidx.fragment < 1.3 (pulled in by biometric 1.1.0) a
 * FragmentActivity throws "Can only use lower 16 bits for requestCode" as soon as
 * an ActivityResult launcher starts. That crashed the app on the chat photo button
 * (photo picker) and the voice button (microphone permission).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityResultLaunchTest {
    private fun activity() = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    @Test fun photoPickerLaunchDoesNotCrash() {
        val a = activity()
        a.activityResultRegistry.register("photo", ActivityResultContracts.GetContent()) {}.launch("image/*")
    }

    @Test fun microphonePermissionLaunchDoesNotCrash() {
        val a = activity()
        a.activityResultRegistry.register("mic", ActivityResultContracts.RequestPermission()) {}
            .launch(android.Manifest.permission.RECORD_AUDIO)
    }
}
