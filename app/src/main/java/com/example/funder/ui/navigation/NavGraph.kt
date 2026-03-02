package com.example.funder.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.funder.ui.detail.FundDetailScreen
import com.example.funder.ui.home.HomeScreen
import com.example.funder.ui.home.MarketIndexBar
import com.example.funder.ui.home.MarketIndexViewModel
import com.example.funder.ui.import_fund.ImportScreen
import com.example.funder.ui.news.NewsScreen
import com.example.funder.ui.news.NewsDetailScreen
import com.example.funder.ui.search.SearchScreen
import com.example.funder.ui.settings.SettingsScreen
import com.example.funder.ui.stock.StockDetailScreen

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "持仓", Icons.Filled.Home, Icons.Outlined.Home)
    data object Import : Screen("import", "导入", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt)
    data object News : Screen("news", "资讯", Icons.Filled.Newspaper, Icons.Outlined.Newspaper)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
    data object Search : Screen("search", "搜索", Icons.Filled.Search, Icons.Outlined.Search)
}

private val bottomNavItems = listOf(Screen.Home, Screen.Import, Screen.News, Screen.Settings)
private val bottomNavRoutes = bottomNavItems.map { it.route }.toSet()

private val detailEnterTransition: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
    scaleIn(
        initialScale = 0.85f,
        animationSpec = tween(durationMillis = 300)
    ) + fadeIn(animationSpec = tween(durationMillis = 300))
}

private val detailExitTransition: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
    scaleOut(
        targetScale = 0.85f,
        animationSpec = tween(durationMillis = 300)
    ) + fadeOut(animationSpec = tween(durationMillis = 300))
}

@Composable
fun FunderNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route ?: ""
    var showSearch by remember { mutableStateOf(false) }

    val showBottomBar = currentRoute in bottomNavRoutes && !showSearch

    // 独立 ViewModel，NavGraph 级别作用域，所有 Tab 共享
    val marketIndexViewModel: MarketIndexViewModel = hiltViewModel()
    val marketIndices by marketIndexViewModel.indices.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // 大盘指数条：位于导航栏正上方
                    MarketIndexBar(indices = marketIndices)

                    NavigationBar(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(65.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        bottomNavItems.forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true

                            // 图标缩放动画
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.1f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "iconScale"
                            )

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.label,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            }
                                    )
                                },
                                label = { 
                                    Text(
                                        screen.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        letterSpacing = 0.sp
                                    )
                                },
                                selected = selected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(250))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(250))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200))
            }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToSearch = { showSearch = true },
                    onNavigateToImport = {
                        navController.navigate(Screen.Import.route)
                    },
                    onNavigateToDetail = { fundCode ->
                        navController.navigate("detail/$fundCode")
                    },
                    onRefreshMarketIndices = { marketIndexViewModel.refresh() }
                )
            }
            composable(
                route = Screen.Import.route,
                enterTransition = detailEnterTransition,
                exitTransition = detailExitTransition,
                popEnterTransition = detailEnterTransition,
                popExitTransition = detailExitTransition
            ) {
                ImportScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.News.route) {
                NewsScreen(
                    onNavigateToDetail = { url ->
                        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                        navController.navigate("newsDetail/$encodedUrl")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = "detail/{fundCode}",
                arguments = listOf(navArgument("fundCode") { type = NavType.StringType }),
                enterTransition = detailEnterTransition,
                exitTransition = detailExitTransition,
                popEnterTransition = detailEnterTransition,
                popExitTransition = detailExitTransition
            ) {
                FundDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStock = { stockCode, stockName ->
                        val encodedName = java.net.URLEncoder.encode(stockName, "UTF-8")
                        navController.navigate("stockDetail/$stockCode/$encodedName")
                    }
                )
            }
            composable(
                route = "newsDetail/{url}",
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
                enterTransition = detailEnterTransition,
                exitTransition = detailExitTransition,
                popEnterTransition = detailEnterTransition,
                popExitTransition = detailExitTransition
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                val url = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                NewsDetailScreen(
                    url = url,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "stockDetail/{stockCode}/{stockName}",
                arguments = listOf(
                    navArgument("stockCode") { type = NavType.StringType },
                    navArgument("stockName") { type = NavType.StringType }
                ),
                enterTransition = detailEnterTransition,
                exitTransition = detailExitTransition,
                popEnterTransition = detailEnterTransition,
                popExitTransition = detailExitTransition
            ) { backStackEntry ->
                val stockCode = backStackEntry.arguments?.getString("stockCode") ?: ""
                val encodedName = backStackEntry.arguments?.getString("stockName") ?: ""
                val stockName = java.net.URLDecoder.decode(encodedName, "UTF-8")
                StockDetailScreen(
                    stockCode = stockCode,
                    stockName = stockName,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        if (showSearch) {
            SearchScreen(onBack = { showSearch = false })
        }
        }
    }
}
