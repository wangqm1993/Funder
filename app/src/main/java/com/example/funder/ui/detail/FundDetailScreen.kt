package com.example.funder.ui.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.funder.data.remote.StockHolding
import com.example.funder.ui.components.FundLineChart
import com.example.funder.ui.components.LineData
import com.example.funder.ui.components.PeriodSelector
import com.example.funder.ui.components.filterByPeriod
import com.example.funder.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FundDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStock: (String, String) -> Unit = { _, _ -> },
    viewModel: FundDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val tabs = listOf("净值估算", "持仓明细", "历史净值", "累计收益", "基金概况")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.fundName.ifEmpty { state.fundCode },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            letterSpacing = 0.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            state.fundCode,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 标签行
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 12.dp,
                divider = { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) },
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                .padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            height = 2.5.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                title,
                                fontSize = 14.sp,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal,
                                letterSpacing = 0.sp
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 页面内容
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ValuationTab(state)
                    1 -> HoldingsTab(state, onNavigateToStock)
                    2 -> NavHistoryTab(state)
                    3 -> CumulativeReturnTab(state)
                    4 -> OverviewTab(state)
                }
            }
        }
    }
}

// ==================== Tab 1: 净值估算 ====================

@Composable
private fun ValuationTab(state: DetailUiState) {
    val detail = state.detail

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // 估值摘要卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                // 带图标的涨跌指示器
                val growth = state.estimatedGrowth
                val growthVal = growth.replace("%", "").toDoubleOrNull() ?: 0.0
                val isPositive = growthVal > 0
                val isDark = LocalIsDarkTheme.current
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "估算净值",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        AnimatedContent(
                            targetState = state.estimatedNav,
                            transitionSpec = {
                                fadeIn() + slideInVertically() togetherWith
                                        fadeOut() + slideOutVertically()
                            },
                            label = "navAnimation"
                        ) { nav ->
                            Text(
                                "¥$nav",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = -0.5.sp
                            )
                        }
                    }
                    
                    // 涨跌徽章
                    Surface(
                        shape = cardShape,
                        color = if (isPositive)
                            (if (isDark) ProfitRedDark else ProfitRedLight)
                        else
                            (if (isDark) LossGreenDark else LossGreenLight),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = if (isPositive) "上涨" else "下跌",
                                tint = if (isPositive) ProfitRed else LossGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "${if (isPositive) "+" else ""}$growth",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPositive) ProfitRed else LossGreen,
                                letterSpacing = 0.sp
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
                Spacer(Modifier.height(16.dp))
                
                // 最新净值信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "昨日净值",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "¥${state.lastNav}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "更新时间",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.estimateTime,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        
        // 图表卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "净值走势",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.height(16.dp))
                
                val chartData = detail?.chartData
                if (chartData != null && chartData.netWorthTrend.isNotEmpty()) {
                    val recentPoints = chartData.netWorthTrend.takeLast(30)
                    FundLineChart(
                        lines = listOf(
                            LineData("净值", recentPoints, MaterialTheme.colorScheme.primary)
                        ),
                        heightDp = 220
                    )
                } else if (state.isLoadingDetail) {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无数据", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // 如果用户有持仓，显示持仓摘要
        state.holding?.let { h ->
            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "我的持仓",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    // 估算市值 - 核心数据突出
                    val marketValue = state.holdingValue?.let { "¥%.2f".format(it) } ?: "--"
                    Text(
                        "估算市值",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        marketValue,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                    Spacer(Modifier.height(16.dp))

                    // 今日盈亏 | 累计盈亏 - 并排展示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val todayVal = state.todayProfit
                        val todayStr = todayVal?.let { "%.2f".format(it) } ?: "--"
                        val todayColor = when {
                            todayVal != null && todayVal > 0 -> ProfitRed
                            todayVal != null && todayVal < 0 -> LossGreen
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val todayDisplay = if (todayVal != null) {
                            "${if (todayVal > 0) "+" else ""}$todayStr"
                        } else todayStr

                        val totalVal = state.totalProfit
                        val totalStr = totalVal?.let { "%.2f".format(it) } ?: "--"
                        val totalColor = when {
                            totalVal != null && totalVal > 0 -> ProfitRed
                            totalVal != null && totalVal < 0 -> LossGreen
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val totalDisplay = if (totalVal != null) {
                            "${if (totalVal > 0) "+" else ""}$totalStr"
                        } else totalStr

                        // 今日盈亏
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "今日盈亏",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                todayDisplay,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = todayColor,
                                letterSpacing = 0.sp
                            )
                        }
                        // 累计盈亏
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "累计盈亏",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                totalDisplay,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = totalColor,
                                letterSpacing = 0.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                    Spacer(Modifier.height(16.dp))

                    // 持有份额 | 持仓成本 - 辅助信息
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "持有份额",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "%.2f".format(h.shares),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "持仓成本",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "¥%.2f".format(h.totalCost),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 0.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ==================== Tab 2: 持仓明细 ====================

@Composable
private fun HoldingsTab(
    state: DetailUiState,
    onNavigateToStock: (String, String) -> Unit
) {
    val detail = state.detail
    val holdings = state.stockHoldings.ifEmpty { detail?.topHoldings ?: emptyList() }
    val holdingsDate = detail?.holdingsDate ?: ""

    if (state.isLoadingDetail) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 日期标题卡片
        if (holdingsDate.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    "持仓日期：$holdingsDate",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // 持仓表格卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                // 表格标题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text("股票", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(2.5f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.sp)
                    Text("涨跌", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1.5f), textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.sp)
                    Text("占比", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1.2f), textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.sp)
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // 行数据
                if (holdings.isNotEmpty()) {
                    holdings.forEachIndexed { index, stock ->
                        StockRow(
                            stock = stock,
                            index = index,
                            onClick = {
                                onNavigateToStock(stock.code, stock.name)
                            }
                        )
                        if (index < holdings.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        }
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无持仓明细数据",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StockRow(
    stock: StockHolding,
    index: Int,
    onClick: () -> Unit
) {
    val changeVal = stock.change.toDoubleOrNull()
    val changeColor = when {
        changeVal != null && changeVal > 0 -> ProfitRed
        changeVal != null && changeVal < 0 -> LossGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排名徽章 + 股票名称（代码）
        Row(
            modifier = Modifier.weight(2.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名数字
            Surface(
                shape = CircleShape,
                color = when (index) {
                    0 -> MedalGold.copy(alpha = 0.2f)
                    1 -> MedalSilver.copy(alpha = 0.2f)
                    2 -> MedalBronze.copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${index + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (index) {
                            0 -> MedalGoldText
                            1 -> MedalSilverText
                            2 -> MedalBronzeText
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            Spacer(Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stock.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stock.code,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.sp
                    )
                    if (stock.price.isNotEmpty()) {
                        Text(
                            " · ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stock.price,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // 涨跌幅，带更好的样式
        Column(
            modifier = Modifier.weight(1.5f),
            horizontalAlignment = Alignment.End
        ) {
            val changeStr = if (changeVal != null) {
                "${if (changeVal > 0) "+" else ""}${"%.2f".format(changeVal)}%"
            } else "--"
            Text(
                changeStr,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = changeColor,
                letterSpacing = 0.2.sp
            )
        }

        Spacer(Modifier.width(8.dp))

        // 占比，带徽章
        Column(
            modifier = Modifier.weight(1.2f),
            horizontalAlignment = Alignment.End
        ) {
            Surface(
                shape = cardShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    "${stock.ratio}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ==================== Tab 3: 历史净值 ====================

@Composable
private fun NavHistoryTab(state: DetailUiState) {
    val detail = state.detail
    val chartData = detail?.chartData
    var selectedPeriod by remember { mutableStateOf("月") }

    if (state.isLoadingDetail) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "净值走势",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.height(16.dp))

                if (chartData != null && chartData.netWorthTrend.isNotEmpty()) {
                    val navFiltered = filterByPeriod(chartData.netWorthTrend, selectedPeriod)
                    val acFiltered = filterByPeriod(chartData.totalWorthTrend, selectedPeriod)

                    val lines = mutableListOf<LineData>()
                    if (navFiltered.isNotEmpty()) {
                        lines.add(LineData("单位净值", navFiltered, MaterialTheme.colorScheme.primary))
                    }
                    if (acFiltered.isNotEmpty()) {
                        lines.add(LineData("累计净值", acFiltered, ProfitRed))
                    }

                    FundLineChart(lines = lines, heightDp = 260)

                    Spacer(Modifier.height(16.dp))

                    PeriodSelector(
                        selected = selectedPeriod,
                        onSelect = { selectedPeriod = it }
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无历史净值数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        val history = state.navHistory
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text("日期", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1.2f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.sp)
                        Text("单位净值", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.sp)
                        Text("累计净值", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.sp)
                        Text("日增长率", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.sp)
                    }

                    history.forEachIndexed { index, item ->
                        val growthVal = item.growthRate.replace("%", "").toDoubleOrNull()
                        val growthColor = when {
                            growthVal != null && growthVal > 0 -> ProfitRed
                            growthVal != null && growthVal < 0 -> LossGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val rowBg = if (index % 2 != 0)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                        else Color.Transparent

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                        ) {
                            Text(item.date, fontSize = 12.sp, modifier = Modifier.weight(1.2f),
                                color = MaterialTheme.colorScheme.onSurface, letterSpacing = 0.sp)
                            Text("%.4f".format(item.nav), fontSize = 12.sp,
                                modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text("%.4f".format(item.totalNav), fontSize = 12.sp,
                                modifier = Modifier.weight(1f), textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                            Text(
                                item.growthRate.let { if (it.endsWith("%")) it else "${it}%" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = growthColor,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                        if (index < history.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ==================== Tab 4: 累计收益 ====================

@Composable
private fun CumulativeReturnTab(state: DetailUiState) {
    val detail = state.detail
    val chartData = detail?.chartData
    var selectedPeriod by remember { mutableStateOf("月") }

    if (state.isLoadingDetail) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "累计收益走势",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.height(16.dp))

                if (chartData != null && chartData.grandTotal.isNotEmpty()) {
                    val colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        ProfitRed,
                        MaterialTheme.colorScheme.tertiary
                    )

                    val lines = chartData.grandTotal.mapIndexed { idx, series ->
                        val filtered = filterByPeriod(series.points, selectedPeriod)
                        LineData(
                            label = series.name,
                            points = filtered,
                            color = colors.getOrElse(idx) { Color.Gray }
                        )
                    }.filter { it.points.isNotEmpty() }

                    FundLineChart(
                        lines = lines,
                        yAxisSuffix = "%",
                        showPercentAxis = true,
                        heightDp = 260
                    )

                    Spacer(Modifier.height(16.dp))

                    PeriodSelector(
                        selected = selectedPeriod,
                        onSelect = { selectedPeriod = it }
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无累计收益数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (detail != null) {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HighlightReturnCard(
                    label = "近1年收益",
                    growth = detail.growth1y,
                    modifier = Modifier.weight(1f)
                )
                HighlightReturnCard(
                    label = "近3年收益",
                    growth = detail.growth3y,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "阶段收益",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PerformanceItem("近1月", detail.growth1m, Modifier.weight(1f))
                        PerformanceItem("近3月", detail.growth3m, Modifier.weight(1f))
                        PerformanceItem("近6月", detail.growth6m, Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HighlightReturnCard(
    label: String,
    growth: String,
    modifier: Modifier = Modifier
) {
    val value = growth.toDoubleOrNull()
    val color = when {
        value != null && value > 0 -> ProfitRed
        value != null && value < 0 -> LossGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bgColor = when {
        value != null && value > 0 -> MaterialTheme.colorScheme.errorContainer
        value != null && value < 0 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = modifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp)
        ) {
            Text(
                label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (value != null && value > 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                    contentDescription = if (value != null && value > 0) "上涨" else "下跌",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (growth.isEmpty()) "--" else "${if (value != null && value > 0) "+" else ""}$growth%",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun PerformanceItem(label: String, growth: String, modifier: Modifier = Modifier) {
    val value = growth.toDoubleOrNull()
    val isPositive = value != null && value > 0
    val isDark = LocalIsDarkTheme.current
    val color = when {
        value != null && value > 0 -> ProfitRed
        value != null && value < 0 -> LossGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                value != null && value > 0 -> if (isDark) ProfitRedDark else ProfitRedLight
                value != null && value < 0 -> if (isDark) LossGreenDark else LossGreenLight
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Icon(
                        imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = if (isPositive) "上涨" else "下跌",
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    if (growth.isNotEmpty()) "${if (isPositive) "+" else ""}${growth}%" else "--",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

// ==================== Tab 5: 基金概况 ====================

@Composable
private fun OverviewTab(state: DetailUiState) {
    val detail = state.detail

    if (state.isLoadingDetail) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (detail == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "业绩排名",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RankItem("近1月", detail.growth1m, detail.rank1m)
                    RankItem("近3月", detail.growth3m, detail.rank3m)
                    RankItem("近6月", detail.growth6m, detail.rank6m)
                    RankItem("近1年", detail.growth1y, detail.rank1y)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "基金信息",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.height(16.dp))

                val infoRows = listOf(
                    "单位净值" to "${detail.latestNav}（${detail.latestNavDate}）",
                    "累计净值" to detail.totalNav,
                    "基金类型" to detail.type,
                    "基金公司" to detail.managementCompany,
                    "基金经理" to detail.fundManager,
                    "交易状态" to detail.tradeStatus.ifEmpty { "--" },
                    "基金规模" to detail.fundScale.ifEmpty { "--" }
                )

                infoRows.forEachIndexed { index, (label, value) ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.sp
                        )
                        Spacer(Modifier.width(24.dp))
                        Text(
                            value.ifEmpty { "--" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RankItem(label: String, growth: String, rank: String) {
    val value = growth.toDoubleOrNull()
    val isDark = LocalIsDarkTheme.current
    val color = when {
        value != null && value > 0 -> ProfitRed
        value != null && value < 0 -> LossGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bgColor = when {
        value != null && value > 0 -> if (isDark) ProfitRedDark else ProfitRedLight
        value != null && value < 0 -> if (isDark) LossGreenDark else LossGreenLight
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(75.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = cardShape,
            color = bgColor
        ) {
            Text(
                if (growth.isNotEmpty()) "${if (value != null && value > 0) "+" else ""}${growth}%" else "--",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        if (rank.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                rank,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp
            )
        }
    }
}
