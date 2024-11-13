package com.example.myapplication.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import java.util.Date

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items")
    fun getAllItems(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE category = :category")
    fun getItemsByCategory(category: String): List<InventoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: InventoryItem)

    @Update
    fun updateItem(item: InventoryItem)

    @Delete
    fun deleteItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE category = :categoryName")
    suspend fun deleteProductsByCategory(categoryName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addProduct(item: InventoryItem)

    @Query("UPDATE inventory_items SET quantity = :newQuantity WHERE productId = :productId")
    fun updateProductQuantity(productId: String, newQuantity: Int)

    @Query("DELETE FROM inventory_items WHERE productId = :productId")
    fun deleteProduct(productId: String)


    @Query("UPDATE inventory_items SET reorderLevel = :newReorderLevel WHERE productId = :productId")
    fun updateProductReorderLevel(productId: String, newReorderLevel: Int)

    @Query("UPDATE inventory_items SET price = :newPrice WHERE productId = :productId")
    fun updateProductPrice(productId: String, newPrice: Float)

    // InventoryItemDao
    @Query("SELECT quantity FROM inventory_items WHERE productId = :productId")
    fun getInventoryQuantityForProduct(productId: String): Int

    @Query("UPDATE inventory_items SET imageUrl = :newImageUri WHERE productId = :productId")
    suspend fun updateProductImage(productId: String, newImageUri: String)

    @Query("UPDATE inventory_items SET name = :newName WHERE productId = :productId")
    suspend fun updateProductName(productId: String, newName: String)

}