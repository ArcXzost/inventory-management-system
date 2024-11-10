package com.example.myapplication.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "shipments")
data class Shipment(
    @PrimaryKey val shipmentId: String,
    val orderId: String,
    val shipmentDate: Date,
    val status: String
)