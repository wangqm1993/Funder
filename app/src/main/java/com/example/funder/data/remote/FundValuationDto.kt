package com.example.funder.data.remote

import com.google.gson.annotations.SerializedName

/** 实时基金估值（fundgz API）。 */
data class FundValuationDto(
    @SerializedName("fundcode") val fundCode: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("jzrq") val navDate: String = "",
    @SerializedName("dwjz") val nav: String = "",
    @SerializedName("gsz") val estimatedNav: String = "",
    @SerializedName("gszzl") val estimatedGrowth: String = "",
    @SerializedName("gztime") val estimateTime: String = ""
)

/** 行业板块行情快照。 */
data class SectorDto(
    val code: String,
    val name: String,
    val changePercent: Double,
    val change: Double,
    val fundCount: Int = 0        // 该板块关联基金数量
) {
    val isUp: Boolean get() = changePercent >= 0
}

/** 基金搜索结果。 */
data class FundSearchResultDto(
    val code: String,
    val name: String,
    val type: String
)

// ==================== 图表 / 详情数据 ====================

/** 图表上的单个点（时间戳毫秒，值）。 */
data class ChartPoint(val timestamp: Long, val value: Double)

/** 图表的命名数据系列（例如 "涨幅"、"沪深300"）。 */
data class ChartSeries(
    val name: String,
    val points: List<ChartPoint>
)

/** 从 pingzhongdata JS 解析的所有图表数据。 */
data class FundChartData(
    val netWorthTrend: List<ChartPoint> = emptyList(),
    val totalWorthTrend: List<ChartPoint> = emptyList(),
    val grandTotal: List<ChartSeries> = emptyList()
)

/** 历史净值记录（来自 F10DataApi）。 */
data class NavHistoryItem(
    val date: String,
    val nav: Double,
    val totalNav: Double,
    val growthRate: String
)

/** 基金前 10 大持仓股票。 */
data class StockHolding(
    val name: String,
    val code: String,
    val ratio: String,
    val price: String = "",
    val change: String = "",
    val isNew: Boolean = false
)

/** 从 pingzhongdata JS 解析的基金概览 / 详情信息。 */
data class FundDetailDto(
    val code: String = "",
    val name: String = "",
    val type: String = "",
    val establishDate: String = "",
    val fundScale: String = "",
    val fundManager: String = "",
    val managementCompany: String = "",
    val tradeStatus: String = "",
    // 净值
    val latestNav: String = "",
    val latestNavDate: String = "",
    val totalNav: String = "",
    // 业绩排名
    val rank1m: String = "",
    val rank3m: String = "",
    val rank6m: String = "",
    val rank1y: String = "",
    val growth1m: String = "",
    val growth3m: String = "",
    val growth6m: String = "",
    val growth1y: String = "",
    val growth3y: String = "",
    // 持仓
    val holdingsDate: String = "",
    val topHoldings: List<StockHolding> = emptyList(),
    // 图表数据
    val chartData: FundChartData = FundChartData()
)
