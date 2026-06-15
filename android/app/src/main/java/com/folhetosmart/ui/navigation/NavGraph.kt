package com.folhetosmart.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.folhetosmart.R
import com.folhetosmart.features.admin.AdminScreen
import com.folhetosmart.features.alerts.AlertsScreen
import com.folhetosmart.features.compare.CompareScreen
import com.folhetosmart.features.legal.PrivacyPolicyScreen
import com.folhetosmart.features.legal.TermsOfServiceScreen
import com.folhetosmart.features.list.ListScreen
import com.folhetosmart.features.settings.SettingsScreen
import com.folhetosmart.features.sync.SyncScreen

/** Rotas fora da bottom navigation (documentos legais). */
const val ROUTE_PRIVACY_POLICY = "privacy_policy"
const val ROUTE_TERMS_OF_SERVICE = "terms_of_service"

/** Destinos da bottom navigation: Comparar · Lista · Sincronizar · Alertas. */
sealed class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    data object Compare : Destination("compare", R.string.nav_compare, Icons.Filled.Search)
    data object ShoppingList : Destination("list", R.string.nav_list, Icons.Filled.ShoppingCart)
    data object Sync : Destination("sync", R.string.nav_sync, Icons.Filled.Sync)
    data object Alerts : Destination("alerts", R.string.nav_alerts, Icons.Filled.Notifications)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Filled.Settings)
    data object Admin : Destination("admin", R.string.nav_admin, Icons.Filled.AdminPanelSettings)

    companion object {
        // Separadores base (todos os utilizadores). O "Admin" é acrescentado à
        // parte para quem tiver role ADMIN — ver FolhetoSmartRoot (Fix 2).
        val all = listOf(Compare, ShoppingList, Sync, Alerts, Settings)
    }
}

@Composable
fun FolhetoSmartRoot(isAdmin: Boolean = false, onLogout: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // ADMIN (Fix 2): separador extra "Admin"; restantes veem os separadores base.
    val destinations = remember(isAdmin) {
        if (isAdmin) Destination.all + Destination.Admin else Destination.all
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Compare.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Compare.route) { CompareScreen() }
            composable(Destination.ShoppingList.route) { ListScreen() }
            composable(Destination.Sync.route) {
                SyncScreen(onSyncSuccess = {
                    // Após sincronizar, leva o utilizador às promoções no Comparar.
                    navController.navigate(Destination.Compare.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(Destination.Alerts.route) { AlertsScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenPrivacyPolicy = { navController.navigate(ROUTE_PRIVACY_POLICY) },
                    onOpenTerms = { navController.navigate(ROUTE_TERMS_OF_SERVICE) },
                    onLogout = onLogout
                )
            }

            // Painel de administração — só registado para quem é ADMIN (Fix 2/3).
            if (isAdmin) {
                composable(Destination.Admin.route) { AdminScreen() }
            }

            // Documentos legais (ecrãs internos, texto local)
            composable(ROUTE_PRIVACY_POLICY) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(ROUTE_TERMS_OF_SERVICE) {
                TermsOfServiceScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
