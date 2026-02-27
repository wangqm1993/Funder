package com.example.funder.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FundDao {

    @Query("SELECT * FROM fund_holdings ORDER BY sortOrder ASC, createTime DESC")
    fun getAllHoldings(): Flow<List<FundHoldingEntity>>

    @Query("SELECT * FROM fund_holdings WHERE fundCode = :code")
    suspend fun getHolding(code: String): FundHoldingEntity?

    @Query("UPDATE fund_holdings SET sortOrder = :order WHERE fundCode = :code")
    suspend fun updateSortOrder(code: String, order: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: FundHoldingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHoldings(holdings: List<FundHoldingEntity>)

    @Update
    suspend fun updateHolding(holding: FundHoldingEntity)

    @Delete
    suspend fun deleteHolding(holding: FundHoldingEntity)

    @Query("DELETE FROM fund_holdings WHERE fundCode = :code")
    suspend fun deleteByCode(code: String)

    @Query("SELECT COUNT(*) FROM fund_holdings")
    suspend fun getCount(): Int

    @Query("SELECT * FROM fund_holdings ORDER BY sortOrder ASC, createTime DESC")
    suspend fun getAllHoldingsSync(): List<FundHoldingEntity>
}
