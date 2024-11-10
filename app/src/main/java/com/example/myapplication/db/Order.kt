package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey val orderId: String,
    val customerId: String,
    val orderDate: Date,
    val status: String,
    val approved: Boolean
)