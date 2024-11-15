package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_demand")
data class CategoryDemand(
    @PrimaryKey val categoryName: String,
    val avgDemand: Double,
    val demandStdDev: Double
)
