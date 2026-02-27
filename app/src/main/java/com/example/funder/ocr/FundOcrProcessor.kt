package com.example.funder.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR识别的基金持仓信息
 */
data class OcrFundResult(
    val fundCode: String,      // 基金代码
    val fundName: String,      // 基金名称
    val amount: Double?,       // 持有金额/市值
    val shares: Double?,       // 持有份额
    val costPrice: Double?,    // 成本净值
    val profitLoss: Double?    // 盈亏/收益
)

/**
 * OCR识别结果，包含原始文本用于调试
 */
data class OcrResult(
    val rawText: String,       // 原始识别文本
    val funds: List<OcrFundResult>  // 识别到的基金列表
)

@Singleton
class FundOcrProcessor @Inject constructor() {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /**
     * 从图片识别文本并提取基金信息
     * 使用Text blocks保持空间关系，支持表格识别
     */
    suspend fun recognizeFromBitmap(bitmap: Bitmap): OcrResult {
        val visionText = extractVisionText(bitmap)
        val rawText = visionText.text
        
        Log.d("FundOcrProcessor", "========== OCR Start ==========")
        Log.d("FundOcrProcessor", "Raw text length: ${rawText.length}")
        
        // 使用文本块保持空间布局
        val funds = parseFundInfoFromBlocks(visionText)
        
        Log.d("FundOcrProcessor", "Parsed ${funds.size} funds")
        funds.forEachIndexed { index, fund ->
            Log.d("FundOcrProcessor", "Fund $index: ${fund.fundName} [${fund.fundCode}] - amount: ${fund.amount}, shares: ${fund.shares}")
        }
        Log.d("FundOcrProcessor", "========== OCR End ==========")
        
        return OcrResult(rawText = rawText, funds = funds)
    }

    /**
     * 使用 ML Kit 从图片中提取文本。
     */
    private suspend fun extractVisionText(bitmap: Bitmap): Text =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    // ==================== 关键词 ====================

    companion object {
        // 表示"持有金额"/"市值"的关键词（持仓的货币价值）
        private val AMOUNT_KEYWORDS = listOf(
            "持有金额", "金额", "市值", "资产", "总额",
            "持有市值", "参考市值", "最新市值", "当前市值",
            "持仓金额", "持仓市值"
        )

        // 表示"持有份额"的关键词（份额数量）
        private val SHARES_KEYWORDS = listOf(
            "持有份额", "份额", "持仓份额", "可用份额",
            "总份额", "基金份额", "可用", "持有"
        )

        // 表示盈亏的关键词
        private val PROFIT_KEYWORDS = listOf(
            "收益", "盈亏", "浮动盈亏", "累计收益", "持有收益",
            "日收益", "今日收益", "总收益", "累计盈亏"
        )

        // 数字模式：匹配整数和小数，可选逗号、+/-、¥
        // 示例：10234.56, 10,234.56, +234.56, -50.00, ¥10234, 10234
        private val NUMBER_PATTERN = Regex(
            """[¥￥]?\s*[+-]?\s*\d[\d,，]*(?:\.\d+)?"""
        )

        // 基金代码：6位连续数字
        private val FUND_CODE_PATTERN = Regex("""(?<!\d)(\d{6})(?!\d)""")
    }

    // ==================== 使用文本块的主解析 ====================
    
