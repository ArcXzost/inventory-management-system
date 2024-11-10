package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.utility.ColorUtility

class InventoryCategoryAdapter(
    private val categories: List<InventoryCategory>,
    private val onCategoryClick: (InventoryCategory) -> Unit,
    private val onDeleteProductClick: (InventoryCategory) -> Unit
) : RecyclerView.Adapter<InventoryCategoryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount() = categories.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCategoryName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(category: InventoryCategory) {
            tvCategoryName.text = category.name
            tvQuantity.text = "${category.totalQuantity} units"
            val progress = (category.totalQuantity.toFloat() / category.reorderLevel * 100).toInt().coerceIn(0, 100)
            progressBar.progress = progress
            progressBar.progressTintList = ColorUtility.getProgressColorStateList(progress)

            itemView.setOnClickListener { onCategoryClick(category) }
            btnDelete.setOnClickListener { onDeleteProductClick(category) }
        }
    }
}
