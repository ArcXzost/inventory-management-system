package com.example.myapplication.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myapplication.db.Converters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [
        InventoryItem::class, RestockHistory::class, DemandHistory::class,
        Order::class, OrderItem::class, Shipment::class,
        Supplier::class, SupplierItem::class, Customer::class,
        Categories::class, Warehouse::class, OrderTracking::class,
    ],
    version = 9
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    abstract fun orderDao(): OrderDao
    abstract fun categoryDao(): CategoryDao
    abstract fun warehouseDao(): WarehouseDao
    abstract fun customerDao(): CustomerDao
    abstract fun shipmentDao(): ShipmentDao
    abstract fun orderItemDao(): OrderItemDao
    abstract fun orderTrackingDao(): OrderTrackingDao

    companion object {
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            if (instance == null) {
                instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventory_management_db"
                ).fallbackToDestructiveMigration().build()
            }
            return instance!!
        }
    }
}
