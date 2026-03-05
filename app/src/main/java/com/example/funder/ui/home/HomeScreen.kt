package com.example.funder.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.funder.ui.theme.cardShape
import com.example.funder.ui.components.dragContainer
import com.example.funder.ui.components.rememberDragDropState
import com.example.funder.ui.components.FundCard
import com.example.funder.ui.components.SummaryCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onRefreshMarketIndices: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editingHolding by remember { mutableStateOf<FundHoldingWithValuation?>(null) }
    var deletingHolding by remember { mutableStateOf<FundHoldingWithValuation?>(null) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = {
            viewModel.refresh()
            onRefreshMarketIndices()
        }
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "持仓",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pullRefresh(pullRefreshState)
        ) {
        if (uiState.isLoading && uiState.holdings.isEmpty()) {
                // 骨架屏加载动画
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SummaryCardSkeleton()
                    }
                    items(6) {
                        FundCardSkeleton()
                    }
                }
            } else if (uiState.holdings.isEmpty()) {
                // 空状态
                EmptyPortfolioContent(
                    onAddClick = onNavigateToSearch,
                    onImportClick = onNavigateToImport
                )
            } else {
                val listState = rememberLazyListState()
                val headerCount = 2

                val holdingsList = remember {
                    mutableStateListOf<FundHoldingWithValuation>().apply {
                        addAll(uiState.holdings)
                    }
                }

                val dragDropState = rememberDragDropState(
                    lazyListState = listState,
                    startIndex = headerCount,
                    itemCount = holdingsList.size
                ) { from, to ->
                    java.util.Collections.swap(holdingsList, from, to)
                }

                LaunchedEffect(uiState.holdings) {
                    if (dragDropState.currentIndexOfDraggedItem == null) {
                        val newMap = uiState.holdings.associateBy { it.holding.fundCode }
                        val currentCodes = holdingsList.map { it.holding.fundCode }.toSet()

                        holdingsList.removeAll { it.holding.fundCode !in newMap }

                        for (i in holdingsList.indices) {
                            val updated = newMap[holdingsList[i].holding.fundCode]
                            if (updated != null && updated != holdingsList[i]) {
                                holdingsList[i] = updated
                            }
                        }

                        for (item in uiState.holdings) {
                            if (item.holding.fundCode !in currentCodes) {
                                holdingsList.add(item)
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .dragContainer(dragDropState) {
                            viewModel.updateHoldingsOrder(holdingsList.toList())
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SummaryCard(
                            totalAssets = uiState.totalAssets,
                            todayProfit = uiState.todayTotalProfit,
                            totalProfit = uiState.totalProfit,
                            totalProfitRate = uiState.totalProfitRate,
                            lastUpdateTime = uiState.lastUpdateTime,
                            isAllSettled = uiState.isAllSettled,
                            hasAnySettled = uiState.hasAnySettled,
                            settledCount = uiState.settledCount,
                            totalCount = uiState.holdings.size
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "我的持仓 (${uiState.holdings.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    letterSpacing = 0.3.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "左滑删 · 右滑编辑 · 长按排序",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    val anyDragActive = dragDropState.currentIndexOfDraggedItem != null ||
                        dragDropState.settlingItemIndex != null

                    itemsIndexed(
                        items = holdingsList,
                        key = { _, item -> item.holding.fundCode }
                    ) { index, item ->
                        val lazyListIndex = index + headerCount
                        val isDragging = lazyListIndex == dragDropState.currentIndexOfDraggedItem
                        val isSettling = lazyListIndex == dragDropState.settlingItemIndex
                        val displacement = dragDropState.elementDisplacement.takeIf { isDragging }
                        val currentCardShape = cardShape

                        Box(
                            modifier = Modifier
                                .then(
                                    when {
                                        // 拖动或结算动画进行中，所有 item 均禁用 animateItem，避免重复动画
                                        anyDragActive -> Modifier
                                        else -> Modifier.animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null
                                        )
                                    }
                                )
                                .zIndex(if (isDragging || isSettling) 1f else 0f)
                                .graphicsLayer {
                                    when {
                                        isDragging -> {
                                            translationY = displacement ?: 0f
                                            scaleX = 1.03f
                                            scaleY = 1.03f
                                            shadowElevation = 16f
                                            shape = currentCardShape
                                            clip = true
                                        }
                                        isSettling -> {
                                            translationY = dragDropState.settlingItemOffset.value
                                        }
                                    }
                                }
                        ) {
                            FundCard(
                                item = item,
                                onClick = { onNavigateToDetail(item.holding.fundCode) },
                                onDelete = { deletingHolding = item },
                                onEdit = { editingHolding = item }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = uiState.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    // 编辑持仓对话框
    editingHolding?.let { item ->
        EditHoldingDialog(
            holding = item.holding,
            onDismiss = { editingHolding = null },
            onConfirm = { shares, costPrice ->
                viewModel.updateHolding(item.holding.fundCode, shares, costPrice)
                editingHolding = null
            }
        )
    }

    // 删除确认对话框
    deletingHolding?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingHolding = null },
            title = {
                Text(
                    text = "确认删除",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "确定要删除「${item.holding.fundName.ifEmpty { item.holding.fundCode }}」吗？删除后持仓数据将无法恢复。",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteHolding(item.holding.fundCode)
                        deletingHolding = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deletingHolding = null }) {
                    Text("取消")
                }
            },
            shape = cardShape,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun EmptyPortfolioContent(
    onAddClick: () -> Unit,
    onImportClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "emptyAlpha"
    )
    val offsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "emptyOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = offsetY
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalanceWallet,
            contentDescription = "暂无持仓",
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "暂无持仓",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "开启你的基金投资之旅",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            ),
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("搜索添加")
            }
            OutlinedButton(
                onClick = onImportClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("截图导入")
            }
        }
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val shimmerColors = listOf(baseColor, highlightColor, baseColor)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim, translateAnim * 0.5f),
        end = Offset(translateAnim + 500f, translateAnim * 0.5f + 250f)
    )
}

@Composable
private fun SummaryCardSkeleton() {
    val brush = shimmerBrush()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 总资产骨架
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 收益信息骨架
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(2) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FundCardSkeleton() {
    val brush = shimmerBrush()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Top: Name + Rate badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(brush)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                // Bottom: Profit + Holding
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(brush)
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                    }
                }
            }
            // Bottom accent skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(brush)
            )
        }
    }
}
