package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "demand_history")
data class DemandHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: String,
    val demandDate: Date,
    val quantity: Int
)