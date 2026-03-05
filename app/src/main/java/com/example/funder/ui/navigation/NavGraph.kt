package com.example.funder.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.example.funder.ui.market.MarketScreen
import com.example.funder.ui.news.NewsScreen
import com.example.funder.ui.news.NewsDetailScreen
import com.example.funder.ui.search.SearchScreen
import com.example.funder.ui.settings.SettingsScreen
import com.example.funder.ui.stock.StockDetailScreen
import com.example.funder.ui.watchlist.WatchlistScreen

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home      : Screen("home",      "持仓", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet)
    data object Watchlist : Screen("watchlist", "自选", Icons.Filled.Star,      Icons.Outlined.StarOutline)
    data object Market    : Screen("market",    "行情", Icons.AutoMirrored.Filled.ShowChart,  Icons.AutoMirrored.Outlined.ShowChart)
    data object News      : Screen("news",      "资讯", Icons.Filled.Newspaper,  Icons.Outlined.Newspaper)
    data object Settings  : Screen("settings",  "设置", Icons.Filled.Settings,   Icons.Outlined.Settings)
    // 非底栏路由
    data object Import : Screen("import", "导入", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt)
    data object Search : Screen("search", "搜索", Icons.Filled.Search,    Icons.Outlined.Search)
}

private val bottomNavItems = listOf(
    Screen.Home, Screen.Watchlist, Screen.Market, Screen.News, Screen.Settings
)
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
                Surface(
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        MarketIndexBar(indices = marketIndices)
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                        PillNavigationBar(
                            items = bottomNavItems,
                            currentDestination = currentDestination,
                            onNavigate = { screen ->
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
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
            composable(Screen.Watchlist.route) {
                WatchlistScreen(
                    onNavigateToDetail = { navController.navigate("detail/$it") }
                )
            }
            composable(Screen.Market.route) {
                MarketScreen()
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

// ── 导航栏：图标上方固定 Pill + 标签常驻，5 tab 均适用 ──
@Composable
private fun PillNavigationBar(
    items: List<Screen>,
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (Screen) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(62.dp)
            .background(MaterialTheme.colorScheme.surface),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            PillNavItem(
                screen = screen,
                selected = selected,
                onClick = { onNavigate(screen) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PillNavItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary     = MaterialTheme.colorScheme.primary
    val unselected  = MaterialTheme.colorScheme.onSurfaceVariant

    // Pill 缩放弹出动画（spring，带回弹感）
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness    = 450f
        ),
        label = "pillScale"
    )

    // 图标颜色平滑过渡
    val iconColor by animateColorAsState(
        targetValue = if (selected) primary else unselected,
        animationSpec = tween(220),
        label = "iconColor"
    )

    // 图标 bounce：选中时轻弹
    val iconTranslateY by animateFloatAsState(
        targetValue = if (selected) -2f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "iconY"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness    = 500f
        ),
        label = "iconScale"
    )

    // 标签字重动画
    val labelAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = tween(200),
        label = "labelAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pill 指示器 + 图标叠加
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(width = 54.dp, height = 28.dp)
            ) {
                // 圆角 Pill 背景（独立于图标，scale in/out）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = pillScale
                            scaleY = pillScale
                            alpha  = pillScale
                        }
                        .clip(RoundedCornerShape(50.dp))
                        .background(primary.copy(alpha = 0.14f))
                )
                // 图标（bounce 动画）
                Icon(
                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                    contentDescription = screen.label,
                    tint = iconColor,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX      = iconScale
                            scaleY      = iconScale
                            translationY = iconTranslateY
                        }
                )
            }

            Spacer(Modifier.height(3.dp))

            // 标签文字（始终显示，透明度区分选中态）
            Text(
                text  = screen.label,
                fontSize   = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = iconColor.copy(alpha = labelAlpha),
                maxLines = 1,
                letterSpacing = 0.sp
            )
        }
    }
}
