package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "restock_history")
data class RestockHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: String,
    val restockDate: Date,
    val quantity: Int
)