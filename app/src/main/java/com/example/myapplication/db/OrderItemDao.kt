package com.example.myapplication.db
import androidx.room.*
import com.example.myapplication.OrderItemWithProduct

@Dao
interface OrderItemDao {

    @Transaction
    @Query("""
    SELECT 
        order_items.*, 
        inventory_items.productId AS product_productId, 
        inventory_items.name AS product_name, 
        inventory_items.sku AS product_sku, 
        inventory_items.category AS product_category, 
        inventory_items.quantity AS product_quantity, 
        inventory_items.reorderLevel AS product_reorderLevel, 
        inventory_items.lastRestocked AS product_lastRestocked,
        inventory_items.imageUrl AS product_imageUrl,
        inventory_items.price AS product_price
    FROM order_items 
    INNER JOIN inventory_items ON order_items.productId = inventory_items.productId
    WHERE order_items.orderId = :orderId
""")
    suspend fun getOrderItemsWithProducts(orderId: String): List<OrderItemWithProduct>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getItemsByOrderId(orderId: String): List<OrderItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(orderItem: OrderItem)

    @Update
    suspend fun updateOrderItem(orderItem: OrderItem)

    @Delete
    suspend fun deleteOrderItem(orderItem: OrderItem)
}
