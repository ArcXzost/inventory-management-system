package com.example.myapplication.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DemandHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDemandHistory(demandHistory: DemandHistory)

    @Query("SELECT * FROM demand_history WHERE productId = :productId ORDER BY demandDate DESC")
    fun getDemandHistoryForProduct(productId: String): Flow<List<DemandHistory>>

    @Query("SELECT * FROM demand_history WHERE productId = :productId ORDER BY demandDate DESC LIMIT :limit")
    fun getRecentDemands(productId: String, limit: Int): List<DemandHistory>

}