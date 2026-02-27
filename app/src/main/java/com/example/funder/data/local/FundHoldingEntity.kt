package com.example.funder.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fund_holdings")
data class FundHoldingEntity(
    @PrimaryKey
    val fundCode: String,
    val fundName: String,
    val shares: Double,
    val costPrice: Double,
    val totalCost: Double,
    val createTime: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0
)
