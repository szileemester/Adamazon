package hu.szileemester.adamazon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import hu.szileemester.adamazon.data.ThemeMode
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

/** Whether the app is currently drawn dark – follows the user's choice, not only the system. */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/** Material You colours on Android 12+, the app's blue scheme before that. */
@Composable
fun AdamazonTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, content = content)
    }
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
    return if (LocalDarkTheme.current) lerp(color, surface, 0.72f) else color
}
