package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): List<Categories>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategory(category: Categories)

    @Query("SELECT * FROM categories WHERE name = :name")
    fun getCategoryByName(name: String): Categories?

    @Query("DELETE FROM categories WHERE name = :name")
    fun deleteCategory(name: String)

}