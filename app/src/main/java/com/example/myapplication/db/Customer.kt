package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val customerId: String,
    val name: String,
    val email: String
)