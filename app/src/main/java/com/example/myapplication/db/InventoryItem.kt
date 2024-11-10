package com.example.myapplication.db

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey val productId: String,
    val name: String,
    val sku: String,
    val category: String,
    val quantity: Int,
    val reorderLevel: Int,
    val lastRestocked: Date = Date(),
    val price: Float,
    val imageUrl: String? = null
): Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readInt(),
        Date(parcel.readLong()),
        parcel.readFloat(),
        parcel.readString()!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(productId)
        parcel.writeString(name)
        parcel.writeString(sku)
        parcel.writeString(category)
        parcel.writeInt(quantity)
        parcel.writeInt(reorderLevel)
        parcel.writeLong(lastRestocked.time)
        parcel.writeFloat(price)
        parcel.writeString(imageUrl)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<InventoryItem> {
        override fun createFromParcel(parcel: Parcel): InventoryItem {
            return InventoryItem(parcel)
        }

        override fun newArray(size: Int): Array<InventoryItem?> {
            return arrayOfNulls(size)
        }
    }
}



