package com.example.funder.ui.import_fund

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funder.data.local.FundHoldingEntity
import com.example.funder.data.remote.FundValuationDto
import com.example.funder.data.repository.FundRepository
import com.example.funder.ocr.FundOcrProcessor
import com.example.funder.ocr.OcrFundResult
import com.example.funder.ocr.OcrResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportFundItem(
    val ocrResult: OcrFundResult,
    val verifiedName: String? = null,
    val isSelected: Boolean = true,
    val shares: String = "",
    val totalCost: String = "",
    val editedFundCode: String = ""  // 用于手动编辑
) {
    val displayName: String get() = verifiedName ?: ocrResult.fundName
    val displayFundCode: String get() = editedFundCode.ifEmpty { ocrResult.fundCode }
    val displayShares: String
        get() = shares.ifEmpty {
            ocrResult.shares?.toString() ?: ""
        }
    val displayCost: String
        get() = totalCost.ifEmpty {
            ocrResult.amount?.toString() ?: ""
        }
}

data class ImportUiState(
    val selectedBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val recognizedText: String = "",
    val fundItems: List<ImportFundItem> = emptyList(),
    val isImporting: Boolean = false,
    val importSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val ocrProcessor: FundOcrProcessor,
    private val repository: FundRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onImageSelected(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                selectedBitmap = bitmap,
                isProcessing = true,
                fundItems = emptyList(),
                recognizedText = "",
                importSuccess = false,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val ocrResult = ocrProcessor.recognizeFromBitmap(bitmap)
                val results = ocrResult.funds

                if (results.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            recognizedText = ocrResult.rawText,
                            error = "未识别到基金信息，请确保图片中包含6位基金代码"
                        )
                    }
                    return@launch
                }

                // 通过 API 验证基金名称并自动计算份额
                val items = results.map { result ->
                    // 如果代码是占位符（以 SEARCH_ 开头），则按名称搜索
                    if (result.fundCode.startsWith("SEARCH_")) {
                        try {
                            // 搜索前清理基金名称
                            val cleanName = result.fundName
                                .replace("|", "")
                                .replace("（", "(")
                                .replace("）", ")")
                                .replace("(LOPc", "")  // 移除 OCR 错误后缀
                                .trim()
                            
                            android.util.Log.d("ImportViewModel", "搜索基金: $cleanName")
                            
                            // 特殊基金直接映射代码（避免搜索错误）
                            val directCodeMapping = mapOf(
                                "鹏华中证国防指数" to "160630",
                                "鹏华国防" to "160630"
                            )
                            
                            // 检查是否有直接映射
                            val directCode = directCodeMapping.entries.find { 
                                cleanName.contains(it.key) 
                            }?.value
                            
                            if (directCode != null) {
                                android.util.Log.d("ImportViewModel", "使用直接映射代码: $directCode")
                                try {
                                    val valuation = repository.getValuation(directCode)
                                    if (valuation != null) {
                                        val nav = valuation.nav?.toDoubleOrNull() ?: valuation.estimatedNav?.toDoubleOrNull()
                                        val calculatedShares = if (result.amount != null && nav != null && nav > 0) {
                                            result.amount / nav
                                        } else null
                                        
                                        return@map ImportFundItem(
                                            ocrResult = result.copy(fundCode = directCode, shares = calculatedShares),
                                            verifiedName = valuation.name,
                                            shares = calculatedShares?.let { "%.2f".format(it) } ?: "",
                                            totalCost = result.amount?.let { "%.2f".format(it) } ?: ""
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ImportViewModel", "直接映射失败: $directCode", e)
                                }
                            }
                            
                            // 多策略搜索
                            val searchStrategies = mutableListOf<String>()
                            searchStrategies.add(cleanName)  // 1. 原始名称
                            
                            // 2. 针对特定基金的优化
                            if (cleanName.contains("鹏华") && cleanName.contains("国防")) {
                                searchStrategies.add("鹏华中证国防指数LOF")
                            }
                            if (cleanName.contains("华泰柏瑞") && cleanName.contains("鼎利")) {
                                searchStrategies.add("华泰柏瑞鼎利")
                                searchStrategies.add("华泰柏瑞鼎利混合")
                            }
                            
                            // 3. 通用简化策略
                            searchStrategies.add(cleanName.replace("和灵活配置", "").replace("灵活配置", ""))
                            searchStrategies.add(cleanName.replace("发起联接", "联接"))
                            searchStrategies.add(cleanName.replace("(LOPc", "").replace("(", ""))
                            
                            val distinctStrategies = searchStrategies.distinct()
                            
                            var searchResults = emptyList<com.example.funder.data.remote.FundSearchResultDto>()
                            
                            for (searchName in distinctStrategies) {
                                if (searchName.length < 3) continue  // 太短的名称跳过
                                
                                android.util.Log.d("ImportViewModel", "尝试搜索: $searchName")
                                val results = repository.searchFund(searchName).filter { fund ->
                                    val name = fund.name
                                    // 必须包含基金关键词
                                    val isFund = name.contains("基金") || name.contains("混合") || 
                                        name.contains("指数") || name.contains("ETF") || name.contains("债券") ||
                                        name.contains("股票") || name.contains("LOF") || name.contains("QDII")
                                    // 必须有 6 位数字代码
                                    val hasValidCode = fund.code.matches(Regex("\\d{6}"))
                                    
                                    // 严格的名称相似度检查
                                    val hasSimilarity = if (cleanName.length >= 4) {
                                        // 提取原始名称的关键词（公司名+产品名）
                                        val companies = listOf("华泰柏瑞", "鹏华", "嘉实", "招商", "东方", "平安")
                                        val company = companies.find { cleanName.contains(it) }
                                        val keywords = when {
                                            cleanName.contains("鼎利") -> listOf("鼎利")
                                            cleanName.contains("国防") -> listOf("国防", "军工")
                                            cleanName.contains("卫星") -> listOf("卫星")
                                            cleanName.contains("机器人") -> listOf("机器人")
                                            cleanName.contains("享利") -> listOf("享利")
                                            cleanName.contains("低碳") -> listOf("低碳")
                                            cleanName.contains("人工智能") -> listOf("人工智能", "智能")
                                            else -> emptyList()
                                        }
                                        
                                        // 排除明显不相关的基金
                                        val excludeKeywords = when {
                                            cleanName.contains("国防") -> listOf("科创", "科技创新")
                                            else -> emptyList()
                                        }
                                        val isExcluded = excludeKeywords.any { name.contains(it) }
                                        
                                        // 必须包含公司名且包含关键词，不能包含排除词
                                        val hasCompany = company == null || name.contains(company)
                                        val hasKeyword = keywords.isEmpty() || keywords.any { name.contains(it) }
                                        
                                        hasCompany && hasKeyword && !isExcluded
                                    } else true
                                    
                                    isFund && hasValidCode && hasSimilarity
                                }
                                
                                if (results.isNotEmpty()) {
                                    searchResults = results
                                    android.util.Log.d("ImportViewModel", "找到 ${results.size} 个匹配结果: ${results.map { it.name }}")
                                    break
                                }
                            }
                            
                            if (searchResults.isEmpty()) {
                                // 搜索失败，保留该项但标记为未验证
                                android.util.Log.w("ImportViewModel", "搜索失败: $cleanName")
                                return@map ImportFundItem(
                                    ocrResult = result,
                                    verifiedName = null,
                                    shares = "",
                                    totalCost = result.amount?.let { "%.2f".format(it) } ?: ""
                                )
                            }
                            
                            // 使用第一个匹配结果
                            val match = searchResults.first()
                            val fundCode = match.code
                            
                            // 获取当前净值以计算份额
                            val valuation = repository.getValuation(fundCode)
                            val nav = valuation?.nav?.toDoubleOrNull() ?: valuation?.estimatedNav?.toDoubleOrNull()
                            
                            // 计算份额 = 金额 ÷ 净值
                            val calculatedShares = if (result.amount != null && nav != null && nav > 0) {
                                result.amount / nav
                            } else {
                                null
                            }
                            
                            android.util.Log.d("ImportViewModel", "匹配成功: ${match.name} (${fundCode}), 净值: $nav, 份额: $calculatedShares")
                            
                            ImportFundItem(
                                ocrResult = result.copy(fundCode = fundCode, shares = calculatedShares),
                                verifiedName = match.name,
                                shares = calculatedShares?.let { "%.2f".format(it) } ?: "",
                                totalCost = result.amount?.let { "%.2f".format(it) } ?: ""
                            )
                        } catch (e: Exception) {
                            // 搜索异常，保留该项
                            android.util.Log.e("ImportViewModel", "搜索异常: ${result.fundName}", e)
                            ImportFundItem(
                                ocrResult = result,
                                verifiedName = null,
                                shares = "",
                                totalCost = result.amount?.let { "%.2f".format(it) } ?: ""
                            )
                        }
                    } else {
                        // 基于代码的正常验证
                        val (verifiedName, calculatedShares) = try {
                            val valuation = repository.getValuation(result.fundCode)
                            val nav = valuation?.nav?.toDoubleOrNull() ?: valuation?.estimatedNav?.toDoubleOrNull()
                            
                            // 计算份额 = 金额 / 净值
                            val shares = if (result.amount != null && nav != null && nav > 0) {
                                result.amount / nav
                            } else {
                                null
                            }
                            
                            Pair(valuation?.name, shares)
                        } catch (_: Exception) {
                            Pair(null, null)
                        }

                        ImportFundItem(
                            ocrResult = result.copy(shares = calculatedShares),
                            verifiedName = verifiedName,
                            shares = calculatedShares?.let { "%.2f".format(it) } ?: "",
                            totalCost = result.amount?.let { "%.2f".format(it) } ?: ""
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        recognizedText = ocrResult.rawText,
                        fundItems = items
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "识别失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleSelection(index: Int) {
        _uiState.update { state ->
            val updatedItems = state.fundItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems[index] = updatedItems[index].copy(
                    isSelected = !updatedItems[index].isSelected
                )
            }
            state.copy(fundItems = updatedItems)
        }
    }

    fun updateShares(index: Int, shares: String) {
        _uiState.update { state ->
            val updatedItems = state.fundItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems[index] = updatedItems[index].copy(shares = shares)
            }
            state.copy(fundItems = updatedItems)
        }
    }

    fun updateCost(index: Int, cost: String) {
        _uiState.update { state ->
            val updatedItems = state.fundItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems[index] = updatedItems[index].copy(totalCost = cost)
            }
            state.copy(fundItems = updatedItems)
        }
    }

    fun updateFundCode(index: Int, fundCode: String) {
        _uiState.update { state ->
            val updatedItems = state.fundItems.toMutableList()
            if (index in updatedItems.indices) {
                updatedItems[index] = updatedItems[index].copy(editedFundCode = fundCode)
            }
            state.copy(fundItems = updatedItems)
        }
    }

    fun importSelected() {
        val selected = _uiState.value.fundItems.filter { it.isSelected }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(error = "请至少选择一只基金") }
            return
        }

        _uiState.update { it.copy(isImporting = true, error = null) }

        viewModelScope.launch {
            try {
                val holdings = selected.mapNotNull { item ->
                    val fundCode = item.displayFundCode
                    
                    android.util.Log.d("ImportViewModel", "正在处理: ${item.displayName}, code=${fundCode}, shares=${item.displayShares}, cost=${item.displayCost}")
                    
                    // 跳过基金代码无效的项（SEARCH_ 前缀且未手动编辑）
                    if (fundCode.startsWith("SEARCH_")) {
                        android.util.Log.w("ImportViewModel", "跳过 ${item.displayName}: 基金代码无效 $fundCode (需要手动填写或搜索失败)")
                        return@mapNotNull null
                    }
                    
                    // 验证基金代码格式（6 位数字）
                    if (!fundCode.matches(Regex("\\d{6}"))) {
                        android.util.Log.w("ImportViewModel", "跳过 ${item.displayName}: 基金代码格式错误 $fundCode")
                        return@mapNotNull null
                    }
                    
                    val shares = item.displayShares.toDoubleOrNull()
                    val cost = item.displayCost.toDoubleOrNull()
                    
                    if (shares == null || shares <= 0 || cost == null || cost <= 0) {
                        android.util.Log.w("ImportViewModel", "跳过 ${item.displayName}: 份额或金额无效 (shares=$shares, cost=$cost)")
                        return@mapNotNull null
                    }

                    android.util.Log.d("ImportViewModel", "准备导入: ${item.displayName} ($fundCode), shares=$shares, cost=$cost")
                    
                    FundHoldingEntity(
                        fundCode = fundCode,
                        fundName = item.displayName,
                        shares = shares,
                        totalCost = cost,
                        costPrice = cost / shares
                    )
                }

                if (holdings.isEmpty()) {
                    val failedCount = selected.size
                    val needManualCode = selected.count { it.displayFundCode.startsWith("SEARCH_") }
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            error = "没有可导入的有效基金（共${failedCount}个失败）。" +
                                if (needManualCode > 0) "其中${needManualCode}个需要手动填写基金代码。" else ""
                        )
                    }
                    return@launch
                }

                android.util.Log.d("ImportViewModel", "Importing ${holdings.size} holdings")

                // 更新或插入持仓（合并逻辑：替换现有或添加新项）
                holdings.forEach { newHolding ->
                    val existing = repository.getHolding(newHolding.fundCode)
                    if (existing != null) {
                        // 更新现有持仓 - 用新数据替换
                        android.util.Log.d("ImportViewModel", "Updating existing: ${newHolding.fundCode}")
                        val updated = existing.copy(
                            shares = newHolding.shares,
                            costPrice = newHolding.costPrice,
                            totalCost = newHolding.totalCost,
                            fundName = newHolding.fundName
                        )
                        repository.updateHolding(updated)
                    } else {
                        // 插入新持仓
                        android.util.Log.d("ImportViewModel", "Inserting new: ${newHolding.fundCode}")
                        repository.addHoldings(listOf(newHolding))
                    }
                }

                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importSuccess = true,
                        error = null
                    )
                }
                
                android.util.Log.d("ImportViewModel", "Import completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("ImportViewModel", "Import failed", e)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        error = "导入失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearState() {
        _uiState.value = ImportUiState()
    }
}
