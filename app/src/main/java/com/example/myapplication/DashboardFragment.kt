package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.example.myapplication.db.InventoryItem
import com.example.myapplication.db.InventoryDao
import com.example.myapplication.db.Order
import com.example.myapplication.db.OrderDao
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.Categories
import com.example.myapplication.db.CategoryDao
import com.example.myapplication.db.Warehouse
import com.example.myapplication.db.WarehouseDao
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.async


class DashboardFragment : Fragment(), ProductsOverlayFragment.ProductUpdateListener {
    private lateinit var chartCostProfit: LineChart
    private lateinit var chartInventorySummary: PieChart
    private lateinit var rvInventoryCategories: RecyclerView
    private lateinit var warehouseCapacityIndicator: LinearProgressIndicator
    private lateinit var tvWarehouseCapacity: TextView
    private lateinit var tvTotalSales: TextView
    private lateinit var tvPendingOrders: TextView
    private var currentTotalItems = 0L
    private var currentWarehouseLimit: Long = 10000 // Default value


    private lateinit var database: AppDatabase
    private lateinit var inventoryDao: InventoryDao
    private lateinit var orderDao: OrderDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var warehouseDao: WarehouseDao

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupDatabase()
        setupAddCategoryButton()
        setupWarehouseButton()
        fetchDataAndUpdateUI()
    }

    private fun initViews(view: View) {
//        searchView = view.findViewById(R.id.searchView)
        chartCostProfit = view.findViewById(R.id.chartCostProfit)
        chartInventorySummary = view.findViewById(R.id.chartInventorySummary)
        rvInventoryCategories = view.findViewById(R.id.rvInventoryCategories)
        warehouseCapacityIndicator = view.findViewById(R.id.warehouseCapacityIndicator)
        tvWarehouseCapacity = view.findViewById(R.id.tvWarehouseCapacity)
        tvTotalSales = view.findViewById(R.id.tvTotalSales)
        tvPendingOrders = view.findViewById(R.id.tvPendingOrders)
    }

    private fun setupDatabase() {
        database = AppDatabase.getInstance(requireContext())
        inventoryDao = database.inventoryDao()
        orderDao = database.orderDao()
        categoryDao = database.categoryDao()
        warehouseDao = database.warehouseDao()
    }


    private fun performSearch(query: String?) {
        // Implement search functionality using Room
        // This would typically involve querying the database based on the search term
    }

    private fun setupWarehouseButton() {
        view?.findViewById<View>(R.id.btnWarehouse)?.setOnClickListener {
            showWarehouseLimitDialog()
        }
    }

    private fun updateWarehouseLimit(newLimit: Long) {
        lifecycleScope.launch {
            // First check if new limit is less than current inventory
            val currentInventory = withContext(Dispatchers.IO) {
                inventoryDao.getAllItems().sumOf { it.quantity }.toLong()
            }

            if (newLimit < currentInventory) {
                MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
                    .setTitle("Invalid Warehouse Limit")
                    .setMessage("Cannot set warehouse limit to $newLimit as current inventory ($currentInventory items) exceeds this limit.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            withContext(Dispatchers.IO) {
                // Update warehouse in database
                val warehouse = Warehouse(warehouseLimit = newLimit)
                warehouseDao.insertOrUpdateWarehouse(warehouse)
                currentTotalItems = inventoryDao.getAllItems().sumOf { it.quantity }.toLong()
            }

            // Update the current warehouse limit
            currentWarehouseLimit = newLimit
            warehouseCapacityIndicator.progress = 0
            // Update UI with current inventory
            updateWarehouseCapacity(currentTotalItems)
        }
    }

    private fun updateWarehouseCapacity(totalItems: Long) {
        currentTotalItems = totalItems

        // Calculate capacity percentage based on current warehouse limit
        val capacity = (currentTotalItems.toFloat() / currentWarehouseLimit * 100).toInt().coerceIn(0, 100)

        // Update the TextView first
        tvWarehouseCapacity.text = "$capacity% Occupied ($currentTotalItems/${currentWarehouseLimit})"

        // Use post to ensure UI update happens after the current frame
        warehouseCapacityIndicator.post {
            // Animate to the new progress value
            warehouseCapacityIndicator.setProgress(capacity, true)
        }
    }

    override suspend fun checkWarehouseCapacity(additionalItems: Int): Boolean {
        // Get fresh data from database to ensure accuracy
        val currentInventory = withContext(Dispatchers.IO) {
            inventoryDao.getAllItems().sumOf { it.quantity }.toLong()
        }
        currentTotalItems = currentInventory  // Update cached value

        val totalAfterAdd = currentTotalItems + additionalItems

        if (totalAfterAdd > currentWarehouseLimit) {
            withContext(Dispatchers.Main) {
                val remainingCapacity = currentWarehouseLimit - currentTotalItems
                MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
                    .setTitle("Warehouse Capacity Exceeded")
                    .setMessage("Cannot add $additionalItems items. Available capacity: $remainingCapacity items")
                    .setPositiveButton("OK", null)
                    .show()
            }
            return false
        }
        return true
    }

    fun fetchDataAndUpdateUI() {
        lifecycleScope.launch {
            // Load all data concurrently
            val warehouseDeferred = async(Dispatchers.IO) { warehouseDao.getWarehouse() }
            val inventoryItemsDeferred = async(Dispatchers.IO) { inventoryDao.getAllItems() }
            val ordersDeferred = async(Dispatchers.IO) { orderDao.getAllOrders() }
            val categoriesDeferred = async(Dispatchers.IO) { categoryDao.getAllCategories() }

            // Wait for all data to be loaded
            val warehouse = warehouseDeferred.await()
            val inventoryItems = inventoryItemsDeferred.await()
            val orders = ordersDeferred.await()
            val categories = categoriesDeferred.await()

            // Update warehouse limit and current total items
            currentWarehouseLimit = warehouse?.warehouseLimit ?: 10000
            currentTotalItems = inventoryItems.sumOf { it.quantity }.toLong()

            // Update UI with current data
            updateWarehouseCapacity(currentTotalItems)
            updateCharts(inventoryItems, orders)
            updateInventoryCategories(categories, inventoryItems)
            updateKPIs(orders)
        }
    }

    private fun showWarehouseLimitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val etWarehouseLimit = dialogView.findViewById<TextInputEditText>(R.id.etProductName)
        val etProductName = dialogView.findViewById<TextInputEditText>(R.id.etQuantity)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.etPrice)
        val image = dialogView.findViewById<View>(R.id.btnUploadImage)
        etPrice.visibility = View.GONE
        image.visibility = View.GONE
        etProductName.visibility = View.GONE
        val etReorderLevel = dialogView.findViewById<TextInputEditText>(R.id.etReorderLevel)
        etReorderLevel.visibility = View.GONE
        etWarehouseLimit.setText(currentWarehouseLimit.toString())
        etWarehouseLimit.hint = "Enter new warehouse limit"

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
            .setTitle("Update Warehouse Limit")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val newLimit = etWarehouseLimit.text.toString().toLongOrNull()
                if (newLimit != null && newLimit > 0) {
                    updateWarehouseLimit(newLimit)
                } else {
                    MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
                        .setTitle("Invalid Input")
                        .setMessage("Please enter a valid positive number")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onProductsUpdated() {
        fetchDataAndUpdateUI()
    }

    private fun updateCharts(inventoryItems: List<InventoryItem>, orders: List<Order>) {
        setupCostProfitChart(orders)
        setupInventorySummaryChart(inventoryItems)
    }

    private fun setupCostProfitChart(orders: List<Order>) {
        val dateFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val currentDate = Calendar.getInstance()
        val sixMonthsAgo = Calendar.getInstance().apply { add(Calendar.MONTH, -5) }

        val monthlyData = mutableMapOf<String, Pair<Float, Float>>()

        while (sixMonthsAgo <= currentDate) {
            val month = dateFormat.format(sixMonthsAgo.time)
            monthlyData[month] = Pair(0f, 0f)
            sixMonthsAgo.add(Calendar.MONTH, 1)
        }

        orders.forEach { order ->
            val month = dateFormat.format(order.orderDate)
            if (monthlyData.containsKey(month)) {
                val (cost, profit) = monthlyData[month]!!
                monthlyData[month] = Pair(cost + 100f, profit + 150f)
            }
        }

        val costEntries = monthlyData.values.mapIndexed { index, pair -> Entry(index.toFloat(), pair.first) }
        val profitEntries = monthlyData.values.mapIndexed { index, pair -> Entry(index.toFloat(), pair.second) }

        val costDataSet = LineDataSet(costEntries, "Cost").apply {
            color = Color.RED
            setDrawCircles(false)
            setDrawValues(false)
        }

        val profitDataSet = LineDataSet(profitEntries, "Profit").apply {
            color = Color.GREEN
            setDrawCircles(false)
            setDrawValues(false)
        }

        chartCostProfit.apply {
            data = LineData(costDataSet, profitDataSet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(monthlyData.keys.toTypedArray())
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisRight.isEnabled = false
            animateX(1000)
            invalidate()
        }

        chartCostProfit.axisLeft.textColor = Color.WHITE
        chartCostProfit.xAxis.textColor = Color.WHITE
        chartCostProfit.legend.textColor = Color.WHITE
        chartCostProfit.description.textColor = Color.WHITE
    }

    private fun setupInventorySummaryChart(inventoryItems: List<InventoryItem>) {
        val categorySummary = inventoryItems.groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }

        val entries = categorySummary.map { (category, quantity) ->
            PieEntry(quantity.toFloat(), category)
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW)
            setDrawValues(true)
        }

        chartInventorySummary.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            legend.isEnabled = true
            setDrawEntryLabels(false)
            animateY(1000)
            invalidate()
        }

        chartInventorySummary.legend.textColor = Color.WHITE
    }

    private fun showDeleteConfirmationDialog(category: InventoryCategory) {
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete the category '${category.name}' and all its products?")
            .setPositiveButton("Delete") { _, _ ->
                deleteCategory(category)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCategory(category: InventoryCategory) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Delete all products in the category
                category.items.forEach { product ->
                    inventoryDao.deleteProduct(product.productId)
                }
                // Delete the category itself
                categoryDao.deleteCategory(category.name)
            }
            fetchDataAndUpdateUI() // Refresh the UI after deletion
        }
    }

    private fun updateInventoryCategories(categories: List<Categories>, inventoryItems: List<InventoryItem>) {
        val categorySummary = categories.map { category ->
            val itemsInCategory = inventoryItems.filter { it.category == category.name }
            InventoryCategory(
                name = category.name,
                totalQuantity = itemsInCategory.sumOf { it.quantity },
                reorderLevel = itemsInCategory.sumOf { it.reorderLevel },
                items = itemsInCategory
            )
        }

        rvInventoryCategories.layoutManager = LinearLayoutManager(requireContext())
        rvInventoryCategories.adapter = InventoryCategoryAdapter(categorySummary,
            onCategoryClick = { category -> showProductsOverlay(category) },
            onDeleteProductClick = { category -> showDeleteConfirmationDialog(category) }
        )
    }

    private fun updateKPIs(orders: List<Order>) {
        val totalSales = orders.size * 100 // Assuming $100 per order for simplicity
        tvTotalSales.text = "$$totalSales"

        val pendingOrders = orders.count { it.status == "Pending" }
        tvPendingOrders.text = pendingOrders.toString()
    }

    private fun showProductsOverlay(category: InventoryCategory) {
        val productsDialog = ProductsOverlayFragment.newInstance(category)
        productsDialog.setProductUpdateListener(this)
        productsDialog.show(childFragmentManager, "ProductsOverlay")
    }

    private fun setupAddCategoryButton() {
        view?.findViewById<View>(R.id.btnInventory)?.setOnClickListener {
            showAddCategoryDialog()
        }
    }

    private fun showAddCategoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)

        // Get references to the input fields
        val etProductName = dialogView.findViewById<TextInputEditText>(R.id.etProductName)
        val etQuantity = dialogView.findViewById<TextInputEditText>(R.id.etQuantity)
        val etReorderLevel = dialogView.findViewById<TextInputEditText>(R.id.etReorderLevel)
        val etPrice = dialogView.findViewById<TextInputEditText>(R.id.etPrice)
        val btnUploadImage = dialogView.findViewById<View>(R.id.btnUploadImage)

        // Hide quantity and reorder level fields
        etQuantity.visibility = View.GONE
        etReorderLevel.visibility = View.GONE
        etPrice.visibility = View.GONE
        btnUploadImage.visibility = View.GONE
        etProductName.hint = "Enter Category Name"

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialog)
            .setTitle("Add New Category")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val categoryName = etProductName.text.toString()
                if (categoryName.isNotBlank()) {
                    addCategoryToDatabase(categoryName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCategoryToDatabase(categoryName: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val categoryId = UUID.randomUUID().toString()
                val newCategory = Categories(categoryId, categoryName)
                categoryDao.insertCategory(newCategory)
            }
            fetchDataAndUpdateUI()
        }
    }
}



