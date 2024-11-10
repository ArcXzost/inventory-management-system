package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouse")
    suspend fun getWarehouse(): Warehouse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateWarehouse(warehouse: Warehouse)
}