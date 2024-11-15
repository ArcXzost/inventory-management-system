package com.example.myapplication.utility


import com.example.myapplication.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.pow
import kotlin.math.sqrt

class DemandForecastRepository(
    private val database: AppDatabase,
    private val forecaster: DemandForecaster
) {
    suspend fun getForecastForProduct(productId: String): List<Pair<Date, Float?>> =
        withContext(Dispatchers.IO) {
            val inventory = database.inventoryDao().getItemById(productId)
            val demands = database.demandHistoryDao().getRecentDemands(productId, 3)

            // Calculate rolling statistics
            val rollingMean = demands.map { it.quantity }.average().toFloat()
            val rollingStd = calculateStdDev(demands.map { it.quantity })

            // Generate forecasts for next 30 days
            (0..29).map { daysAhead ->
                val localdate = LocalDate.now().plusDays(daysAhead.toLong())
                val date = Date.from(localdate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                val input = inventory?.let {
                    ForecastInput(
                        date = date,
                        productId = productId,
                        currentStock = it.quantity,
                        restockAmount = inventory.reorderLevel,
                        promotion = if (isPromotion(date)) 1 else 0,
                        holiday = if (isHoliday(date)) 1 else 0,
                        peakSeason = if (isPeakSeason(date)) 1 else 0,
                        reorderPoint = inventory.lastRestocked,
//                        leadTime = inventory.leadTime,
                        demandLag1 = demands.getOrNull(0)?.quantity ?: 0,
                        demandLag2 = demands.getOrNull(1)?.quantity ?: 0,
                        demandLag3 = demands.getOrNull(2)?.quantity ?: 0,
                        rollingMean = rollingMean,
                        rollingStd = rollingStd
                    )
                }
                forecaster.initializeModel()
                date to input?.let { forecaster.predict(it) }
            }
        }

    private fun calculateStdDev(values: List<Int>): Float {
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance).toFloat()
    }

    private fun isPromotion(date: Date): Boolean {
        // Implement promotion check logic
        return false
    }

    private fun isHoliday(date: Date): Boolean {
        // Implement holiday check logic
        return false
    }

    private fun isPeakSeason(date: Date): Boolean {
        // Implement peak season check logic
        return false
    }
}