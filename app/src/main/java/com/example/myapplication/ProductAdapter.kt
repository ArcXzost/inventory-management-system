package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
//import androidx.core.content.ContentProviderCompat.requireContext
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.InventoryDao
import com.example.myapplication.db.InventoryItem
import com.example.myapplication.utility.ColorUtility
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ProductAdapter(
    private var items: List<InventoryItem>,
    private val onEditClick: (InventoryItem) -> Unit,
    private val onDeleteClick: (InventoryItem) -> Unit,
    private var inventoryDao: InventoryDao
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
//        updateItems()
        return ViewHolder(view)
    }

//    private fun updateItems() {
//        CoroutineScope(Dispatchers.IO).launch {
//            val latestItems = inventoryDao.getAllItems()
//            withContext(Dispatchers.Main) {
//                items = latestItems
//                notifyDataSetChanged() // Notify the adapter to refresh the RecyclerView
//            }
//        }
//    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val btnEdit: View = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)
        private val image: View = itemView.findViewById(R.id.ivImage)

        fun bind(item: InventoryItem) {
            tvProductName.text = item.name
            tvQuantity.text = "${item.quantity} units"

            // Cast the image view
            val imageView: ImageView = itemView.findViewById(R.id.ivImage)

            // Check if the cast was successful
            if (item.imageUrl?.isNotEmpty() == true) {
                Picasso.get()
                    .load(item.imageUrl)
                    .placeholder(R.drawable.default_image) // Optional
                    .error(R.drawable.default_image) // Optional
//                    .resize(200, 200) // Optional (resize to 100x100 pixels)
                    .into(imageView)


                println("Image URL: ${item.imageUrl}")
            } else {
                // If no image URL is available, you can set a default image
                imageView.setImageResource(R.drawable.default_image)
                println("No Image URL available")
            }

            val progress = (item.quantity.toFloat() / item.reorderLevel * 100).toInt().coerceIn(0, 100)
            progressBar.progress = progress
            progressBar.progressTintList = ColorUtility.getProgressColorStateList(progress)


            btnEdit.setOnClickListener { onEditClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }
}
