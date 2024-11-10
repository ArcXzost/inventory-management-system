package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "warehouse")
data class Warehouse (
    @PrimaryKey val warehouseLimit: Long
)