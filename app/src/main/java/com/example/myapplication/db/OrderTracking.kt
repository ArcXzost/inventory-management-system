package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.myapplication.TrackingStatus

@Entity(tableName = "order_tracking")
data class OrderTracking(
    @PrimaryKey val trackingId: String,
    val orderId: String,
    val status: TrackingStatus,
    val timestamp: Long,
    val description: String?
)