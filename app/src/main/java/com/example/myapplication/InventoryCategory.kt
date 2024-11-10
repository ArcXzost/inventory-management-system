package com.example.myapplication

import com.example.myapplication.db.InventoryItem


data class InventoryCategory(
    val name: String,
    val totalQuantity: Int,
    val reorderLevel: Int,
    val items: List<InventoryItem>
)