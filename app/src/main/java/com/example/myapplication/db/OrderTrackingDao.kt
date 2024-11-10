package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderTrackingDao {
    @Query("SELECT * FROM order_tracking WHERE orderId = :orderId ORDER BY timestamp ASC")
    fun getOrderTracking(orderId: String): List<OrderTracking>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracking(tracking: OrderTracking)

    @Query("SELECT * FROM order_tracking WHERE orderId = :orderId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTrackingStatus(orderId: String): OrderTracking?
}
