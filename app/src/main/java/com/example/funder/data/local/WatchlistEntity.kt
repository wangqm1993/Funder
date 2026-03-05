package com.example.funder.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val fundCode: String,
    val fundName: String,
    val createTime: Long = System.currentTimeMillis()
)
