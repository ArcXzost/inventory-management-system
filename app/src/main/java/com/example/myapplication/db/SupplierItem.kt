package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "supplier_items")
data class SupplierItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val supplierId: String,
    val productId: String,
    val price: Int
)