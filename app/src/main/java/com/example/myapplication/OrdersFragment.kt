package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Embedded
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.Customer
import com.example.myapplication.db.InventoryItem
import com.example.myapplication.db.Order
import com.example.myapplication.db.OrderItem
import com.example.myapplication.db.Shipment
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// Data classes for UI representation
data class OrderWithDetails(
    val order: Order,
    val customer: Customer,
    val items: List<OrderItemWithProduct>,
    val shipment: Shipment?
)
data class OrderItemWithProduct(
    @Embedded val orderItem: OrderItem,
    @Embedded(prefix = "product_") val product: InventoryItem
)

// View Model
class OrdersViewModel(private val database: AppDatabase) : ViewModel() {
    private val _ordersList = MutableLiveData<List<OrderWithDetails>?>()
    val ordersList: MutableLiveData<List<OrderWithDetails>?> = _ordersList

    private val _todayStats = MutableLiveData<OrderStats>()
    val todayStats: LiveData<OrderStats> = _todayStats

    init {
        loadOrders()
        loadTodayStats()
    }

    private fun loadOrders() = viewModelScope.launch(Dispatchers.IO) {
        combine(
            database.orderDao().getAllOrdersWithDetails(),
            database.customerDao().getAllCustomers(),
            database.shipmentDao().getAllShipments()
        ) { orders, customers, shipments ->
            println("Debug: Found ${orders.size} orders")
            println("Debug: Found ${customers.size} customers")
            println("Debug: Found ${shipments.size} shipments")

            // Create lookup maps for faster access
            val customerMap = customers.associateBy { it.customerId }
            val shipmentMap = shipments.associateBy { it.orderId }

            orders.mapNotNull { order ->
                println("Debug: Processing order ${order.orderId} with customer ID ${order.customerId}")
                val customer = customerMap[order.customerId]
                if (customer == null) {
                    println("Debug: Skipping order ${order.orderId} - customer ${order.customerId} not found")
                }

                val shipment = shipmentMap[order.orderId]
                val items = database.orderItemDao().getOrderItemsWithProducts(order.orderId)
                items.forEach { item ->
                    println("Debug: Processing order ${item.orderItem.orderId} with customer ID ${item.product.productId}")
                }


                customer?.let {
                    OrderWithDetails(
                        order = order,
                        customer = it,
                        items = items,
                        shipment = shipment
                    )
                }
            }
        }.collect { ordersWithDetails ->
            println("Debug: Final processed orders count: ${ordersWithDetails.size}")
//            withContext(Dispatchers.Main) {
//                _ordersList.value = ordersWithDetails
//            }
        }
    }

    private fun loadTodayStats() = viewModelScope.launch(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val todayStart = calendar.time

        val todayOrders = database.orderDao().getOrdersForDate(todayStart)
        val yesterdayOrders = database.orderDao().getOrdersForDate(
            Date(todayStart.time - 24 * 60 * 60 * 1000)
        )

        val todayRevenue = calculateRevenue(todayOrders)
        val yesterdayRevenue = calculateRevenue(yesterdayOrders)

        // Update LiveData on main thread
        withContext(Dispatchers.Main) {
            _todayStats.value = OrderStats(
                ordersCount = todayOrders.size,
                orderChange = calculatePercentageChange(
                    yesterdayOrders.size.toFloat(),
                    todayOrders.size.toFloat()
                ),
                revenue = todayRevenue,
                revenueChange = calculatePercentageChange(yesterdayRevenue, todayRevenue)
            )
        }
    }

    private suspend fun calculateRevenue(orders: List<Order>): Float {
        var total = 0f
        for (order in orders) {
            val items = withContext(Dispatchers.IO) {
                database.orderItemDao().getOrderItemsWithProducts(order.orderId)
            }
            items.forEach { item ->
                total += item.product.price * item.orderItem.quantity
            }
        }
        return total
    }

    fun searchOrders(query: String) = viewModelScope.launch(Dispatchers.IO) {
        val searchResult = _ordersList.value?.filter { orderWithDetails ->
            orderWithDetails.order.orderId.contains(query, ignoreCase = true) ||
                    orderWithDetails.customer.name.contains(query, ignoreCase = true)
        }

        withContext(Dispatchers.Main) {
            _ordersList.value = searchResult
        }
    }

    private fun calculatePercentageChange(old: Float, new: Float): Float {
        return if (old == 0f) 0f else ((new - old) / old) * 100
    }


}

// RecyclerView Adapter
class OrdersAdapter : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {
    private var orders: List<OrderWithDetails> = emptyList()
    private var onTrackClickListener: ((String) -> Unit)? = null
    private var approvalListener: OrderApprovalListener? = null  // Add this

