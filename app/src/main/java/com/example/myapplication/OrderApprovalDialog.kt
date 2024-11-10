package com.example.myapplication

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.TextView
import com.example.myapplication.db.AppDatabase
import kotlinx.coroutines.*

class OrderApprovalDialog(
    private val context: Context,
    private val order: OrderWithDetails,
    private val listener: OrderApprovalListener
) {
    fun showApprovalDialog() {
        val alertDialogBuilder = AlertDialog.Builder(context)
        val dialogView = LayoutInflater.from(context).inflate(R.layout.approval_dialog, null)

        dialogView.findViewById<TextView>(R.id.tvOrderDetails).text =
            "Order ID: ${order.order.orderId}\nCustomer: ${order.customer.name}"

        val dialog = alertDialogBuilder
            .setView(dialogView)
            .create()

        dialog.setOnShowListener {
            dialog.findViewById<ImageButton>(R.id.btnApproved)?.setOnClickListener {
                approveOrder(dialog)
            }

            dialog.findViewById<ImageButton>(R.id.btnCancelled)?.setOnClickListener {
                cancelOrder(dialog)
            }
        }

        dialog.show()
    }

    private fun approveOrder(dialog: AlertDialog) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)

                // Update order status in database
                database.orderDao().updateOrderApproval(order.order.orderId, true)
                database.orderDao().updateOrderApprovedStatus(order.order.orderId, "true")
                database.orderDao().updateOrderStatus(order.order.orderId, "Completed")

                // Update inventory
                for (orderItem in order.items) {
                    val currentQuantity = database.inventoryDao()
                        .getInventoryQuantityForProduct(orderItem.orderItem.productId)
                    val newQuantity = currentQuantity - orderItem.orderItem.quantity
                    database.inventoryDao()
                        .updateInventoryQuantityForProduct(orderItem.orderItem.productId, newQuantity)
                }

                withContext(Dispatchers.Main) {
                    listener.onOrderApproved(order.order.orderId, true, "Completed")
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(context)
                        .setTitle("Error")
                        .setMessage("Failed to approve order: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun cancelOrder(dialog: AlertDialog) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)

                // Update order status in database
                database.orderDao().updateOrderApproval(order.order.orderId, false)
                database.orderDao().updateOrderApprovedStatus(order.order.orderId, "false")
                database.orderDao().updateOrderStatus(order.order.orderId, "Cancelled")

                withContext(Dispatchers.Main) {
                    listener.onOrderCancelled(order.order.orderId, false, "Cancelled")
                    dialog.dismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(context)
                        .setTitle("Error")
                        .setMessage("Failed to cancel order: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
}
