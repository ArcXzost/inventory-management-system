package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDemandDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryDemand(demand: CategoryDemand)

    @Query("SELECT * FROM category_demand WHERE categoryName = :categoryName")
    suspend fun getCategoryDemand(categoryName: String): CategoryDemand

    @Query("SELECT * FROM category_demand")
    fun getAllCategoryDemand(): List<CategoryDemand>
}
