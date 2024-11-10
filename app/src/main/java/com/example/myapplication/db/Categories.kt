package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Categories(
    @PrimaryKey val categoryId: String,
    val name: String
)
