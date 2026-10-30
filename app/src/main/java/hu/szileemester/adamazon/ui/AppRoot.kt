package hu.szileemester.adamazon.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import hu.szileemester.adamazon.AppContainer
import hu.szileemester.adamazon.data.AccessState
import hu.szileemester.adamazon.ui.cards.CardEditScreen
import hu.szileemester.adamazon.ui.cards.CardViewerScreen
import hu.szileemester.adamazon.ui.cards.CardsScreen
import hu.szileemester.adamazon.ui.list.StoreListScreen
import hu.szileemester.adamazon.ui.list.StoreSettingsScreen
import hu.szileemester.adamazon.ui.settings.SettingsScreen
import hu.szileemester.adamazon.ui.stores.StoresScreen

@Composable
fun AppRoot(container: AppContainer) {
    val state by container.auth.state.collectAsStateWithLifecycle()
    when (val s = state) {
        AccessState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AccessState.SignedOut -> SignInScreen()
        is AccessState.NoAccess -> NoAccessScreen(s.email)
        is AccessState.Error -> ErrorScreen(s.message)
        is AccessState.Allowed -> {
            LaunchedEffect(Unit) { container.categories.seedDefaultsIfEmpty() }
            MainNavigation()
        }
    }
}

object Routes {
    const val STORES = "stores"
    const val CARDS = "cards"
    const val SETTINGS = "settings"
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

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val bottomBar: @Composable () -> Unit = { BottomBar(nav) }
    NavHost(nav, startDestination = Routes.STORES) {
        composable(Routes.STORES) {
            StoresScreen(bottomBar = bottomBar, openStore = { nav.navigate(Routes.store(it)) })
        }
        composable(Routes.CARDS) {
            CardsScreen(
                bottomBar = bottomBar,
                openCard = { nav.navigate(Routes.card(it)) },
                newCard = { nav.navigate(Routes.cardEdit(null)) },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(bottomBar = bottomBar) }
        composable(Routes.STORE) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            StoreListScreen(
                storeId = id,
                back = { nav.popBackStack() },
                openSettings = { nav.navigate(Routes.storeSettings(id)) },
                openCard = { nav.navigate(Routes.card(it)) },
            )
        }
        composable(Routes.STORE_SETTINGS) { entry ->
            StoreSettingsScreen(
                storeId = entry.arguments?.getString("id").orEmpty(),
                back = { nav.popBackStack() },
                deleted = { nav.popBackStack(Routes.STORES, inclusive = false) },
            )
        }
        composable(Routes.CARD) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            CardViewerScreen(cardId = id, back = { nav.popBackStack() }, edit = { nav.navigate(Routes.cardEdit(id)) })
        }
        composable(Routes.CARD_EDIT) { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it != Routes.NEW }
            CardEditScreen(
                cardId = id,
                back = { nav.popBackStack() },
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
