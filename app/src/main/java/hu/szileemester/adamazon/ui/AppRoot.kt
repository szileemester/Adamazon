package hu.szileemester.adamazon.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.data.AccessState
import hu.szileemester.adamazon.data.DataErrors
import hu.szileemester.adamazon.data.GroupSession
import hu.szileemester.adamazon.ui.cards.CardEditScreen
import hu.szileemester.adamazon.ui.help.HelpScreen
import hu.szileemester.adamazon.ui.cards.CardViewerScreen
import hu.szileemester.adamazon.ui.cards.CardsScreen
import hu.szileemester.adamazon.ui.list.StoreListScreen
import hu.szileemester.adamazon.ui.list.StoreSettingsScreen
import hu.szileemester.adamazon.ui.settings.CategoriesScreen
import hu.szileemester.adamazon.ui.settings.SettingsScreen
import hu.szileemester.adamazon.ui.stores.StoreOrderScreen
import hu.szileemester.adamazon.ui.stores.StoresScreen

@Composable
fun AppRoot(container: AppContainer) {
    val state by container.auth.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // Several listeners usually fail together; one toast per message is enough.
        var last = "" to 0L
        DataErrors.messages.collect { message ->
            val now = System.currentTimeMillis()
            if (message != last.first || now - last.second > 10_000) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            last = message to now
        }
    }
    when (val s = state) {
        AccessState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AccessState.SignedOut -> SignInScreen()
        is AccessState.NoAccess -> NoAccessScreen(s.email)
        is AccessState.NoGroup -> NoGroupScreen(s.email)
        is AccessState.Error -> ErrorScreen(s.message)
        is AccessState.Allowed -> {
            val session = remember(s.group.id) { container.openSession(s.group.id) }
            if (s.group.needsLegacyImport) {
                // Ends by itself: the copy sets migratedAt, and the live group update clears the flag.
                MigrationScreen(session)
            } else {
                // A new group (also after a move to another group) starts with fresh screens.
                key(session.groupId) {
                    LaunchedEffect(Unit) { session.categories.seedDefaultsIfEmpty() }
                    val helpSeen by container.help.seen.collectAsStateWithLifecycle()
                    // The feature tour opens by itself once per device; later it is in Settings.
                    if (helpSeen) MainNavigation() else HelpScreen(onClose = container.help::markSeen)
                }
            }
        }
    }
}

@Composable
private fun MigrationScreen(session: GroupSession) {
    var error by remember(session) { mutableStateOf<String?>(null) }
    var attempt by remember(session) { mutableIntStateOf(0) }
    LaunchedEffect(session, attempt) {
        error = null
        runCatching { session.migration.run() }.onFailure { error = it.message ?: it.javaClass.simpleName }
    }
    val message = error
    if (message == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Adatok átköltöztetése…")
        }
    } else {
        ErrorScreen("Az adatok átköltöztetése nem sikerült: $message", onRetry = { attempt++ })
    }
}

object Routes {
    const val STORES = "stores"
    const val STORE_ORDER = "stores/order"
    const val CARDS = "cards"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val CATEGORIES = "settings/categories"
    const val STORE = "store/{id}"
    const val STORE_SETTINGS = "store/{id}/settings"
    const val CARD = "card/{id}"
    const val CARD_EDIT = "card/{id}/edit"
    const val NEW = "new"

    fun store(id: String) = "store/$id"
    fun storeSettings(id: String) = "store/$id/settings"
    fun card(id: String) = "card/$id"
    fun cardEdit(id: String?) = "card/${id ?: NEW}/edit"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.STORES, "Listák", Icons.Default.ShoppingCart),
    Tab(Routes.CARDS, "Kártyák", Icons.Default.CreditCard),
    Tab(Routes.SETTINGS, "Beállítások", Icons.Default.Settings),
)

/**
 * Goes back one screen, but never past the first one: a quick double tap on a back arrow would
 * otherwise also pop the start screen and leave an empty window with no way out.
 */
private fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val bottomBar: @Composable () -> Unit = { BottomBar(nav) }
    NavHost(nav, startDestination = Routes.STORES) {
        composable(Routes.STORES) {
            StoresScreen(
                bottomBar = bottomBar,
                openStore = { nav.navigate(Routes.store(it)) },
                openOrder = { nav.navigate(Routes.STORE_ORDER) },
            )
        }
        composable(Routes.STORE_ORDER) {
            StoreOrderScreen(back = nav::back)
        }
        composable(Routes.CARDS) {
            CardsScreen(
                bottomBar = bottomBar,
                openCard = { nav.navigate(Routes.card(it)) },
                newCard = { nav.navigate(Routes.cardEdit(null)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                bottomBar = bottomBar,
                openCategories = { nav.navigate(Routes.CATEGORIES) },
                openHelp = { nav.navigate(Routes.HELP) },
            )
        }
        composable(Routes.CATEGORIES) { CategoriesScreen(back = nav::back) }
        composable(Routes.HELP) { HelpScreen(onClose = nav::back) }
        composable(Routes.STORE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            StoreListScreen(
                storeId = id,
                back = nav::back,
                openSettings = { nav.navigate(Routes.storeSettings(id)) },
                openCard = { nav.navigate(Routes.card(it)) },
            )
        }
        composable(Routes.STORE_SETTINGS) { entry ->
            StoreSettingsScreen(
                storeId = entry.arguments?.getString("id").orEmpty(),
                back = nav::back,
                deleted = { nav.popBackStack(Routes.STORES, inclusive = false) },
            )
        }
        composable(Routes.CARD) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            CardViewerScreen(cardId = id, back = nav::back, edit = { nav.navigate(Routes.cardEdit(id)) })
        }
        composable(Routes.CARD_EDIT) { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it != Routes.NEW }
            CardEditScreen(
                cardId = id,
                back = nav::back,
                deleted = { nav.popBackStack(Routes.CARDS, inclusive = false) },
            )
        }
    }
}

@Composable
private fun BottomBar(nav: NavController) {
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
    }
}