    fun setOrders(newOrders: List<OrderWithDetails>) {
        val oldOrders = orders
        orders = newOrders
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldOrders.size
            override fun getNewListSize() = newOrders.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldOrders[oldPos].order.orderId == newOrders[newPos].order.orderId
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldOrders[oldPos] == newOrders[newPos]
            }
        }).dispatchUpdatesTo(this)
    }

    // Add this method
    fun setApprovalListener(listener: OrderApprovalListener) {
        approvalListener = listener
    }

    fun setOnTrackClickListener(listener: (String) -> Unit) {
        onTrackClickListener = listener
    }

    fun getCurrentOrders(): List<OrderWithDetails> = orders

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        holder.bind(order)
    }

    override fun getItemCount() = orders.size

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOrderNumber: TextView = itemView.findViewById(R.id.tvOrderNumber)
        private val tvOrderDate: TextView = itemView.findViewById(R.id.tvOrderDate)
        private val chipOrderStatus: Chip = itemView.findViewById(R.id.chipOrderStatus)
        private val btnTrackOrder: Button = itemView.findViewById(R.id.btnTrackOrder)

        fun bind(orderWithDetails: OrderWithDetails) {
            val order = orderWithDetails.order

            tvOrderNumber.text = "Order #${order.orderId}"
            tvOrderDate.text = formatDate(order.orderDate)


            val productsContainer = itemView.findViewById<LinearLayout>(R.id.productsContainer)
            productsContainer.removeAllViews()

            orderWithDetails.items.forEach { item ->
                val productView = LayoutInflater.from(itemView.context).inflate(R.layout.product_item, productsContainer, false)

                val ivProductImage: ImageView = productView.findViewById(R.id.ivProductImage)
                if (item.product.imageUrl?.isNotEmpty() == true) {
                    Picasso.get()
                        .load(item.product.imageUrl)
                        .placeholder(R.drawable.default_image) // Optional
                        .error(R.drawable.default_image) // Optional
                        .into(ivProductImage)
                } else {
                    ivProductImage.setImageResource(R.drawable.default_image)
                }

                productView.findViewById<TextView>(R.id.tvProductName).text = item.product.name
                productView.findViewById<TextView>(R.id.tvProductDetails).text = "${item.product.category} • Qty: ${item.orderItem.quantity}"
                productView.findViewById<TextView>(R.id.tvProductPrice).text = "$${item.orderItem.quantity * item.product.price}"

                productsContainer.addView(productView)

                if(order.status == "Pending") {
                    itemView.findViewById<Chip>(R.id.chipOrderStatus).setOnClickListener {
                        approvalListener?.let { listener ->
                            val approvalDialog = OrderApprovalDialog(itemView.context, orderWithDetails, listener)
                            approvalDialog.showApprovalDialog()
                        }
                    }
                } else {
                    itemView.findViewById<Chip>(R.id.chipOrderStatus).setOnClickListener(null)
                }
            }

            // Set status chip color and text based on order status
            val (backgroundColor, textColor, statusText) = getStatusDetails(order.status)
            chipOrderStatus.apply {
                setChipBackgroundColorResource(backgroundColor)
                setTextColor(ContextCompat.getColor(context, textColor))
                text = statusText
            }

            btnTrackOrder.setOnClickListener {
                onTrackClickListener?.invoke(orderWithDetails.order.orderId)
            }
        }

        private fun formatDate(date: Date): String {
            val today = Calendar.getInstance()
            val orderDate = Calendar.getInstance().apply { time = date }

            return when {
                isSameDay(today, orderDate) -> "Today, ${formatTime(date)}"
                isYesterday(today, orderDate) -> "Yesterday, ${formatTime(date)}"
                else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
            }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun isYesterday(today: Calendar, orderDate: Calendar): Boolean {
            val yesterday = Calendar.getInstance().apply {
                time = today.time
                add(Calendar.DAY_OF_YEAR, -1)
            }
            return isSameDay(yesterday, orderDate)
        }

        private fun formatTime(date: Date): String {
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }

        private fun getStatusDetails(status: String): Triple<Int, Int, String> {
            return when (status.lowercase(Locale.ROOT)) {
                "pending" -> Triple(
                    R.color.status_pending_background,
                    R.color.status_pending_text,
                    "Pending"
                )
                "processing" -> Triple(
                    R.color.status_processing_background,
                    R.color.status_processing_text,
                    "Processing"
                )
                "ready" -> Triple(
                    R.color.status_ready_background,
                    R.color.status_ready_text,
                    "Ready to Pickup"
                )
                "completed" -> Triple(
                    R.color.status_completed_background,
                    R.color.status_completed_text,
                    "Completed"
                )
                "cancelled" -> Triple(
                    R.color.status_cancelled_background,
                    R.color.status_cancelled_text,
                    "Cancelled"
                )
                else -> Triple(
                    R.color.status_pending_background,
                    R.color.status_pending_text,
                    status
                )
            }
        }
    }
}

