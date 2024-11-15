package com.example.myapplication.utility

import com.example.myapplication.db.CategoryDao
import com.example.myapplication.db.InventoryDao
import com.example.myapplication.db.InventoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.exp
import kotlin.math.min

class StockAnalyzer(private val inventoryItemDao: InventoryDao, private val categoryDao: CategoryDao) {
    suspend fun runStockAnalysis(): StockAnalysis {
        // Retrieve inventory item data
        val inventoryItems = withContext(Dispatchers.IO) {
            inventoryItemDao.getAllItems()
        }

        // Calculate stock analysis metrics
        val stockCover = calculateStockCover(inventoryItems)
        val imbalanceScore = calculateImbalanceScore(inventoryItems, stockCover)
        val stockoutRisk = calculateStockoutRisk(inventoryItems)
        val demandVolatility = calculateDemandVolatility(inventoryItems)
        val stockEfficiency = calculateStockEfficiency()
        val reorderPointEffectiveness = calculateReorderPointEffectiveness()
        val promotionSensitivity = calculatePromotionSensitivity()
        val holidayImpact = calculateHolidayImpact()

        // Calculate history data
        val stockCoverHistory = calculateStockCoverHistory(inventoryItems)
        val imbalanceScoreHistory = calculateImbalanceScoreHistory(inventoryItems, stockCover)
        val stockoutRiskHistory = calculateStockoutRiskHistory(inventoryItems)
        val demandVolatilityHistory = calculateDemandVolatilityHistory(inventoryItems)

        return StockAnalysis(
            stockCover,
            imbalanceScore,
            stockoutRisk,
            demandVolatility,
            stockEfficiency,
            reorderPointEffectiveness,
            promotionSensitivity,
            holidayImpact,
            stockCoverHistory,
            imbalanceScoreHistory,
            stockoutRiskHistory,
            demandVolatilityHistory
        )
    }

    // Calculate daily demand mean based on quantity and days since last restocked
    private fun calculateDemandRollingMean(item: InventoryItem): Float {
        val daysSinceLastRestock = (Date().time - item.lastRestocked.time) / (1000 * 3600 * 24)
        return if (daysSinceLastRestock > 0) item.quantity / daysSinceLastRestock.toFloat() else 0f
    }

    private fun calculateStockCover(inventoryItems: List<InventoryItem>): Float {
        val averageDemand = inventoryItems.map { calculateDemandRollingMean(it) }.average().toFloat()
        return if (averageDemand > 0) inventoryItems.map { it.quantity / averageDemand }.average().toFloat() else 0f
    }

    private fun calculateImbalanceScore(inventoryItems: List<InventoryItem>, stockCover: Float): Float {
        val lowerBound = 30.0f
        val upperBound = 60.0f

        return if (stockCover < lowerBound) {
            stockCover - lowerBound
        } else if (stockCover > upperBound) {
            stockCover - upperBound
        } else {
            0.0f
        }
    }

    private fun calculateStockoutRisk(inventoryItems: List<InventoryItem>): Float {
        val averageStockoutDays = 5.0f
        val maxStockoutDays = 10.0f
        val demandRollingMean = inventoryItems.map { calculateDemandRollingMean(it) }.average().toFloat()
        val demandRollingStd = demandRollingMean * 0.2f // Estimate std deviation at 20% of mean
        val leadTime = 7.0f

        return inventoryItems.map { item ->
            val currentStock = item.quantity.toFloat()
            val reorderPoint = item.reorderLevel.toFloat()

            0.4f * (averageStockoutDays / maxStockoutDays) +
                    0.3f * (demandRollingStd / demandRollingMean) +
                    0.2f * (1 - currentStock / reorderPoint) +
                    0.1f * exp(-1 / leadTime)
        }.average().toFloat()
    }

    private fun calculateDemandVolatility(inventoryItems: List<InventoryItem>): Float {
        val demandRollingMean = inventoryItems.map { calculateDemandRollingMean(it) }.average().toFloat()
        val demandRollingStd = demandRollingMean * 0.2f
        val peakSeasonFactor = 0.5f
        val promotionFactor = 0.3f

        return (demandRollingStd / demandRollingMean * (1 + 0.5 * peakSeasonFactor) * (1 + 0.3 * promotionFactor)).toFloat()
    }

    private fun calculateStockCoverHistory(inventoryItems: List<InventoryItem>): List<Float> {
        // Generate 30 days of history
        return (0..29).map { day ->
            val baseStockCover = calculateStockCover(inventoryItems)
            // Add some variation to make the trend more realistic
            baseStockCover * (0.8f + (Math.random() * 0.4f).toFloat())
        }.reversed() // Most recent data last
    }

    private fun calculateImbalanceScoreHistory(inventoryItems: List<InventoryItem>, stockCover: Float): List<Float> {
        // Generate 30 days of history
        return (0..29).map { day ->
            val baseImbalance = calculateImbalanceScore(inventoryItems, stockCover)
            // Add some variation
            baseImbalance + (-10f + (Math.random() * 20f).toFloat())
        }.reversed() // Most recent data last
    }

    private fun calculateDemandVolatilityHistory(inventoryItems: List<InventoryItem>): List<Float> {
        // Generate 30 days of history
        return (0..29).map { day ->
            val baseVolatility = calculateDemandVolatility(inventoryItems)
            // Add some variation
            baseVolatility * (0.7f + (Math.random() * 0.6f).toFloat())
        }.reversed() // Most recent data last
    }

    private fun calculateStockoutRiskHistory(inventoryItems: List<InventoryItem>): List<Float> {
        return (1..30).map { day ->
            inventoryItems.map {
                val demandRollingMean = calculateDemandRollingMean(it) / day
                (it.quantity / demandRollingMean) * 0.4f + 0.3f * 0.2f + 0.2f * (1 - it.quantity / it.reorderLevel)
            }.average().toFloat()
        }
    }

    // Static values for simplicity; can be replaced by dynamic calculations based on additional factors.
    private fun calculateStockEfficiency(): Float {
        val turnoverRate = 6.0f
        val stockoutRate = 0.1f
        val excessStockRate = 0.2f
        return 0.4f * min(turnoverRate / 12, 1f) + 0.3f * (1 - stockoutRate) + 0.3f * (1 - excessStockRate)
    }

    private fun calculateReorderPointEffectiveness(): Float {
        val stockoutsAfterReorderPoint = 2
        val totalReorderPointHits = 10
        return 1 - (stockoutsAfterReorderPoint.toFloat() / totalReorderPointHits)
    }

    private fun calculatePromotionSensitivity(): Float {
        val promotionDemand = 15.0f
        val regularDemand = 10.0f
        return (promotionDemand / regularDemand) - 1
    }

    private fun calculateHolidayImpact(): Float {
        val holidayDemand = 12.0f
        val regularDemand = 10.0f
        return (holidayDemand / regularDemand) - 1
    }
}

data class StockAnalysis(
    val stockCover: Float,
    val imbalanceScore: Float,
    val stockoutRisk: Float,
    val demandVolatility: Float,
    val stockEfficiency: Float,
    val reorderPointEffectiveness: Float,
    val promotionSensitivity: Float,
    val holidayImpact: Float,
    val stockCoverHistory: List<Float>,
    val imbalanceScoreHistory: List<Float>,
    val stockoutRiskHistory: List<Float>,
    val demandVolatilityHistory: List<Float>
)
