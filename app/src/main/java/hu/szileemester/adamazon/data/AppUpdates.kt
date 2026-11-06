package hu.szileemester.adamazon.data

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Keeps everyone on the newest version: when Google Play has a newer one, the app stops until it
 * is installed (Play's full-screen "immediate" update). The whole group must run the same version,
 * as new versions may need new security rules.
 *
 * Only an app installed from Google Play can ask; a sideloaded one (or no network) never blocks.
 */
class AppUpdates(context: Context) {
    private val manager = AppUpdateManagerFactory.create(context)
    private var info: AppUpdateInfo? = null

    private val _required = MutableStateFlow(false)
    val required: StateFlow<Boolean> = _required.asStateFlow()

    /** Asks Play for a newer version; true if one must be installed now. */
    suspend fun check(): Boolean {
        val latest = runCatching { manager.appUpdateInfo.await() }.getOrNull()
        info = latest
        val availability = latest?.updateAvailability()
        _required.value = latest != null && (
            // An update the user already started (and left) is resumed, not asked again.
            availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ||
                (availability == UpdateAvailability.UPDATE_AVAILABLE && latest.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE))
            )
        return _required.value
    }

    /** Opens Play's full-screen update; false if Play cannot do it (then the store page helps). */
    fun start(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        val latest = info ?: return false
        return runCatching {
            manager.startUpdateFlowForResult(latest, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        }.getOrDefault(false)
    }
}
