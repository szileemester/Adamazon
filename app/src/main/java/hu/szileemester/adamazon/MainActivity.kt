package hu.szileemester.adamazon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import hu.szileemester.adamazon.ui.AppRoot
import hu.szileemester.adamazon.ui.theme.AdamazonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AdamazonApp).container
        setContent {
            AdamazonTheme {
                AppRoot(container)
            }
        }
    }
}
