package com.example.funder.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.funder.data.remote.MarketIndexDto
import com.example.funder.data.remote.SectorDto
import com.example.funder.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun MarketScreen(viewModel: MarketViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    var showAllGainers by remember { mutableStateOf(false) }
    var showAllLosers by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "行情",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // ── 三大指数卡片 ──
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.indices.take(3).forEach { index ->
                                IndexCard(index = index, isDark = isDark, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // ── 板块总览 涨幅榜 ──
                    item {
                        SectionHeader(
                            title = "板块总览",
                            subtitle = "涨幅榜",
                            showAll = showAllGainers,
                            updateTime = state.lastUpdateTime,
                            onToggle = { showAllGainers = !showAllGainers }
                        )
                    }

                    val displayedGainers = if (showAllGainers) state.gainers else state.gainers.take(5)
                    items(displayedGainers) { sector ->
                        SectorRow(sector = sector, isDark = isDark)
                    }

                    if (state.gainers.isEmpty() && !state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // ── 板块总览 跌幅榜 ──
                    item {
                        SectionHeader(
                            title = "板块总览",
                            subtitle = "跌幅榜",
                            showAll = showAllLosers,
                            updateTime = "",
                            onToggle = { showAllLosers = !showAllLosers }
                        )
                    }

                    val displayedLosers = if (showAllLosers) state.losers else state.losers.take(5)
                    items(displayedLosers) { sector ->
                        SectorRow(sector = sector, isDark = isDark)
                    }

                    if (state.losers.isEmpty() && !state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

// ── 指数卡片（彩色背景，仿截图） ──
@Composable
private fun IndexCard(
    index: MarketIndexDto,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val isUp = index.changePercent >= 0
    val bgColor: Color
    val textColor: Color
    if (isDark) {
        bgColor  = if (isUp) Color(0xFF3B1818) else Color(0xFF0E2B1A)
        textColor = if (isUp) ProfitRed else LossGreen
    } else {
        bgColor  = if (isUp) Color(0xFFFFF3F3) else Color(0xFFF0FFF5)
        textColor = if (isUp) ProfitRed else LossGreenOnLight
    }
    val sign = if (isUp) "+" else ""

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(
                text = index.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "%.2f".format(index.price),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "$sign${"%.2f".format(index.change)}",
                fontSize = 12.sp,
                color = textColor,
                maxLines = 1
            )
            Text(
                text = "$sign${"%.2f".format(index.changePercent)}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

// ── 板块分区标题 ──
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    showAll: Boolean,
    updateTime: String,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (subtitle.contains("涨")) ProfitRed.copy(alpha = 0.1f)
                                else LossGreenOnLight.copy(alpha = 0.1f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            subtitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (subtitle.contains("涨")) ProfitRed else LossGreenOnLight
                        )
                    }
                }
                if (updateTime.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "更新：$updateTime",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = if (showAll) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        // 列标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "板块名称",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                "涨跌幅",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(70.dp)
            )
        }
    }
}

// ── 板块行（仿截图：板块名、基金数量、涨跌幅）──
@Composable
private fun SectorRow(
    sector: SectorDto,
    isDark: Boolean
) {
    val isUp = sector.isUp
    val color = if (isUp) ProfitRed else if (isDark) LossGreen else LossGreenOnLight
    val bgColor = color.copy(alpha = if (isDark) 0.18f else 0.09f)
    val sign = if (isUp) "+" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：板块名 + 基金数量
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sector.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (sector.fundCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${sector.fundCount}只基金",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 右：涨跌幅色块
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$sign${"%.2f".format(sector.changePercent)}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
}
