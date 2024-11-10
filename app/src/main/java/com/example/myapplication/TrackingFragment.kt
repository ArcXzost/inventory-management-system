package com.example.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.navArgs
import com.example.myapplication.databinding.FragmentTrackingBinding
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.OrderTracking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.*

class TrackingFragment : Fragment() {
    private var _binding: FragmentTrackingBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_ORDER_ID = "orderId"

        fun newInstance(orderId: String): TrackingFragment {
            val fragment = TrackingFragment()
            val args = Bundle()
            args.putString(ARG_ORDER_ID, orderId)
            fragment.arguments = args
            return fragment
        }
    }

    private val viewModel: TrackingViewModel by viewModels {
        TrackingViewModelFactory((requireActivity().application as Inventark).databaseInitializer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val orderId = arguments?.getString(ARG_ORDER_ID)
        if (orderId != null) {
            // Fetch order tracking details with orderId
            viewModel.getOrderTracking(orderId)
        }

        // Observe tracking updates
        viewModel.trackingStatus.observe(viewLifecycleOwner) { trackingList ->
            updateTrackingUI(trackingList)
        }

        // Observe order details
        viewModel.orderDetails.observe(viewLifecycleOwner) { orderWithDetails ->
            updateOrderDetails(orderWithDetails)
        }
    }

    private fun updateTrackingUI(trackingList: List<OrderTracking>) {
        val latestStatus = trackingList.maxByOrNull { it.timestamp }?.status

        binding.apply {
            circleOrderPlaced.setImageResource(
                if (latestStatus != null) R.drawable.circle_filled else R.drawable.circle_outline
            )
            lineOrderPlacedToConfirmed.setBackgroundResource(
                if (latestStatus?.ordinal ?: -1 > TrackingStatus.ORDER_PLACED.ordinal)
                    R.color.accent_teal else R.color.white_70
            )

            circleConfirmed.setImageResource(
                if (latestStatus?.ordinal ?: -1 >= TrackingStatus.CONFIRMED.ordinal)
                    R.drawable.circle_filled else R.drawable.circle_outline
            )
            lineConfirmedToProcessing.setBackgroundResource(
                if (latestStatus?.ordinal ?: -1 > TrackingStatus.CONFIRMED.ordinal)
                    R.color.accent_teal else R.color.white_70
            )

            circleProcessing.setImageResource(
                if (latestStatus?.ordinal ?: -1 >= TrackingStatus.PROCESSING.ordinal)
                    R.drawable.circle_filled else R.drawable.circle_outline
            )
            lineProcessingToReady.setBackgroundResource(
                if (latestStatus?.ordinal ?: -1 > TrackingStatus.PROCESSING.ordinal)
                    R.color.accent_teal else R.color.white_70
            )

            circleReady.setImageResource(
                if (latestStatus?.ordinal ?: -1 >= TrackingStatus.READY_FOR_PICKUP.ordinal)
                    R.drawable.circle_filled else R.drawable.circle_outline
            )
            lineReadyToCompleted.setBackgroundResource(
                if (latestStatus?.ordinal ?: -1 > TrackingStatus.READY_FOR_PICKUP.ordinal)
                    R.color.accent_teal else R.color.white_70
            )

            circleCompleted.setImageResource(
                if (latestStatus?.ordinal ?: -1 >= TrackingStatus.COMPLETED.ordinal)
                    R.drawable.circle_filled else R.drawable.circle_outline
            )
        }

        updateTrackingHistory(trackingList)
    }

    private fun updateTrackingHistory(trackingList: List<OrderTracking>) {
        binding.trackingHistoryContainer.removeAllViews()

        trackingList.sortedByDescending { it.timestamp }.forEach { tracking ->
            val historyItem = LayoutInflater.from(requireContext())
                .inflate(R.layout.tracking_history, binding.trackingHistoryContainer, false)

            historyItem.findViewById<TextView>(R.id.tvTrackingStatus).text = tracking.status.name
                .replace("_", " ")
            historyItem.findViewById<TextView>(R.id.tvTrackingTimestamp).text =
                DateFormat.getDateTimeInstance().format(Date(tracking.timestamp))
            historyItem.findViewById<TextView>(R.id.tvTrackingDescription).text =
                tracking.description ?: ""

            binding.trackingHistoryContainer.addView(historyItem)
        }
    }

    private fun updateOrderDetails(orderWithDetails: OrderWithDetails) {
        binding.apply {
            tvOrderNumber.text = "Order #${orderWithDetails.order.orderId}"
            tvOrderDate.text = DateFormat.getDateTimeInstance().format(orderWithDetails.order.orderDate)
            // Update other order details as needed
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class TrackingViewModelFactory(private val databaseInitializer: DatabaseInitializer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrackingViewModel(databaseInitializer.getDatabase()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class TrackingViewModel(private val database: AppDatabase) : ViewModel() {
    private val _trackingStatus = MutableLiveData<List<OrderTracking>>()
    val trackingStatus: LiveData<List<OrderTracking>> = _trackingStatus

    private val _orderDetails = MutableLiveData<OrderWithDetails>()
    val orderDetails: LiveData<OrderWithDetails> = _orderDetails

    fun getOrderTracking(orderId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Get tracking updates
                val trackingList = database.orderTrackingDao().getOrderTracking(orderId)
                withContext(Dispatchers.Main) {
                    _trackingStatus.value = trackingList
                }

                // Get order details
                val order = database.orderDao().getOrderById(orderId)
                val customer = database.customerDao().getCustomerById(order.customerId)
                val items = database.orderItemDao().getOrderItemsWithProducts(orderId)
                val shipment = database.shipmentDao().getShipmentForOrder(orderId)

                withContext(Dispatchers.Main) {
                    _orderDetails.value = OrderWithDetails(order, customer, items, shipment)
                }
            }
        }
    }

    fun updateOrderStatus(
        orderId: String,
        status: TrackingStatus,
        description: String? = null
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val tracking = OrderTracking(
                        trackingId = UUID.randomUUID().toString(),
                        orderId = orderId,
                        status = status,
                        timestamp = System.currentTimeMillis(),
                        description = description
                    )
                    database.orderTrackingDao().insertTracking(tracking)
                }
            } catch (e: Exception) {
                // Handle error (e.g., log it)
            }
        }
    }
}

enum class TrackingStatus {
    ORDER_PLACED,
    CONFIRMED,
    PROCESSING,
    READY_FOR_PICKUP,
    COMPLETED,
    CANCELLED
}
