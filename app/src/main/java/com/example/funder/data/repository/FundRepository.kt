package com.example.funder.data.repository

import com.example.funder.data.local.FundDao
import com.example.funder.data.local.FundHoldingEntity
import com.example.funder.data.remote.FundApiService
import com.example.funder.data.remote.FundDetailDto
import com.example.funder.data.remote.FundSearchResultDto
import com.example.funder.data.remote.FundValuationDto
import com.example.funder.data.remote.NavHistoryItem
import com.example.funder.data.remote.NewsDto
import com.example.funder.data.remote.StockHolding
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FundRepository @Inject constructor(
    private val fundDao: FundDao,
    private val apiService: FundApiService
) {
    // ---- 本地持仓 ----

    fun getAllHoldings(): Flow<List<FundHoldingEntity>> = fundDao.getAllHoldings()

    suspend fun getHolding(code: String): FundHoldingEntity? = fundDao.getHolding(code)

    suspend fun addHolding(holding: FundHoldingEntity) = fundDao.insertHolding(holding)

    suspend fun addHoldings(holdings: List<FundHoldingEntity>) = fundDao.insertHoldings(holdings)

    suspend fun updateHolding(holding: FundHoldingEntity) = fundDao.updateHolding(holding)

    suspend fun deleteHolding(code: String) = fundDao.deleteByCode(code)

    suspend fun updateSortOrders(codeOrderPairs: List<Pair<String, Int>>) {
        codeOrderPairs.forEach { (code, order) ->
            fundDao.updateSortOrder(code, order)
        }
    }

    // ---- 远程估值 ----

    suspend fun getValuation(fundCode: String): FundValuationDto? =
        apiService.getValuation(fundCode)

    suspend fun getValuations(fundCodes: List<String>): Map<String, FundValuationDto> =
        apiService.getValuations(fundCodes)

    // ---- 详情 ----

    suspend fun getFundDetail(fundCode: String): FundDetailDto? =
        apiService.getFundDetail(fundCode)

    suspend fun getNavHistory(fundCode: String, page: Int = 1, perPage: Int = 20): List<NavHistoryItem> =
        apiService.getNavHistory(fundCode, page, perPage)

    suspend fun getLatestNavDate(fundCode: String): String? =
        apiService.getNavHistory(fundCode, page = 1, perPage = 1).firstOrNull()?.date

    suspend fun getStockPrices(codes: List<String>): Map<String, Pair<String, String>> =
        apiService.getStockPrices(codes)

    suspend fun getDetailedHoldings(fundCode: String): Pair<String, List<StockHolding>> =
        apiService.getDetailedHoldings(fundCode)

    suspend fun getFundBasicInfo(fundCode: String): Map<String, String> =
        apiService.getFundBasicInfo(fundCode)

    // ---- 搜索 ----

    suspend fun searchFund(keyword: String): List<FundSearchResultDto> =
        apiService.searchFund(keyword)

    // ---- 新闻 ----

    suspend fun getNews(page: Int = 1, pageSize: Int = 20): List<NewsDto> =
        apiService.getNews(page, pageSize)
}
