package com.example.myapplication

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.random.Random
import com.example.myapplication.db.*
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

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
    private val categoryDemandDao = database.categoryDemandDao()
    private val demandHistoryDao = database.demandHistoryDao()

    private val categorySeasonality = mapOf(
        "Electronics" to SeasonalPattern(
            baselineDemand = 30,
            seasonalAmplitude = 15,
            peakMonth = 11  // Peak in December (Black Friday/Christmas)
        ),
        "Clothing" to SeasonalPattern(
            baselineDemand = 25,
            seasonalAmplitude = 10,
            peakMonth = 8   // Peak in September (Fall season)
        ),
        "Books" to SeasonalPattern(
            baselineDemand = 20,
            seasonalAmplitude = 8,
            peakMonth = 7   // Peak in August (Back to school)
        ),
        "Home & Garden" to SeasonalPattern(
            baselineDemand = 15,
            seasonalAmplitude = 12,
            peakMonth = 4   // Peak in May (Spring season)
        ),
        "Sports" to SeasonalPattern(
            baselineDemand = 22,
            seasonalAmplitude = 10,
            peakMonth = 5   // Peak in June (Summer season)
        )
    )

    data class SeasonalPattern(
        val baselineDemand: Int,
        val seasonalAmplitude: Int,
        val peakMonth: Int
    )


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

    private suspend fun generateDemandHistory() {
        val calendar = Calendar.getInstance()
        val currentDate = calendar.time

        // Get all inventory items
        val items = inventoryItemDao.getAllItems()

        // Generate 365 days of history
        items.forEach { item ->
            calendar.time = currentDate
            calendar.add(Calendar.DAY_OF_YEAR, -365) // Start from one year ago

            val seasonalPattern = categorySeasonality[item.category]
                ?: categorySeasonality["Electronics"]!! // Default pattern

            repeat(365) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val demand = calculateDailyDemand(
                    calendar.time,
                    seasonalPattern,
                    item.category
                )

                val demandHistory = DemandHistory(
                    productId = item.productId,
                    demandDate = calendar.time,
                    quantity = demand
                )
                demandHistoryDao.insertDemandHistory(demandHistory)
            }
        }
    }

    private fun calculateDailyDemand(
        date: Date,
        pattern: SeasonalPattern,
        category: String
    ): Int {
        val calendar = Calendar.getInstance().apply { time = date }

        // Calculate seasonal component
        val monthPosition = calendar.get(Calendar.MONTH)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Calculate distance from peak month (in radians)
        val monthDistance = (monthPosition - pattern.peakMonth + 12) % 12
        val seasonalFactor = sin(monthDistance * (2 * PI / 12))

        // Base demand with seasonal variation
        var demand = pattern.baselineDemand +
                (pattern.seasonalAmplitude * seasonalFactor).toInt()

        // Add day-of-week variation
        demand += when (dayOfWeek) {
            Calendar.SATURDAY, Calendar.SUNDAY -> -5  // Lower demand on weekends
            Calendar.FRIDAY -> 5               // Higher demand on Fridays
            else -> 0
        }

        // Add category-specific variations
        demand += when (category) {
            "Electronics" -> if (calendar.get(Calendar.MONTH) == 10) 15 else 0  // Black Friday month
            "Clothing" -> if (calendar.get(Calendar.MONTH) in 7..8) 10 else 0   // Back to school
            "Books" -> if (calendar.get(Calendar.MONTH) in 7..8) 12 else 0      // Academic year start
            else -> 0
        }

        // Add random noise (-20% to +20%)
        val randomNoise = (demand * (Random.nextDouble(-0.2, 0.2))).toInt()
        demand = demand + randomNoise

        // Ensure demand is not negative
        return maxOf(0, demand)
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

        val categoryDemandData = listOf(
            CategoryDemand("Electronics", avgDemand = 500.0, demandStdDev = 50.0),
            CategoryDemand("Clothing", avgDemand = 400.0, demandStdDev = 60.0),
            CategoryDemand("Books", avgDemand = 300.0, demandStdDev = 40.0),
            CategoryDemand("Home & Garden", avgDemand = 200.0, demandStdDev = 30.0),
            CategoryDemand("Sports", avgDemand = 350.0, demandStdDev = 45.0)
        )
        categoryDemandData.forEach { categoryDemandDao.insertCategoryDemand(it) }

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