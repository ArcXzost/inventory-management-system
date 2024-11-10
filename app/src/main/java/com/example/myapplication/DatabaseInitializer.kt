package com.example.myapplication

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.random.Random
import com.example.myapplication.db.*
import kotlinx.coroutines.flow.first

class DatabaseInitializer(context: Context) {
    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "inventory_management_db"
    ).build()

    private val customerDao = database.customerDao()
    private val orderDao = database.orderDao()
    private val shipmentDao = database.shipmentDao()
    private val categoryDao = database.categoryDao()
    private val inventoryItemDao = database.inventoryDao()
    private val orderItemDao = database.orderItemDao()
    private val orderTrackingDao = database.orderTrackingDao()

    suspend fun initializeDatabase() {
        withContext(Dispatchers.IO) {
            if (isDatabaseEmpty()) {
                insertDummyData()
            }
        }
    }

    private suspend fun isDatabaseEmpty(): Boolean {
        return customerDao.getAllCustomers().first().isEmpty()
    }

    private suspend fun insertDummyData() {
        // 1. Insert Categories with predefined names
        val categories = listOf(
            Categories("CAT1", "Electronics"),
            Categories("CAT2", "Clothing"),
            Categories("CAT3", "Books"),
            Categories("CAT4", "Home & Garden"),
            Categories("CAT5", "Sports")
        )
        categories.forEach { categoryDao.insertCategory(it) }

        // 2. Insert Inventory Items with proper category mapping
        val productNames = mapOf(
            "Electronics" to listOf("Smartphone", "Laptop", "Tablet", "Headphones", "Smart Watch", "Camera"),
            "Clothing" to listOf("T-Shirt", "Jeans", "Dress", "Jacket", "Sweater", "Shoes"),
            "Books" to listOf("Novel", "Textbook", "Comic Book", "Dictionary", "Biography", "Self-Help"),
            "Home & Garden" to listOf("Plant Pot", "Garden Tools", "Furniture", "Lamp", "Cushion", "Rug"),
            "Sports" to listOf("Basketball", "Tennis Racket", "Football", "Yoga Mat", "Weights", "Running Shoes")
        )

        // Create inventory items for each category
        categories.forEach { category ->
            val namesForCategory = productNames[category.name] ?: listOf()
            namesForCategory.forEach { productName ->
                val inventoryItem = InventoryItem(
                    productId = UUID.randomUUID().toString(),
                    name = productName,
                    sku = "SKU${Random.nextInt(1000, 9999)}",
                    category = category.name,  // Use category name instead of ID
                    quantity = Random.nextInt(5, 50),
                    reorderLevel = Random.nextInt(10, 20),
                    lastRestocked = Date(System.currentTimeMillis() - Random.nextLong(0, 30L * 24 * 60 * 60 * 1000)),
                    price = Random.nextInt(10, 1000).toFloat(),
//                    imageUrl = "https://example.com/products/${productName.lowercase().replace(" ", "_")}.jpg"
                )
                inventoryItemDao.insertItem(inventoryItem)
            }
        }

        // 3. Insert Customers
        val customers = (1..20).map { index ->
            Customer(
                customerId = "C${1000 + index}",
                name = "Customer $index",
                email = "customer$index@example.com"
            )
        }
        customers.forEach { customerDao.insertCustomer(it) }

        // 4. Insert Orders and related entities
        val orderStatuses = listOf("Pending", "Processing", "Completed", "Cancelled")
        val trackingStatuses = TrackingStatus.values()

        customers.forEach { customer ->
            // Create 1-3 orders per customer
            repeat(Random.nextInt(1, 4)) { orderIndex ->
                val orderId = "O${customer.customerId}${orderIndex}"
                val orderStatus = orderStatuses.random()
                val orderDate = Date(System.currentTimeMillis() - Random.nextLong(0, 30L * 24 * 60 * 60 * 1000))
                var approved = false
                if(orderStatus == "Pending")
                    approved = true
                // Create Order
                val order = Order(
                    orderId = orderId,
                    customerId = customer.customerId,
                    orderDate = orderDate,
                    status = orderStatus,
                    approved = approved
                )
                orderDao.insertOrder(order)

                // Create Order Items (2-5 items per order)
                val allItems = inventoryItemDao.getAllItems()
                val orderItems = (1..Random.nextInt(2, 6)).map {
                    val item = allItems.random()
                    OrderItem(
                        orderId = orderId,
                        productId = item.productId,
                        quantity = Random.nextInt(1, 5),
                        price = item.price.toInt()
                    )
                }
                orderItems.forEach { orderItemDao.insertOrderItem(it) }

                // Create Order Tracking
                val tracking = OrderTracking(
                    trackingId = "T$orderId",
                    orderId = orderId,
                    status = trackingStatuses.random(),
                    timestamp = orderDate.time,
                    description = "Order tracking for $orderId"
                )
                orderTrackingDao.insertTracking(tracking)

                // Create Shipment for non-pending orders
                if (orderStatus != "Pending") {
                    val shipment = Shipment(
                        shipmentId = "S$orderId",
                        orderId = orderId,
                        shipmentDate = Date(orderDate.time + Random.nextLong(24 * 60 * 60 * 1000)),
                        status = when (orderStatus) {
                            "Completed" -> "Delivered"
                            "Processing" -> "In Transit"
                            else -> "Cancelled"
                        }
                    )
                    shipmentDao.insertShipment(shipment)
                }
            }
        }
    }

    fun getDatabase(): AppDatabase {
        return database
    }
}