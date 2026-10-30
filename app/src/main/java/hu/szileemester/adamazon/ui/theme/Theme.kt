package hu.szileemester.adamazon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF1E88E5),
    tertiary = Color(0xFFFF8F00),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF64B5F6),
    tertiary = Color(0xFFFFB74D),
)

/** Material You colours on Android 12+, the app's blue scheme before that. */
@Composable
fun AdamazonTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Keep-style note colours; 0 means "no colour". */
val StoreColors = listOf(
    0L,
    0xFFFFD8D8, 0xFFFFE2C8, 0xFFFFF3C4, 0xFFD5EFD8, 0xFFD0EEF0,
    0xFFD6E6FA, 0xFFE4DCF5, 0xFFF8DDEB, 0xFFEEE3D6, 0xFFE8EAED,
)

/** Background for a store card; pastel colours are muted in dark mode like in Keep. */
@Composable
fun storeContainerColor(argb: Long): Color {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    if (argb == 0L) return surface
    val color = Color(argb)
    return if (isSystemInDarkTheme()) lerp(color, surface, 0.72f) else color
}
