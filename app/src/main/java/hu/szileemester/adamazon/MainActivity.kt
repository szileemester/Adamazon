package hu.szileemester.adamazon

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import hu.szileemester.adamazon.ui.AppRoot
import hu.szileemester.adamazon.ui.UpdateScreen
import hu.szileemester.adamazon.ui.theme.AdamazonTheme
import hu.szileemester.adamazon.ui.theme.isDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Play's update screen; leaving it without updating keeps the app on UpdateScreen.
    private val updateFlow = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AdamazonApp).container
        setContent {
            val mode by container.theme.mode.collectAsStateWithLifecycle()
            val updateRequired by container.updates.required.collectAsStateWithLifecycle()
            val dark = mode.isDark()
            // Status/navigation bar icons must follow the app's theme, not the system's.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AdamazonTheme(dark) {
                if (updateRequired) UpdateScreen(onUpdate = { container.updates.start(updateFlow) }) else AppRoot(container)
            }
        }
    }

    private var updateOffered = false

    /**
     * Checked on every return to the app, so a version released meanwhile is caught too. Play's
     * update screen opens by itself only once; after that UpdateScreen's buttons lead there.
     */
    override fun onResume() {
        super.onResume()
        val updates = (application as AdamazonApp).container.updates
        lifecycleScope.launch {
            if (updates.check() && !updateOffered) {
                updateOffered = true
                updates.start(updateFlow)
            }
        }
    }
}