    /**
     * 使用表格识别方法解析基金信息
     * 识别表格结构：表头 + 数据行
     */
    private fun parseFundInfoFromBlocks(visionText: Text): List<OcrFundResult> {
        val fundResults = mutableListOf<OcrFundResult>()
        
        Log.d("FundOcrProcessor", "Text blocks count: ${visionText.textBlocks.size}")
        
        // 收集所有文本元素及其位置
        val allElements = mutableListOf<TextElement>()
        
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val boundingBox = line.boundingBox
                if (boundingBox != null) {
                    val centerY = boundingBox.centerY().toFloat()
                    val centerX = boundingBox.centerX().toFloat()
                    allElements.add(TextElement(line.text, centerY, centerX))
                }
            }
        }
        
        Log.d("FundOcrProcessor", "Total text elements: ${allElements.size}")
        
        // 步骤1：查找表头行（包含"基金名称"）
        val headerElement = allElements.find { 
            it.text.contains("基金名称") || it.text.contains("基金")
        }
        
        if (headerElement == null) {
            Log.w("FundOcrProcessor", "No table header found, falling back to keyword search")
            return parseFundsByKeywords(allElements)
        }
        
        val headerY = headerElement.y
        Log.d("FundOcrProcessor", "Found table header at Y=$headerY: ${headerElement.text}")
        
        // 步骤2：将元素分组为行（表头下方）
        val dataElements = allElements.filter { 
            it.y > headerY + 20  // 至少距离表头20像素
        }.sortedBy { it.y }
        
        // 按Y坐标分组（容差：15像素）
        val rows = mutableListOf<MutableList<TextElement>>()
        var currentRow = mutableListOf<TextElement>()
        var lastY = -1f
        
        for (element in dataElements) {
            if (lastY < 0 || kotlin.math.abs(element.y - lastY) < 15) {
                currentRow.add(element)
                lastY = element.y
            } else {
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                }
                currentRow = mutableListOf(element)
                lastY = element.y
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }
        
        Log.d("FundOcrProcessor", "Found ${rows.size} data rows below header")
        
        // 步骤3：将每一行作为基金条目处理
        for ((rowIndex, row) in rows.withIndex()) {
            // 按X坐标对行元素排序（从左到右）
            val sortedRow = row.sortedBy { it.x }
            val rowText = sortedRow.joinToString(" ") { it.text }
            
            Log.d("FundOcrProcessor", "Row $rowIndex: $rowText")
            
            // 如果这一行看起来像UI元素或摘要，则跳过
            if (rowText.contains("行情中心") || rowText.contains("总金额") || 
                rowText.contains("日收益") || rowText.contains("暂停更新") ||
                rowText.contains("编辑") || rowText.contains("设置")) {
                Log.d("FundOcrProcessor", "  -> Skipped (UI element)")
                continue
            }
            
            // 提取基金名称：第一个包含>=5个字符的中文文本
            var fundName = ""
            for (element in sortedRow) {
                val text = element.text.trim()
                    .replace("|", "")
                    .replace("（", "(")
                    .replace("）", ")")
                val chineseCount = text.count { it in '\u4e00'..'\u9fff' }
                if (chineseCount >= 5) {
                    fundName = text
                    break
                }
            }
            
            if (fundName.isEmpty()) {
                Log.d("FundOcrProcessor", "  -> Skipped (no fund name)")
                continue
            }
            
            // 过滤掉市场指数
            if (fundName in listOf("上证指数", "沪深300", "深证成指", "创业板指", "恒生指数")) {
                Log.d("FundOcrProcessor", "  -> Skipped (market index)")
                continue
            }
            
            Log.d("FundOcrProcessor", "  -> Fund name: $fundName")
            
            // 提取基金代码（6位数字）
            val codeMatch = FUND_CODE_PATTERN.find(rowText)
            val fundCode = if (codeMatch != null && !isLikelyDate(codeMatch.value)) {
                codeMatch.value
            } else {
                "SEARCH_${fundResults.size}"
            }
            
            // 从这一行提取所有数字
            val numbers = mutableListOf<Double>()
            for (element in sortedRow) {
                if (element.text == fundName || element.text == fundCode) continue
                
                NUMBER_PATTERN.findAll(element.text).forEach { match ->
                    val cleaned = cleanNumber(match.value)
                    if (cleaned != null && cleaned >= 1000) {
                        numbers.add(cleaned)
                    }
                }
            }
            
            // 最大数字是持仓金额
            val amount = numbers.maxOrNull()
            
            Log.d("FundOcrProcessor", "  -> Code: $fundCode, Amount: $amount")
            
            fundResults.add(OcrFundResult(
                fundCode = fundCode,
                fundName = fundName,
                amount = amount,
                shares = null,  // 稍后计算
                costPrice = null,
                profitLoss = null
            ))
        }
        
        Log.d("FundOcrProcessor", "=== Table extraction complete: ${fundResults.size} funds ===")
        return fundResults
    }
    
    /**
     * 备用方法：当找不到表头时，通过关键词解析基金
     */
    private fun parseFundsByKeywords(allElements: List<TextElement>): List<OcrFundResult> {
        val fundResults = mutableListOf<OcrFundResult>()
        
        Log.d("FundOcrProcessor", "=== Using keyword-based extraction ===")
        
        // 查找潜在的基金名称
        val fundNameElements = allElements.filter { element ->
            val text = element.text.trim()
                .replace("|", "")
                .replace("（", "(")
                .replace("）", ")")
            
            val hasChinese = text.count { it in '\u4e00'..'\u9fff' } >= 5
            val looksLikeFund = text.contains("混合") || text.contains("指数") || text.contains("ETF") || 
                 text.contains("股票") || text.contains("债券") || text.contains("主题") ||
                 text.contains("产业") || text.contains("配置") || text.contains("灵活") ||
                 text.contains("联接") || text.contains("发起") || text.contains("机器人") ||
                 text.contains("卫星") || text.contains("低碳") || text.contains("人工智能") ||
                 text.contains("国防") || text.endsWith("C") || text.endsWith("A")
            val notExcluded = text !in listOf("上证指数", "沪深300", "深证成指", "创业板指", 
                "恒生指数", "行情中心", "日志", "打赏", "编辑", "设置")
            
            hasChinese && looksLikeFund && notExcluded
        }
        
        Log.d("FundOcrProcessor", "Found ${fundNameElements.size} potential fund names")
        
        // 对于每个基金名称，查找附近的数字
        for (nameElement in fundNameElements.sortedBy { it.y }) {
            val fundName = nameElement.text.trim()
                .replace("|", "")
                .replace("（", "(")
                .replace("）", ")")
            
            // 查找同一行的元素
            val sameRowElements = allElements.filter { element ->
                kotlin.math.abs(element.y - nameElement.y) < 15
            }
            
            val rowText = sameRowElements.joinToString(" ") { it.text }
            
            // 提取基金代码
            val codeMatch = FUND_CODE_PATTERN.find(rowText)
            val fundCode = if (codeMatch != null && !isLikelyDate(codeMatch.value)) {
                codeMatch.value
            } else {
                "SEARCH_${fundResults.size}"
            }
            
            // 提取数字
            val numbers = mutableListOf<Double>()
            for (element in sameRowElements) {
                NUMBER_PATTERN.findAll(element.text).forEach { match ->
                    val cleaned = cleanNumber(match.value)
                    if (cleaned != null && cleaned >= 1000) {
                        numbers.add(cleaned)
                    }
                }
            }
            
            val amount = numbers.maxOrNull()
            
            fundResults.add(OcrFundResult(
                fundCode = fundCode,
                fundName = fundName,
                amount = amount,
                shares = null,
                costPrice = null,
                profitLoss = null
            ))
        }
        
        Log.d("FundOcrProcessor", "=== Keyword extraction complete: ${fundResults.size} funds ===")
        return fundResults
    }

    // ==================== Main parsing (legacy, for code-based) ====================

    fun parseFundInfo(text: String): List<OcrFundResult> {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            Log.d("FundOcrProcessor", "No lines after splitting")
            return emptyList()
        }

        Log.d("FundOcrProcessor", "Total lines: ${lines.size}")
        lines.take(10).forEachIndexed { i, line ->
            Log.d("FundOcrProcessor", "Line $i: $line")
        }

        // 步骤1：查找所有6位基金代码及其行位置
        val fundEntries = mutableListOf<FundEntry>()
        for (i in lines.indices) {
            val codes = FUND_CODE_PATTERN.findAll(lines[i]).toList()
            for (codeMatch in codes) {
                val code = codeMatch.value
                if (isLikelyDate(code) || isLikelyNonFundCode(code)) continue
                fundEntries.add(FundEntry(code = code, codeLine = i))
                Log.d("FundOcrProcessor", "Found fund code: $code at line $i")
            }
        }

        // 如果未找到基金代码，尝试提取带金额的基金名称
        if (fundEntries.isEmpty()) {
            Log.d("FundOcrProcessor", "No fund codes found, trying name-based extraction")
            val fundResults = extractFundsFromNameList(lines)
            if (fundResults.isNotEmpty()) {
                Log.d("FundOcrProcessor", "Extracted ${fundResults.size} funds by name")
                return fundResults
            }
            Log.d("FundOcrProcessor", "Name-based extraction found nothing")
            return emptyList()
        }

        // 步骤2：对于每个基金，确定其"所属"的行范围
        //         （从其代码行到下一个基金代码行之前）
        for (idx in fundEntries.indices) {
            val nextCodeLine = if (idx + 1 < fundEntries.size) {
                fundEntries[idx + 1].codeLine
            } else {
                lines.size
            }
            fundEntries[idx].endLine = nextCodeLine
        }

        // 步骤3：使用行范围解析每个基金条目
        val results = fundEntries.map { entry ->
            parseSingleFund(lines, entry)
        }

        return results.distinctBy { it.fundCode }
    }

    // ==================== Single fund parsing ====================

    private fun parseSingleFund(lines: List<String>, entry: FundEntry): OcrFundResult {
        val code = entry.code
        val startLine = maxOf(0, entry.codeLine - 2) // look 2 lines above for name
        val endLine = minOf(lines.size, entry.endLine)
        val regionLines = lines.subList(startLine, endLine)
        val regionText = regionLines.joinToString("\n")

        // 提取基金名称
        val fundName = extractFundName(lines, entry.codeLine, code)

        // 使用区域内的关键词匹配提取金额
        var amount: Double? = null
        var shares: Double? = null
        var profitLoss: Double? = null

        for (line in regionLines) {
            val cleanLine = line.replace(" ", "")

            // 首先尝试基于关键词的匹配
            val matchedAmount = matchKeywordNumber(cleanLine, AMOUNT_KEYWORDS)
            val matchedShares = matchKeywordNumber(cleanLine, SHARES_KEYWORDS)
            val matchedProfit = matchKeywordNumber(cleanLine, PROFIT_KEYWORDS)

            if (matchedAmount != null && amount == null) amount = matchedAmount
            if (matchedShares != null && shares == null) shares = matchedShares
            if (matchedProfit != null && profitLoss == null) profitLoss = matchedProfit
        }

        // 如果关键词匹配未找到所有内容，则回退到位置猜测
        if (amount == null || shares == null) {
            val fallback = fallbackExtractAmounts(regionLines, code)
            if (amount == null) amount = fallback.amount
            if (shares == null) shares = fallback.shares
            if (profitLoss == null) profitLoss = fallback.profitLoss
        }

        return OcrFundResult(
            fundCode = code,
            fundName = fundName,
            amount = amount,
            shares = shares,
            costPrice = null,
            profitLoss = profitLoss
        )
    }

    // ==================== Keyword + number matching ====================

    /**
     * For a given line, check if it contains any of the keywords.
     * If yes, extract the first number from that line as the associated value.
     *
     * Handles formats like:
     *   "持有金额(元) 10,234.56"
     *   "份额 8765.43份"
     *   "持有金额 ¥10234.56"
     *   "10,234.56 持有金额"
     */
    private fun matchKeywordNumber(line: String, keywords: List<String>): Double? {
        val hasKeyword = keywords.any { line.contains(it) }
        if (!hasKeyword) return null

        val numbers = NUMBER_PATTERN.findAll(line).toList()
        if (numbers.isEmpty()) return null

        // 返回绝对值最大的数字（最可能是主要数值）
        return numbers
            .mapNotNull { cleanNumber(it.value) }
            .maxByOrNull { kotlin.math.abs(it) }
    }

    // ==================== 回退：位置数字提取 ====================

    /**
     * 当未找到关键词时（例如简单的截图），收集区域内的所有数字
     * 并尝试猜测哪个是金额，哪个是份额。
     *
     * 启发式规则：
     * - 收集基金代码附近行的所有数字（排除代码本身）
     * - 最大的正数可能是"金额"
     * - 第二大的正数可能是"份额"
     * - 负数或带有+/-标记的数字是"盈亏"
     */
    private fun fallbackExtractAmounts(regionLines: List<String>, code: String): FallbackAmounts {
        val allNumbers = mutableListOf<NumberWithSign>()

        for (line in regionLines) {
            // 跳过仅包含基金代码或名称的行
            if (line.trim() == code) continue

            val matches = NUMBER_PATTERN.findAll(line)
            for (m in matches) {
                val raw = m.value
                // 如果这个数字就是基金代码，则跳过
                val digits = raw.replace(Regex("[^0-9]"), "")
                if (digits == code) continue
                // 跳过看起来像百分比或日期的非常小的数字
                val value = cleanNumber(raw) ?: continue
                if (kotlin.math.abs(value) < 0.001) continue

                val hasSign = raw.contains('+') || raw.contains('-')
                allNumbers.add(NumberWithSign(value, hasSign))
            }
        }

        if (allNumbers.isEmpty()) return FallbackAmounts(null, null, null)

        // 将有符号（盈亏）与无符号（金额/份额）分开
        val profitCandidate = allNumbers.filter { it.hasPlusMinusSign }.maxByOrNull { kotlin.math.abs(it.value) }
        val unsigned = allNumbers.filter { !it.hasPlusMinusSign }.sortedByDescending { it.value }

        val amount = unsigned.getOrNull(0)?.value
        val shares = unsigned.getOrNull(1)?.value

        return FallbackAmounts(
            amount = amount,
            shares = shares,
            profitLoss = profitCandidate?.value
        )
    }

    // ==================== 基金名称提取 ====================

    private fun extractFundName(lines: List<String>, codeLineIndex: Int, code: String): String {
        // 首先检查当前行
        val currentLine = lines[codeLineIndex]
        val nameFromCurrent = extractNameFromLine(currentLine, code)
        if (nameFromCurrent.isNotEmpty()) return nameFromCurrent

        // 检查上一行（常见情况：名称在上，代码在下）
        if (codeLineIndex > 0) {
            val above = extractChineseName(lines[codeLineIndex - 1])
            if (above.isNotEmpty() && !isKeywordLine(lines[codeLineIndex - 1])) return above
        }

        // 检查下一行
        if (codeLineIndex < lines.lastIndex) {
            val below = extractChineseName(lines[codeLineIndex + 1])
            if (below.isNotEmpty() && !isKeywordLine(lines[codeLineIndex + 1])) return below
        }

        return ""
    }

    private fun extractNameFromLine(line: String, code: String): String {
        val withoutCode = line.replace(code, " ").trim()
        return extractChineseName(withoutCode)
    }

    /**
     * 提取中文基金名称。
     * 基金名称：2~20个字符，中文，可选字母（A/C/ETF）和数字（500/300）。
     */
    private fun extractChineseName(text: String): String {
        val namePattern = Regex("""[\u4e00-\u9fff][\u4e00-\u9fffA-Za-z0-9（）()·\-/]{1,30}""")
        val match = namePattern.find(text)
        val name = match?.value ?: return ""
        // 不要将类似关键词的文本作为基金名称返回
        if (isKeywordText(name)) return ""
        return name
    }

    /**
     * 从列表截图中提取基金。
     * 策略：查找包含基金名称的行，然后从同一行提取代码和数字。
     */
    private fun extractFundsFromNameList(lines: List<String>): List<OcrFundResult> {
        val fundResults = mutableListOf<OcrFundResult>()
        val seenNames = mutableSetOf<String>()
        
        Log.d("FundOcrProcessor", "=== Name-based extraction started ===")
        Log.d("FundOcrProcessor", "Total lines: ${lines.size}")
        
        // 第一遍：从表头识别表格结构
        var hasSharesColumn = false
        var hasAmountColumn = false
        for (line in lines.take(5)) {
            if (line.contains("持有份额") || line.contains("份额")) hasSharesColumn = true
            if (line.contains("持有金额") || line.contains("金额") || line.contains("持有市值")) hasAmountColumn = true
        }
        Log.d("FundOcrProcessor", "Table structure: shares=$hasSharesColumn, amount=$hasAmountColumn")
        
        for (i in lines.indices) {
            val line = lines[i]
            
            // 跳过短行
            if (line.length < 4) continue
            
            // 跳过表头/页脚行
            if (line.contains("基金名称") || line.contains("估算净值") || 
                line.contains("涨跌幅") || line.contains("持有收益") ||
                line.contains("更新时间") || line.contains("总资产") ||
                line.contains("合计") || line.contains("共")) {
                continue
            }
            
            // 必须包含中文字符
            val chineseCount = line.count { it in '\u4e00'..'\u9fff' }
            if (chineseCount < 3) continue
            
            // 提取基金名称
            val fundName = extractFundNameFromLine(line)
            if (fundName.isEmpty() || fundName in seenNames) continue
            
            Log.d("FundOcrProcessor", "Line $i: $line")
            Log.d("FundOcrProcessor", "  -> Extracted name: $fundName")
            
            // 尝试在同一行查找6位代码
            val codeMatch = FUND_CODE_PATTERN.find(line)
            val fundCode = if (codeMatch != null && !isLikelyDate(codeMatch.value)) {
                Log.d("FundOcrProcessor", "  -> Found code: ${codeMatch.value}")
                codeMatch.value
            } else {
                "SEARCH_${fundResults.size}"
            }
            
            // 从行中提取所有数字（排除基金名称和代码）
            val lineWithoutNameAndCode = line
                .replace(fundName, "")
                .replace(fundCode, "")
            
            val allNumbers = NUMBER_PATTERN.findAll(lineWithoutNameAndCode)
                .mapNotNull { match ->
                    val cleaned = cleanNumber(match.value)
                    if (cleaned != null && kotlin.math.abs(cleaned) > 0.01) {
                        cleaned
                    } else null
                }
                .toList()
            
            Log.d("FundOcrProcessor", "  -> All numbers: $allNumbers")
            
            // 基于数量级的智能数字分配
            // 典型模式：
            // - 估算净值: 1.xxxx (小，约1-10)
            // - 持有份额: 数百到数千（例如，76,807.93）
            // - 持有金额: 数千到数百万（可能与份额重叠）
            // - 日收益率: -1.09% (非常小，通常为负数)
            
            var amount: Double? = null
            var shares: Double? = null
            var profitLoss: Double? = null
            
            // 按数量级分开
            val largeNumbers = allNumbers.filter { kotlin.math.abs(it) >= 1000 } // 1000+
            val mediumNumbers = allNumbers.filter { it >= 100 && it < 1000 } // 100-999
            val smallNumbers = allNumbers.filter { kotlin.math.abs(it) < 100 }
            val negativeNumbers = allNumbers.filter { it < 0 }
            
            // 如果有2个或更多大数字，它们可能是份额和金额
            if (largeNumbers.size >= 2) {
                // 两者都可能是份额或金额，按顺序选择
                amount = largeNumbers[0]
                shares = largeNumbers[1]
            } else if (largeNumbers.size == 1) {
                // 一个大数字 - 可能是金额或份额
                amount = largeNumbers[0]
                // 查找中等数字作为份额
                shares = mediumNumbers.firstOrNull()
            } else {
                // 没有大数字，如果有中等数字则使用
                amount = mediumNumbers.firstOrNull()
            }
            
            // 盈亏通常是负数或非常小的正数
            profitLoss = negativeNumbers.firstOrNull()
            
            Log.d("FundOcrProcessor", "  -> Final: amount=$amount, shares=$shares, profit=$profitLoss")
            
            fundResults.add(OcrFundResult(
                fundCode = fundCode,
                fundName = fundName,
                amount = amount,
                shares = shares,
                costPrice = null,
                profitLoss = profitLoss
            ))
            
            seenNames.add(fundName)
        }
        
        Log.d("FundOcrProcessor", "=== Extraction complete: ${fundResults.size} funds ===")
        return fundResults
    }
    
    /**
     * 从单行提取基金名称。
     * 返回看起来像基金名称的最长中文文本段。
     */
    private fun extractFundNameFromLine(line: String): String {
        // 移除纯数字序列和常见符号
        val cleaned = line
            .replace(Regex("""\d+\.\d+"""), " ") // 移除小数，如123.45
            .replace(Regex(""",\d+"""), " ") // 移除逗号数字
            .replace(Regex("""[%¥￥+\-]"""), " ")
        
        // 查找所有中文文本段
        val chineseSegments = Regex("""[\u4e00-\u9fff]+(?:[A-Za-z0-9·（）()/\-]{0,3}[\u4e00-\u9fff]+)*(?:[A-Za-z0-9ETFLOFQDII/]{0,8})?""")
            .findAll(cleaned)
            .map { it.value.trim() }
            .filter { it.length >= 4 && it.count { c -> c in '\u4e00'..'\u9fff' } >= 3 }
            .toList()
        
        if (chineseSegments.isEmpty()) return ""
        
        // 返回最长的段作为基金名称
        val longest = chineseSegments.maxByOrNull { it.length } ?: ""
        
        // 验证它不是关键词
        if (isKeywordText(longest)) return ""
        
        return longest
    }
    
    /**
     * 从一行提取所有重要数字（排除基金名称）。
     */
    private fun extractNumbersFromLine(line: String, fundName: String): List<Double> {
        // 从行中移除基金名称
        val lineWithoutName = line.replace(fundName, " ")
        
        // 提取所有数字
        val numbers = mutableListOf<Double>()
        val matches = NUMBER_PATTERN.findAll(lineWithoutName)
        
        for (match in matches) {
            val cleaned = cleanNumber(match.value)
            if (cleaned != null && kotlin.math.abs(cleaned) > 0.01) {
                numbers.add(cleaned)
            }
        }
        
        // 按绝对值排序（最大优先）
        return numbers.sortedByDescending { kotlin.math.abs(it) }
    }

    /** 检查一行是否主要是关键词（如"持有金额(元)"）而不是基金名称 */
    private fun isKeywordLine(line: String): Boolean {
        val keywords = AMOUNT_KEYWORDS + SHARES_KEYWORDS + PROFIT_KEYWORDS
        return keywords.any { line.contains(it) }
    }

    private fun isKeywordText(text: String): Boolean {
        val kwds = listOf(
            "持有金额", "持有份额", "金额", "份额", "市值",
            "收益", "盈亏", "日收益", "累计", "资产", "可用"
        )
        return kwds.any { text.contains(it) }
    }

    // ==================== 数字清理 ====================

    /**
     * 将由正则表达式提取的原始数字字符串清理为Double。
     * 处理：¥、￥、逗号、空格、+/-符号、中文逗号
     */
    private fun cleanNumber(raw: String): Double? {
        val cleaned = raw
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace("，", "")
            .replace(" ", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    // ==================== 工具函数 ====================

    private fun isLikelyDate(code: String): Boolean {
        val year = code.substring(0, 4).toIntOrNull() ?: return false
        val month = code.substring(4, 6).toIntOrNull() ?: return false
        return year in 2000..2030 && month in 1..12
    }

    private fun isLikelyNonFundCode(code: String): Boolean {
        return code.startsWith("999") || code == "000000"
    }

    // ==================== 辅助数据类 ====================

    private data class FundEntry(
        val code: String,
        val codeLine: Int,
        var endLine: Int = 0
    )

    private data class NumberWithSign(
        val value: Double,
        val hasPlusMinusSign: Boolean
    )

    private data class FallbackAmounts(
        val amount: Double?,
        val shares: Double?,
        val profitLoss: Double?
    )
    
    private data class TextElement(
        val text: String,
        val y: Float,
        val x: Float
    )
}
