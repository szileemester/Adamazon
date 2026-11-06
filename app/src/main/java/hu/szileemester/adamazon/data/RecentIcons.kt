package hu.szileemester.adamazon.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The icons picked most recently on this device, newest first, shown on top of the icon picker. */
class RecentIcons(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _icons = MutableStateFlow(prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty())
    val icons: StateFlow<List<String>> = _icons.asStateFlow()

    fun used(icon: String) {
        val next = (listOf(icon) + _icons.value.filter { it != icon }).take(MAX)
        prefs.edit().putString(KEY, next.joinToString(SEPARATOR)).apply()
        _icons.value = next
    }

    private companion object {
        const val KEY = "recent_icons"
        const val SEPARATOR = "\n"
        const val MAX = 24
    }
}
