package com.example.myapplication.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShipmentDao {

    @Query("SELECT * FROM shipments WHERE orderId = :orderId")
    suspend fun getShipmentForOrder(orderId: String): Shipment?

    @Query("SELECT * FROM shipments")
    fun getAllShipments(): Flow<List<Shipment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShipment(shipment: Shipment)

    @Update
    suspend fun updateShipment(shipment: Shipment)

    @Delete
    suspend fun deleteShipment(shipment: Shipment)
}
