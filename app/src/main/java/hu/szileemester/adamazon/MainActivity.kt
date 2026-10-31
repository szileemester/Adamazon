package hu.szileemester.adamazon

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.szileemester.adamazon.ui.AppRoot
import hu.szileemester.adamazon.ui.theme.AdamazonTheme
import hu.szileemester.adamazon.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AdamazonApp).container
        setContent {
            val mode by container.theme.mode.collectAsStateWithLifecycle()
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
                AppRoot(container)
            }
        }
    }
}
