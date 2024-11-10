package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey val supplierId: String,
    val name: String,
    val contact: String
)