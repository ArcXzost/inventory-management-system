package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.databinding.FragmentAnalyticsBinding
import com.example.myapplication.db.AppDatabase
import com.example.myapplication.db.CategoryDao
import com.example.myapplication.utility.DemandForecastRepository
import com.example.myapplication.utility.DemandForecaster
import com.example.myapplication.utility.MonteCarloSimulator
import com.example.myapplication.utility.StockAnalysis
import com.example.myapplication.utility.StockAnalyzer
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val BEST_CASE_DEMAND_GROWTH = 1.2
    private val BEST_CASE_ECONOMIC_SHIFT = 0.8
    private val WORST_CASE_DEMAND_GROWTH = 0.7
    private val WORST_CASE_ECONOMIC_SHIFT = 1.3

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLineChart(binding.chartDemandForecast, "Demand Forecast")
        loadSimulationData()
        loadStockAnalysisData()
    }

    private fun loadSimulationData() {
        lifecycleScope.launch {
//            val categories = listOf("Electronics", "Clothing", "Books", "Home & Garden", "Sports")
            withContext(Dispatchers.IO)
            {
                val categoryDao: CategoryDao =
                    AppDatabase.getInstance(requireContext()).categoryDao()
                val categories: List<String> = categoryDao.getAllCategories().map { it.name }

                withContext(Dispatchers.Main) {
                    val monteCarloSimulator =
                        MonteCarloSimulator(
                            AppDatabase.getInstance(requireContext()).categoryDemandDao()
                        )
                    // Fetch simulation results for demand data by category
                    val demandData: Map<String, Float> =
                        monteCarloSimulator.runCategoryWiseSimulation()

                    // Create entries using category index as X-axis and demand as Y-axis
                    val entries = demandData.entries.mapIndexed { index, entry ->
                        BarEntry(index.toFloat(), entry.value)
                    }

                    val bestCaseScenario = MonteCarloSimulator.Scenario(
                        BEST_CASE_DEMAND_GROWTH,
                        BEST_CASE_ECONOMIC_SHIFT
                    )
                    val worstCaseScenario = MonteCarloSimulator.Scenario(
                        WORST_CASE_DEMAND_GROWTH,
                        WORST_CASE_ECONOMIC_SHIFT
                    )

                    // Run simulations for each scenario
                    val bestCaseDemandData =
                        monteCarloSimulator.runCategoryWiseSimulation(bestCaseScenario)
                    val worstCaseDemandData =
                        monteCarloSimulator.runCategoryWiseSimulation(worstCaseScenario)

                    updateBarChart(
                        binding.chartSalesSimulationBest,
                        bestCaseDemandData.entries.mapIndexed { index, entry ->
                            BarEntry(
                                index.toFloat(),
                                entry.value
                            )
                        },
                        categories,
                        "Best Case Scenario"
                    )

                    updateBarChart(
                        binding.chartSalesSimulationWorst,
                        worstCaseDemandData.entries.mapIndexed { index, entry ->
                            BarEntry(
                                index.toFloat(),
                                entry.value
                            )
                        },
                        categories,
                        "Worst Case Scenario"
                    )

                    // Set Bar Chart data
                    val barChart = BarChart(context)

                    barChart.xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return categories.getOrNull(value.toInt()) ?: ""
                        }
                    }
                    barChart.xAxis.granularity = 1f
                }
            }
        }
    }

    private fun setupForecastChart(chart: LineChart, forecasts: List<Pair<Date, Float>>) {
        val entries = forecasts.mapIndexed { index, (date, value) ->
            Entry(index.toFloat(), value)
        }

        val dataSet = LineDataSet(entries, "Demand Forecast").apply {
            color = Color.GREEN
            lineWidth = 2f
            setDrawFilled(true)
            fillAlpha = 50
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setCircleColor(Color.WHITE)
            setDrawCircles(true)
            circleRadius = 4f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.0f", value)
                }
            }
        }

        chart.apply {
            data = LineData(dataSet)
            description.text = "30-Day Demand Forecast"
            description.textColor = Color.WHITE
            extraBottomOffset = 20f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                labelRotationAngle = -45f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val forecast = forecasts.getOrNull(value.toInt())
                        val date = forecast?.first
                        val format = SimpleDateFormat("MM-dd") // or any other desired pattern
                        return date?.let { format.format(it) } ?: ""
                    }
                }
                setDrawGridLines(false)
            }


            axisLeft.apply {
                textColor = Color.WHITE
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = Color.GRAY
            }

            axisRight.isEnabled = false
            legend.textColor = Color.WHITE

            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            animateXY(1000, 1000)
        }
    }

    private fun setupLineChart(chart: LineChart, descriptionText: String) {
        chart.description = Description().apply { text = descriptionText }
        chart.setNoDataText(getString(R.string.loading_data)) // Localized loading text
        chart.axisLeft.textColor = Color.WHITE
        chart.xAxis.textColor = Color.WHITE
        chart.legend.textColor = Color.WHITE
    }

    private fun loadStockAnalysisData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val stockAnalyzer = withContext(Dispatchers.IO) {
                    StockAnalyzer(
                        AppDatabase.getInstance(requireContext()).inventoryDao(),
                        AppDatabase.getInstance(requireContext()).categoryDao()
                    )
                }
                val stockAnalysis = stockAnalyzer.runStockAnalysis()
                updateStockCharts(stockAnalysis)

                val forecaster = DemandForecaster(requireContext())
                val forecastRepository = DemandForecastRepository(
                    AppDatabase.getInstance(requireContext()),
                    forecaster
                )

                // Get forecasts for each product
                val productIds = withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(requireContext()).inventoryDao().getAllProductIds()
                }

                val allForecasts = mutableMapOf<String, List<Pair<Date, Float>>>()
                productIds.forEach { productId ->
                    allForecasts[productId] = forecastRepository.getForecastForProduct(productId) as List<Pair<Date, Float>>
                }

                // Update charts with forecast data
                setupForecastChart(binding.chartDemandForecast, allForecasts.values.flatten())
            } catch (e: Exception) {
                // Handle errors and possibly show an error message to the user
            }
        }
    }

    private fun updateStockCharts(stockAnalysis: StockAnalysis) {
        setupStockCoverChart(binding.chartStockCover, stockAnalysis.stockCoverHistory)
        setupImbalanceScoreChart(binding.chartImbalanceScore, stockAnalysis.imbalanceScoreHistory)
        setupStockoutRiskChart(binding.chartStockoutRisk, stockAnalysis.stockoutRisk)
        setupDemandVolatilityChart(binding.chartDemandVolatility, stockAnalysis.demandVolatilityHistory)

        // Update text insights
        binding.tvStockEfficiency.apply {
            text = "Stock Efficiency: ${(stockAnalysis.stockEfficiency * 100).toInt()}%"
            setTextColor(Color.WHITE)
        }

        binding.tvReorderPointEffectiveness.apply {
            text =
                "Reorder Point Effectiveness: ${(stockAnalysis.reorderPointEffectiveness * 100).toInt()}%"
            setTextColor(Color.WHITE)
        }
    }

    private fun setupStockCoverChart(chart: LineChart, stockCoverHistory: List<Float>) {
        val entries = stockCoverHistory.mapIndexed { index, value ->
            Entry(index.toFloat(), value)
        }

        val dataSet = LineDataSet(entries, "Stock Cover (Days)").apply {
            color = Color.BLUE
            lineWidth = 2f
            setDrawFilled(true)
            fillAlpha = 50
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setCircleColor(Color.WHITE)
            setDrawCircles(true)
            circleRadius = 4f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f", value)
                }
            }
        }

        chart.apply {
            data = LineData(dataSet)
            description.text = "30-Day Stock Cover Trend"
            description.textColor = Color.WHITE

            extraBottomOffset = 20f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                labelRotationAngle = -45f
                yOffset = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val daysAgo = 29 - value.toInt()
                        return if (daysAgo == 0) "Today" else "$daysAgo d"
                    }
                }
                setDrawGridLines(false)
            }

            axisLeft.apply {
                textColor = Color.WHITE
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = Color.GRAY
//                gridAlpha = 0.3f
            }

            axisRight.isEnabled = false
            legend.textColor = Color.WHITE

            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            animateXY(1000, 1000)
        }
    }

    private fun setupImbalanceScoreChart(chart: LineChart, imbalanceHistory: List<Float>) {
        val entries = imbalanceHistory.mapIndexed { index, value ->
            Entry(index.toFloat(), value)
        }

        val dataSet = LineDataSet(entries, "Imbalance Score").apply {
            color = Color.YELLOW
            lineWidth = 2f
            setDrawFilled(true)
            fillAlpha = 50
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setCircleColor(Color.WHITE)
            setDrawCircles(true)
            circleRadius = 4f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f", value)
                }
            }
        }

        // Add threshold lines
        val upperThreshold = LimitLine(30f, "Upper Bound").apply {
            lineColor = Color.RED
            lineWidth = 1f
            textColor = Color.WHITE
        }

        val lowerThreshold = LimitLine(-30f, "Lower Bound").apply {
            lineColor = Color.RED
            lineWidth = 1f
            textColor = Color.WHITE
        }

        chart.apply {
            data = LineData(dataSet)
            description.text = "Stock Imbalance Trend"
            description.textColor = Color.WHITE
            extraBottomOffset = 20f


            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                labelRotationAngle = -45f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val daysAgo = 29 - value.toInt()
                        return if (daysAgo == 0) "Today" else "$daysAgo d"
                    }
                }
                setDrawGridLines(false)
            }

            axisLeft.apply {
                textColor = Color.WHITE
                addLimitLine(upperThreshold)
                addLimitLine(lowerThreshold)
                setDrawGridLines(true)
                gridColor = Color.GRAY
//                gridAlpha = 0.3f
            }

            axisRight.isEnabled = false
            legend.textColor = Color.WHITE

            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            animateXY(1000, 1000)
        }
    }

    private fun setupDemandVolatilityChart(chart: LineChart, volatilityHistory: List<Float>) {
        val entries = volatilityHistory.mapIndexed { index, value ->
            Entry(index.toFloat(), value * 100) // Convert to percentage
        }

        val dataSet = LineDataSet(entries, "Demand Volatility (%)").apply {
            color = Color.MAGENTA
            lineWidth = 2f
            setDrawFilled(true)
            fillAlpha = 50
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setCircleColor(Color.WHITE)
            setDrawCircles(true)
            circleRadius = 4f
            valueTextColor = Color.WHITE

            // Add dash effect to show uncertainty
            enableDashedLine(10f, 5f, 0f)

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f%%", value)
                }
            }
        }

        chart.apply {
            data = LineData(dataSet)
            description.text = "Demand Volatility Trend"
            description.textColor = Color.WHITE
            extraBottomOffset = 20f


            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                labelRotationAngle = -45f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val daysAgo = 29 - value.toInt()
                        return if (daysAgo == 0) "Today" else "$daysAgo d"
                    }
                }
                setDrawGridLines(false)
            }

            axisLeft.apply {
                textColor = Color.WHITE
                axisMinimum = 0f
                valueFormatter = PercentFormatter()
                setDrawGridLines(true)
                gridColor = Color.GRAY
//                gridAlpha = 0.3f
            }

            axisRight.isEnabled = false
            legend.textColor = Color.WHITE

            setDrawGridBackground(false)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            animateXY(1000, 1000)
        }
    }
    private fun setupStockoutRiskChart(chart: PieChart, stockoutRisk: Float) {
        val entries = listOf(
            PieEntry(stockoutRisk * 100, "Risk"),
            PieEntry((1 - stockoutRisk) * 100, "Safe")
        )

        val dataSet = PieDataSet(entries, "Stockout Risk").apply {
            colors = listOf(
                Color.rgb(255, 89, 94),  // Risk color (red)
                Color.rgb(138, 201, 38)   // Safe color (green)
            )
            valueTextColor = Color.WHITE
            valueTextSize = 14f
            valueFormatter = PercentFormatter(chart)
        }

        chart.apply {
            data = PieData(dataSet)
            description.text = ""
            setUsePercentValues(true)
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(110)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            centerText = "Stockout\nRisk"
            setCenterTextColor(Color.WHITE)
            setCenterTextSize(16f)
            legend.textColor = Color.WHITE
            animateY(1000)
        }
    }


    private fun updateBarChart(
        chart: BarChart,
        entries: List<BarEntry>,
        categories: List<String>,
        label: String
    ) {
//        chart.description = Description().apply { text = label }
        chart.setNoDataText("Loading data...")

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return categories.getOrNull(value.toInt())?: ""
            }
        }
        chart.xAxis.labelRotationAngle = -45f
        chart.xAxis.labelCount = categories.size
        chart.xAxis.granularity = 1f
        chart.axisLeft.textColor = Color.WHITE
        chart.axisRight.textColor = Color.WHITE
        chart.xAxis.textColor = Color.WHITE
        chart.legend.textColor = Color.WHITE
        chart.description = Description().apply { text = "" }

        val barDataSet = BarDataSet(entries, label).apply {
            colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
            valueTextColor = Color.WHITE
        }
        chart.data = BarData(barDataSet)
        chart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
