package com.example.myapplication.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders")
    fun getAllOrders(): List<Order>

    @Query("SELECT * FROM orders WHERE status = :status")
    fun getOrdersByStatus(status: String): Flow<List<Order>>

    @Transaction
    @Query("SELECT * FROM orders WHERE orderId = :orderId")
    fun getOrderById(orderId: String): Order

    @Transaction
    @Query("SELECT * FROM orders WHERE orderDate >= :startDate")
    fun getOrdersForDate(startDate: Date): List<Order>

    @Transaction
    @Query("SELECT * FROM orders")
    fun getAllOrdersWithDetails(): Flow<List<Order>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order)

    @Update
    suspend fun updateOrder(order: Order)

    @Delete
    suspend fun deleteOrder(order: Order)

    // OrderDao
    @Query("UPDATE orders SET approved = :approved WHERE orderId = :orderId")
    fun updateOrderApproval(orderId: String, approved: Boolean)

    @Query("UPDATE orders SET status = :status WHERE orderId = :orderId")
    fun updateOrderStatus(orderId: String, status: String)

    @Query("UPDATE orders SET approved = :status WHERE orderId = :orderId")
    fun updateOrderApprovedStatus(orderId: String, status: String)
}

