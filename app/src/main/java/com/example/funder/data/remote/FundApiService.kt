package com.example.funder.data.remote

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FundApiService @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    companion object {
        private const val VALUATION_URL = "http://fundgz.1234567.com.cn/js/%s.js?rt=%d"
        private const val SEARCH_URL = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=%s"
        private const val DETAIL_URL = "http://fund.eastmoney.com/pingzhongdata/%s.js"
        private const val NAV_HISTORY_URL = "http://fund.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code=%s&page=%d&sdate=&edate=&per=%d"
        // 新浪财经滚动新闻 API（lid=2516 是财经频道）
        private const val NEWS_URL = "https://feed.mix.sina.com.cn/api/roll/get?pageid=153&lid=2516&num=%d&page=%d"
    }

    /**
     * 获取单个基金的实时估值。
     * API 返回 JSONP：jsonpgz({...});
     * 我们从 JSONP 包装器中提取 JSON 对象。
     */
    suspend fun getValuation(fundCode: String): FundValuationDto? = withContext(Dispatchers.IO) {
        try {
            val url = String.format(VALUATION_URL, fundCode, System.currentTimeMillis())
            val request = Request.Builder()
                .url(url)
                .header("Referer", "http://fund.eastmoney.com/")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            // 从 JSONP 中提取 JSON：jsonpgz({...});
            val jsonStr = extractJsonFromJsonp(body) ?: return@withContext null
            gson.fromJson(jsonStr, FundValuationDto::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取多个基金的估值。
     */
    suspend fun getValuations(fundCodes: List<String>): Map<String, FundValuationDto> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, FundValuationDto>()
            fundCodes.forEach { code ->
                getValuation(code)?.let { dto ->
                    result[code] = dto
                }
            }
            result
        }

    /**
     * 通过关键词（代码或名称）搜索基金。
     * 返回匹配的基金列表。
     */
    suspend fun searchFund(keyword: String): List<FundSearchResultDto> =
        withContext(Dispatchers.IO) {
            try {
                val url = String.format(SEARCH_URL, keyword)
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "https://fund.eastmoney.com/")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()

                parseSearchResult(body)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /**
     * 获取基金详情数据（持仓、业绩、概览）。
     * 来源：http://fund.eastmoney.com/pingzhongdata/{code}.js
     */
    suspend fun getFundDetail(fundCode: String): FundDetailDto? = withContext(Dispatchers.IO) {
        try {
            val url = String.format(DETAIL_URL, fundCode)
            val request = Request.Builder()
                .url(url)
                .header("Referer", "http://fund.eastmoney.com/")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            parseFundDetailJs(fundCode, body)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取历史净值数据。
     */
    suspend fun getNavHistory(fundCode: String, page: Int = 1, perPage: Int = 20): List<NavHistoryItem> =
        withContext(Dispatchers.IO) {
            try {
                val url = String.format(NAV_HISTORY_URL, fundCode, page, perPage)
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "http://fund.eastmoney.com/")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseNavHistory(body)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    // ==================== 详情 JS 解析 ====================

    private fun parseFundDetailJs(code: String, js: String): FundDetailDto {
        fun q(varName: String): String {
            val p = Regex("""var\s+$varName\s*=\s*"([^"]*)"""")
            return p.find(js)?.groupValues?.get(1) ?: ""
        }

        fun field(key: String): String {
            val p = Regex(""""$key"\s*:\s*"([^"]*)"""")
            return p.find(js)?.groupValues?.get(1) ?: ""
        }

        val name = q("fS_name")
        val fundCode = q("fS_code").ifEmpty { code }

        // 业绩和排名来自 swithSameType
        val growth1m = q("syl_1y")
        val growth3m = q("syl_3y")
        val growth6m = q("syl_6y")
        val growth1y = q("syl_1n")
        val growth3y = q("syl_3n")

        // 从 swithSameType 解析排名：[{...,"rank":"xxx/yyy",...,"sc":"1y",...}]
        val rank1m = parseRank(js, "1y")
        val rank3m = parseRank(js, "3y")
        val rank6m = parseRank(js, "6y")
        val rank1y = parseRank(js, "1n")

        // 基金信息 - 尝试多种模式
        val type = field("JJLX").ifEmpty { q("fS_jjlx") }.ifEmpty { "股票型" }
        val company = field("JJGSMC").ifEmpty { q("fS_jjgs") }
        val manager = field("JJJL").ifEmpty { q("fS_jjjl") }
        val estDate = field("CLRQ").ifEmpty { q("fS_clrq") }
        val scale = field("JJGM").ifEmpty { q("fS_jjgm") }
        val tradeStatus = field("JJJYZT").ifEmpty { "开放申购" }

        // 最新净值来自 Data_netWorthTrend（最后一个元素）
        val navTrend = parseNetWorthTrend(js)
        val acTrend = parseTotalWorthTrend(js)
        val latestNav = if (navTrend.isNotEmpty()) "%.4f".format(navTrend.last().value) else ""
        val totalNav = if (acTrend.isNotEmpty()) "%.4f".format(acTrend.last().value) else ""
        val latestNavDate = if (navTrend.isNotEmpty()) {
            val ts = navTrend.last().timestamp
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(ts))
        } else ""

        // 持仓
        val topHoldings = parseTopHoldings(js)
        val holdingsDate = parseHoldingsDate(js)

        // 累计总收益（累计收益对比）
        val grandTotal = parseGrandTotal(js)

        return FundDetailDto(
            code = fundCode,
            name = name,
            type = type,
            establishDate = estDate,
            fundScale = scale,
            fundManager = manager,
            managementCompany = company,
            tradeStatus = tradeStatus,
            latestNav = latestNav,
            latestNavDate = latestNavDate,
            totalNav = totalNav,
            rank1m = rank1m, rank3m = rank3m, rank6m = rank6m, rank1y = rank1y,
            growth1m = growth1m, growth3m = growth3m, growth6m = growth6m, growth1y = growth1y, growth3y = growth3y,
            holdingsDate = holdingsDate,
            topHoldings = topHoldings,
            chartData = FundChartData(
                netWorthTrend = navTrend,
                totalWorthTrend = acTrend,
                grandTotal = grandTotal
            )
        )
    }

    /** 解析 Data_netWorthTrend = [{x:ts,y:val,...},...]; */
    private fun parseNetWorthTrend(js: String): List<ChartPoint> {
        val p = Regex("""var\s+Data_netWorthTrend\s*=\s*(\[[\s\S]*?\])\s*;""")
        val arr = p.find(js)?.groupValues?.get(1) ?: return emptyList()
        val xp = Regex(""""x"\s*:\s*(\d+)""")
        val yp = Regex(""""y"\s*:\s*([\d.]+)""")
        val points = mutableListOf<ChartPoint>()
        // 按 }, 分割以获取每个对象
        val items = arr.split("},")
        for (item in items) {
            val x = xp.find(item)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val y = yp.find(item)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
            points.add(ChartPoint(x, y))
        }
        return points
    }

    /** 解析 Data_ACWorthTrend = [[ts,val],...]; */
    private fun parseTotalWorthTrend(js: String): List<ChartPoint> {
        val p = Regex("""var\s+Data_ACWorthTrend\s*=\s*(\[[\s\S]*?\])\s*;""")
        val arr = p.find(js)?.groupValues?.get(1) ?: return emptyList()
        val pp = Regex("""\[\s*(\d+)\s*,\s*([\d.]+)\s*\]""")
        return pp.findAll(arr).map { m ->
            ChartPoint(m.groupValues[1].toLong(), m.groupValues[2].toDouble())
        }.toList()
    }

    /** 解析 Data_grandTotal = [{"name":"...","data":[[ts,val],...]}, ...]; */
    private fun parseGrandTotal(js: String): List<ChartSeries> {
        try {
            // 首先尝试 JSON 解析
            val p = Regex("""Data_grandTotal\s*=\s*(\[[^\;]+\]);""")
            val match = p.find(js)
            if (match != null) {
                val jsonStr = match.groupValues[1]
                // 解析为 JSON
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                val dataList: List<Map<String, Any>> = gson.fromJson(jsonStr, type)
                
                return dataList.mapNotNull { map ->
                    val name = map["name"] as? String ?: return@mapNotNull null
                    val dataArray = map["data"] as? List<*> ?: return@mapNotNull null
                    val points = dataArray.mapNotNull { item ->
                        val pair = item as? List<*> ?: return@mapNotNull null
                        if (pair.size >= 2) {
                            val ts = (pair[0] as? Double)?.toLong() ?: return@mapNotNull null
                            val value = (pair[1] as? Double) ?: (pair[1] as? Long)?.toDouble() ?: return@mapNotNull null
                            ChartPoint(ts, value)
                        } else null
                    }
                    ChartSeries(name = name, points = points)
                }
            }
            
            // 回退到正则表达式解析
            val oldP = Regex("""var\s+Data_grandTotal\s*=\s*(\[[\s\S]*?\])\s*;""")
            val block = oldP.find(js)?.groupValues?.get(1) ?: return emptyList()
            Log.d("FundApiService", "Using fallback regex parsing for grandTotal")
            
            val series = mutableListOf<ChartSeries>()
            val nameP = Regex(""""name"\s*:\s*"([^"]*)"""")
            val dataP = Regex(""""data"\s*:\s*(\[[\s\S]*?\])\s*\}""")
            val pointP = Regex("""\[\s*(\d+)\s*,\s*([-\d.]+)\s*\]""")

            val parts = block.split("""{"name":""").drop(1)
            for (part in parts) {
                val nm = nameP.find(""""name":"$part""")?.groupValues?.get(1)
                    ?: part.substringBefore('"').trim()
                val dataBlock = dataP.find(part)?.groupValues?.get(1) ?: continue
                val pts = pointP.findAll(dataBlock).map { m ->
                    ChartPoint(m.groupValues[1].toLong(), m.groupValues[2].toDouble())
                }.toList()
                val seriesName = Regex("""^([^"]+)""").find(part)?.value ?: nm
                series.add(ChartSeries(name = seriesName, points = pts))
            }
            return series
        } catch (e: Exception) {
            Log.e("FundApiService", "Error parsing grandTotal: ${e.message}", e)
            return emptyList()
        }
    }

    private fun parseRank(js: String, sc: String): String {
        // 在 swithSameType 或类似结构中查找排名信息
        val p = Regex(""""sc"\s*:\s*"$sc"[^}]*?"rank"\s*:\s*"([^"]*)"""")
        val alt = Regex(""""rank"\s*:\s*"([^"]*)"[^}]*?"sc"\s*:\s*"$sc"""")
        return p.find(js)?.groupValues?.get(1) ?: alt.find(js)?.groupValues?.get(1) ?: ""
    }

    private fun parseTopHoldings(js: String): List<StockHolding> {
        // 首先尝试新的 JSON 数组格式：var stockCodesNew =["1.603486","1.689009",...]
        val jsonArrayP = Regex("""var\s+stockCodesNew\s*=\s*\[([^\]]*)\]""")
        val jsonMatch = jsonArrayP.find(js)
        if (jsonMatch != null) {
            val codes = jsonMatch.groupValues[1]
                .split(",")
                .map { it.trim().replace("\"", "").replace("'", "") }
                .filter { it.isNotEmpty() }
            // 对于 JSON 格式，我们只有代码，返回最少信息
            // DetailViewModel 将从持仓 API 获取名称/比例
            return codes.mapIndexed { index, fullCode ->
                val code = fullCode.substringAfter(".")
                StockHolding(
                    code = code,
                    name = "", // 将由持仓 API 填充
                    ratio = "0", // 将由持仓 API 填充
                    isNew = false
                )
            }.take(10)
        }

        // 回退到旧字符串格式：var stockCodesNew ="code,name,ratio|code,name,ratio..."
        val stringP = Regex("""var\s+stockCodesNew\s*=\s*"([^"]*)"""")
        val data = stringP.find(js)?.groupValues?.get(1) ?: return emptyList()
        if (data.isBlank()) return emptyList()

        val oldP = Regex("""var\s+stockCodesOld\s*=\s*"([^"]*)"""")
        val oldData = oldP.find(js)?.groupValues?.get(1) ?: ""
        val oldCodes = oldData.split("|").mapNotNull { it.split(",").firstOrNull() }.toSet()

        return data.split("|").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size >= 3) {
                StockHolding(
                    code = parts[0],
                    name = parts[1],
                    ratio = parts.getOrElse(2) { "0" },
                    isNew = parts[0] !in oldCodes
                )
            } else null
        }.take(10)
    }

    private fun parseHoldingsDate(js: String): String {
        // 尝试查找 fund_sourceRate 日期
        val p = Regex(""""FSRQ"\s*:\s*"([^"]*)"""")
        return p.find(js)?.groupValues?.get(1) ?: ""
    }

    /**
     * 从 HTML API 获取详细持仓。
     * 返回（日期，包含名称和比例的持仓列表）。
     */
    suspend fun getDetailedHoldings(fundCode: String): Pair<String, List<StockHolding>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "http://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=$fundCode&topline=10"
                Log.d("FundApiService", "Fetching detailed holdings from: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "http://fundf10.eastmoney.com/")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext ("" to emptyList())
                
                Log.d("FundApiService", "Holdings response length: ${body.length}")
                
                // 从 JSON 响应解析 HTML 内容：var apidata={ content:"...", ... }
                val contentPattern = Regex("""content:\s*"(.*?)"\s*,""", RegexOption.DOT_MATCHES_ALL)
                val htmlContent = contentPattern.find(body)?.groupValues?.get(1)?.replace("\\/", "/") ?: ""
                
                if (htmlContent.isEmpty()) {
                    Log.e("FundApiService", "No HTML content found in response")
                    return@withContext ("" to emptyList())
                }
                
                // 提取日期
                val datePattern = Regex("""截止至：<font[^>]*>([^<]+)</font>""")
                val date = datePattern.find(htmlContent)?.groupValues?.get(1) ?: ""
                Log.d("FundApiService", "Holdings date: $date")
                
                // 提取股票行
                // HTML 结构：<tr><td>序号</td><td><a>代码</a></td><td><a>名称</a></td><td>价格</td><td>涨跌幅</td><td>占比</td>...
                val holdings = mutableListOf<StockHolding>()
                
                // 按顺序提取所有链接文本（它们交替出现：代码、名称、代码、名称...）
                val linkPattern = Regex("""<a[^>]*>([^<]+)</a>""")
                val allLinks = linkPattern.findAll(htmlContent).map { it.groupValues[1].trim() }.toList()
                
                // 提取所有 TD 内容（比例在普通 TD 中）
                val tdPattern = Regex("""<td[^>]*>([^<]+)</td>""")
                val allTds = tdPattern.findAll(htmlContent).map { it.groupValues[1].trim() }.toList()
                
                // 处理：链接顺序为 [基金名称, 代码1, 名称1, ..., 代码2, 名称2, ...]
                // TD 顺序为 [序号1, 占比1, 价格1, 市值1, 序号2, 占比2, ...]
                // 我们需要将代码与名称和比例匹配
                var linkIndex = 1 // 跳过基金名称
                var tdIndex = 0
                
                while (linkIndex < allLinks.size - 1) {
                    val code = allLinks[linkIndex]
                    val name = allLinks[linkIndex + 1]
                    
                    // 查找对应的比例（跳过序号，获取占比，它是组中的第 2 个 td）
                    val ratioIndex = tdIndex + 1
                    val ratio = if (ratioIndex < allTds.size) {
                        allTds[ratioIndex].replace("%", "").trim()
                    } else "0"
                    
                    // 跳到下一只股票（每只股票有 4 个 TD：序号、占比、价格、市值）
                    tdIndex += 4
                    linkIndex += 5 // 跳过：代码、名称、"变动详情"、"股吧"、"行情"
                    
                    if (code.matches(Regex("\\d+"))) {
                        holdings.add(StockHolding(
                            code = code,
                            name = name,
                            ratio = ratio.ifEmpty { "0" },
                            isNew = false
                        ))
                    }
                    
                    if (holdings.size >= 10) break
                }
                
                Log.d("FundApiService", "Parsed ${holdings.size} holdings: ${holdings.take(3)}")
                date to holdings
            } catch (e: Exception) {
                Log.e("FundApiService", "Error fetching detailed holdings: ${e.message}", e)
                "" to emptyList()
            }
        }

    /**
     * 从 HTML 页面获取基金基本信息以补充详情数据。
     */
    suspend fun getFundBasicInfo(fundCode: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "http://fundf10.eastmoney.com/jbgk_$fundCode.html"
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "http://fundf10.eastmoney.com/")
                    .build()
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: return@withContext emptyMap()
                
                val info = mutableMapOf<String, String>()
                
                // 提取基金全称
                val fullNamePattern = Regex("""<th>基金全称</th>\s*<td[^>]*>([^<]+)</td>""")
                fullNamePattern.find(html)?.groupValues?.get(1)?.let { info["fullName"] = it.trim() }
                
                // 提取基金类型
                val typePattern = Regex("""<th>基金类型</th>\s*<td[^>]*>([^<]+)</td>""")
                typePattern.find(html)?.groupValues?.get(1)?.let { info["type"] = it.trim() }
                
                // 提取基金经理
                val managerPattern = Regex("""<th>基金经理人</th>\s*<td[^>]*>(?:<a[^>]*>)?([^<]+)(?:</a>)?</td>""")
                managerPattern.find(html)?.groupValues?.get(1)?.let { info["manager"] = it.trim() }
                
                // 提取公司
                val companyPattern = Regex("""<th>基金管理人</th>\s*<td[^>]*>(?:<a[^>]*>)?([^<]+)(?:</a>)?</td>""")
                companyPattern.find(html)?.groupValues?.get(1)?.let { info["company"] = it.trim() }
                
                // 提取成立日期
                val estDatePattern = Regex("""<th>成立日期/规模</th>\s*<td[^>]*>([^/]+)/""")
                estDatePattern.find(html)?.groupValues?.get(1)?.let { info["establishDate"] = it.trim() }
                
                // 提取基金规模
                val scalePattern = Regex("""<th>净资产规模</th>\s*<td[^>]*>([^<（]+)""")
                scalePattern.find(html)?.groupValues?.get(1)?.let { info["scale"] = it.trim() }
                
                Log.d("FundApiService", "Parsed basic info: $info")
                info
            } catch (e: Exception) {
                Log.e("FundApiService", "Error fetching basic info: ${e.message}", e)
                emptyMap()
            }
        }

    /** 获取持仓股票的实时价格。 */
    suspend fun getStockPrices(stockCodes: List<String>): Map<String, Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, Pair<String, String>>() // 代码 -> (价格, 涨跌幅%)
            try {
                // 构建 secids：1.代码 用于上海，0.代码 用于深圳/创业板
                val secids = stockCodes.joinToString(",") { code ->
                    val prefix = when {
                        code.startsWith("6") -> "1"
                        code.startsWith("0") || code.startsWith("3") -> "0"
                        code.startsWith("4") || code.startsWith("8") -> "0"
                        else -> "1"
                    }
                    "$prefix.$code"
                }
                val url = "https://push2.eastmoney.com/api/qt/ulist.np/get?fltt=2&fields=f2,f3,f12,f14&secids=$secids"
                val request = Request.Builder().url(url)
                    .header("Referer", "https://quote.eastmoney.com/")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext result
                val json = gson.fromJson(body, Map::class.java)
                val data = json["data"] as? Map<*, *> ?: return@withContext result
                val diff = data["diff"] as? List<*> ?: return@withContext result
                for (item in diff) {
                    val m = item as? Map<*, *> ?: continue
                    val c = m["f12"]?.toString() ?: continue
                    val price = m["f2"]?.toString() ?: "-"
                    val change = m["f3"]?.toString() ?: "0"
                    result[c] = Pair(price, change)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            result
        }

    /**
     * 从新浪财经获取财经新闻。
     * @param page 页码（从 1 开始）。
     * @param pageSize 每页新闻数量。
     */
    suspend fun getNews(page: Int = 1, pageSize: Int = 20): List<NewsDto> =
        withContext(Dispatchers.IO) {
            try {
                val url = String.format(NEWS_URL, pageSize, page)
                Log.d("FundApiService", "Fetching news from: $url")
                val request = Request.Builder()
                    .url(url)
                    .header("Referer", "https://finance.sina.com.cn/")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext emptyList()
                Log.d("FundApiService", "Response code: ${response.code}, body length: ${body.length}")
                Log.d("FundApiService", "Response preview: ${body.take(500)}")

                // 解析新浪新闻 JSON 响应
                val newsResponse = gson.fromJson(body, SinaNewsResponse::class.java)
                val newsList = newsResponse.result?.data ?: emptyList()
                Log.d("FundApiService", "Parsed ${newsList.size} news items")
                if (newsList.isNotEmpty()) {
                    Log.d("FundApiService", "First news: ${newsList.first().title}")
                }
                newsList
            } catch (e: Exception) {
                Log.e("FundApiService", "Error fetching news: ${e.message}", e)
                e.printStackTrace()
                emptyList()
            }
        }

    // ==================== 净值历史 HTML 解析 ====================

    private fun parseNavHistory(html: String): List<NavHistoryItem> {
        val results = mutableListOf<NavHistoryItem>()
        // 每一行：<tr><td>日期</td><td>净值</td><td>累计净值</td><td>增长率</td></tr>
        val rowPattern = Regex("""<tr>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
        val cellPattern = Regex("""<td[^>]*>(.*?)</td>""")

        for (rowMatch in rowPattern.findAll(html)) {
            val cells = cellPattern.findAll(rowMatch.groupValues[1]).map {
                it.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            }.toList()

            if (cells.size >= 4) {
                val date = cells[0]
                val nav = cells[1].toDoubleOrNull() ?: continue
                val totalNav = cells[2].toDoubleOrNull() ?: nav
                val growth = cells[3]
                // 跳过表头行
                if (date.contains("-") && !date.contains("日期")) {
                    results.add(NavHistoryItem(date, nav, totalNav, growth))
                }
            }
        }
        return results
    }

    private fun extractJsonFromJsonp(jsonp: String): String? {
        val regex = Regex("""jsonpgz\((.*)\)""")
        val match = regex.find(jsonp)
        return match?.groupValues?.get(1)
    }

    /**
     * 解析搜索结果，这是一个类似 JSONP 的响应。
     * 响应格式包含带有基金信息的 Datas 数组。
     */
    private fun parseSearchResult(body: String): List<FundSearchResultDto> {
        val results = mutableListOf<FundSearchResultDto>()
        try {
            // 首先尝试解析为 JSON
            val jsonStr = if (body.contains("(")) {
                // JSONP 格式 - 提取 JSON
                val start = body.indexOf("(")
                val end = body.lastIndexOf(")")
                if (start >= 0 && end > start) body.substring(start + 1, end) else body
            } else {
                body
            }

            val jsonObj = gson.fromJson(jsonStr, Map::class.java)
            val datas = jsonObj["Datas"] as? List<*> ?: return results

            for (item in datas) {
                val map = item as? Map<*, *> ?: continue
                val code = map["CODE"]?.toString() ?: continue
                val name = map["NAME"]?.toString() ?: continue
                val type = map["FundBaseInfo"]?.toString()
                    ?.let { parseType(it) } ?: "基金"

                // 仅包含基金类型（代码为 6 位数字）
                if (code.length == 6 && code.all { it.isDigit() }) {
                    results.add(FundSearchResultDto(code, name, type))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun parseType(baseInfo: String): String {
        return when {
            baseInfo.contains("股票") -> "股票型"
            baseInfo.contains("混合") -> "混合型"
            baseInfo.contains("债券") -> "债券型"
            baseInfo.contains("指数") -> "指数型"
            baseInfo.contains("货币") -> "货币型"
            baseInfo.contains("QDII") -> "QDII"
            baseInfo.contains("FOF") -> "FOF"
            else -> "基金"
        }
    }
}