class OrdersFragment : Fragment(), OrderApprovalListener {
    private lateinit var ordersAdapter: OrdersAdapter
    private val viewModel: OrdersViewModel by viewModels {
        OrdersViewModelFactory((requireActivity().application as Inventark).databaseInitializer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_orders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find views using findViewById
        val rvOrders: RecyclerView = view.findViewById(R.id.rvOrders)
        val searchView: SearchView = view.findViewById(R.id.ordersSearchView)
        val orderTabLayout: TabLayout = view.findViewById(R.id.orderTabLayout)
        val tvTotalOrders: TextView = view.findViewById(R.id.tvTotalOrders)
        val tvOrdersChange: TextView = view.findViewById(R.id.tvOrdersChange)
        val tvRevenue: TextView = view.findViewById(R.id.tvRevenue)
        val tvRevenueChange: TextView = view.findViewById(R.id.tvRevenueChange)

        ordersAdapter = OrdersAdapter()
        ordersAdapter.setApprovalListener(this)

        // Setup RecyclerView
        rvOrders.layoutManager = LinearLayoutManager(context)
        rvOrders.adapter = ordersAdapter

        // Setup SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query != null) {
                    viewModel.searchOrders(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null) {
                    viewModel.searchOrders(newText)
                }
                return true
            }
        })


        orderTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val status = when (tab.position) {
                        0 -> "Pending"
                        1 -> "Processing"
                        2 -> "Completed"
                        else -> "Cancelled"
                    }
                    updateOrdersByStatus(status)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Load initial orders (Pending by default)
        updateOrdersByStatus("Pending")
        // Setup Track Order click listener
        ordersAdapter.setOnTrackClickListener { orderId ->
            val trackingFragment = TrackingFragment.newInstance(orderId)

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, trackingFragment)
                .addToBackStack("TrackingFragment")  // Add to back stack to maintain parent-child relationship
                .commit()
        }

        // Observe ViewModel data
        viewModel.ordersList.observe(viewLifecycleOwner) { orders ->
            if (orders != null) {
                ordersAdapter.setOrders(orders)
            }
        }

        viewModel.todayStats.observe(viewLifecycleOwner) { stats ->
            tvTotalOrders.text = stats.ordersCount.toString()
            tvOrdersChange.text =
                "${if (stats.orderChange >= 0) "+" else ""}${stats.orderChange.toInt()}% from yesterday"

            tvRevenue.text = "$${String.format("%.2f", stats.revenue)}"
            tvRevenueChange.text =
                "${if (stats.revenueChange >= 0) "+" else ""}${stats.revenueChange.toInt()}% from yesterday"
        }
    }

    private fun updateOrdersByStatus(status: String) {
        // Use coroutine to fetch orders from the database with the selected status
        val database = AppDatabase.getInstance(requireContext())
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val orders = database.orderDao().getOrdersByStatus(status).first()
                val ordersWithDetails = orders.map { order ->
                    val customer = database.customerDao().getCustomerById(order.customerId)
                    val items = database.orderItemDao().getOrderItemsWithProducts(order.orderId)
                    val shipment = database.shipmentDao().getShipmentForOrder(order.orderId)
                    OrderWithDetails(order, customer, items, shipment)
                }
                withContext(Dispatchers.Main) {
                    ordersAdapter.setOrders(ordersWithDetails)
                }
            }
        }
    }

    override fun onOrderApproved(orderId: String, approvalStatus: Boolean, orderStatus: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Get updated order data
                val database = AppDatabase.getInstance(requireContext())
                val order = database.orderDao().getOrderById(orderId)
                val customer = database.customerDao().getCustomerById(order.customerId)
                val items = database.orderItemDao().getOrderItemsWithProducts(orderId)
                val shipment = database.shipmentDao().getShipmentForOrder(orderId)

                val updatedOrderWithDetails = OrderWithDetails(order, customer, items, shipment)

                withContext(Dispatchers.Main) {
                    // Update adapter with new data
                    val currentOrders = ordersAdapter.getCurrentOrders().toMutableList()
                    val index = currentOrders.indexOfFirst { it.order.orderId == orderId }
                    if (index != -1) {
                        currentOrders[index] = updatedOrderWithDetails
                        ordersAdapter.setOrders(currentOrders)
                    }
                }
            }
        }
    }

    override fun onOrderCancelled(orderId: String, approvalStatus: Boolean, orderStatus: String) {
        // Reuse the same logic as onOrderApproved
        onOrderApproved(orderId, approvalStatus, orderStatus)
    }
}

// ViewModel Factory
class OrdersViewModelFactory(private val initializer: DatabaseInitializer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrdersViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OrdersViewModel(initializer.getDatabase()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

interface OrderApprovalListener {
    fun onOrderApproved(orderId: String, approvalStatus: Boolean, orderStatus: String)
    fun onOrderCancelled(orderId: String, approvalStatus: Boolean, orderStatus: String)
}

// Data class for statistics
data class OrderStats(
    val ordersCount: Int,
    val orderChange: Float,
    val revenue: Float,
    val revenueChange: Float
)
