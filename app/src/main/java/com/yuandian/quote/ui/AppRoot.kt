package com.yuandian.quote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuandian.quote.ui.screens.CustomerEditScreen
import com.yuandian.quote.ui.screens.CustomerListScreen
import com.yuandian.quote.ui.screens.HomeScreen
import com.yuandian.quote.ui.screens.ProductEditScreen
import com.yuandian.quote.ui.screens.ProductListScreen
import com.yuandian.quote.ui.screens.QuoteDetailScreen
import com.yuandian.quote.ui.screens.QuoteEditScreen
import com.yuandian.quote.ui.screens.QuoteListScreen
import com.yuandian.quote.ui.screens.SettingsScreen
import com.yuandian.quote.ui.theme.Divider
import com.yuandian.quote.ui.theme.GreenSoft
import com.yuandian.quote.ui.theme.TextSecondary

object Routes {
    const val HOME = "home"
    const val QUOTES = "quotes"
    const val PRODUCTS = "products"
    const val CUSTOMERS = "customers"
    const val SETTINGS = "settings"
    const val CUSTOMER_EDIT = "customer/edit?id={id}"
    const val PRODUCT_EDIT = "product/edit?id={id}"
    const val QUOTE_EDIT = "quote/edit?id={id}&copyFrom={copyFrom}"
    const val QUOTE_DETAIL = "quote/detail/{id}"

    fun customerEdit(id: Long = 0) = "customer/edit?id=$id"
    fun productEdit(id: Long = 0) = "product/edit?id=$id"
    fun quoteEdit(id: Long = 0, copyFrom: Long = 0) = "quote/edit?id=$id&copyFrom=$copyFrom"
    fun quoteDetail(id: Long) = "quote/detail/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "工作台", Icons.Outlined.Dashboard),
    Tab(Routes.QUOTES, "报价单", Icons.Outlined.Description),
    Tab(Routes.PRODUCTS, "商品", Icons.Outlined.Inventory2),
    Tab(Routes.CUSTOMERS, "客户", Icons.Outlined.Groups),
)

fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(color = Divider)
                    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { nav.switchTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = GreenSoft,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(bottom = if (showBottomBar) inner.calculateBottomPadding() else 0.dp),
        ) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.QUOTES) { QuoteListScreen(nav) }
            composable(Routes.PRODUCTS) { ProductListScreen(nav) }
            composable(Routes.CUSTOMERS) { CustomerListScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(
                Routes.CUSTOMER_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { CustomerEditScreen(nav, it.arguments?.getLong("id") ?: 0L) }
            composable(
                Routes.PRODUCT_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { ProductEditScreen(nav, it.arguments?.getLong("id") ?: 0L) }
            composable(
                Routes.QUOTE_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("copyFrom") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) {
                QuoteEditScreen(nav, it.arguments?.getLong("id") ?: 0L, it.arguments?.getLong("copyFrom") ?: 0L)
            }
            composable(
                Routes.QUOTE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { QuoteDetailScreen(nav, it.arguments?.getLong("id") ?: 0L) }
        }
    }
}

