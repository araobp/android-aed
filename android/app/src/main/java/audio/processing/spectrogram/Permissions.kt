package audio.processing.spectrogram

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

const val REQUEST_CODE_PERMISSIONS = 10
val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)

/**
 * Check if all permission specified in the manifest have been granted
 */
fun allPermissionsGranted(activity: Activity) = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(
        activity, it
    ) == PackageManager.PERMISSION_GRANTED
}