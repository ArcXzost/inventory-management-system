package com.example.myapplication.utility

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import com.example.myapplication.db.*
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

class MonteCarloSimulator(
    private val categoryDemandDao: CategoryDemandDao
) {
    private val NEUTRAL_DEMAND_GROWTH = 1.0
    private val NEUTRAL_ECONOMIC_SHIFT = 1.0

    // Define a data class for scenario
    data class Scenario(val demandGrowth: Double, val economicShift: Double)

    // Update runCategoryWiseSimulation to accept a scenario
    suspend fun runCategoryWiseSimulation(
        scenario: Scenario = Scenario(NEUTRAL_DEMAND_GROWTH, NEUTRAL_ECONOMIC_SHIFT),
        numSimulations: Int = 1000,
        days: Int = 30
    ): Map<String, Float> {
        val demandEstimates = mutableMapOf<String, Float>()

        // Retrieve all category demand data
        val categoryDemands = withContext(Dispatchers.IO) {
            categoryDemandDao.getAllCategoryDemand()
        }

        // Run simulations for each category
        categoryDemands.forEach { demand ->
            val totalDemand = (1..numSimulations).map {
                simulateDailyDemand(
                    demand.avgDemand * scenario.demandGrowth,
                    demand.demandStdDev * scenario.economicShift,
                    days
                )
            }.average() // Calculate average demand over all simulations

            demandEstimates[demand.categoryName] = totalDemand.toFloat()
        }

        return demandEstimates
    }

    /**
     * Runs Monte Carlo simulations for each category to estimate required inventory.
     *
     * @param numSimulations Number of simulations to run per category.
     * @param days Number of days for which the demand is predicted.
     * @return A map of category names to estimated demand for the specified period.
     */
    suspend fun runCategoryWiseSimulation(numSimulations: Int = 1000, days: Int = 30): Map<String, Float> {
        val demandEstimates = mutableMapOf<String, Float>()

        // Retrieve all category demand data
        val categoryDemands = withContext(Dispatchers.IO) {
            categoryDemandDao.getAllCategoryDemand()
        }

        // Run simulations for each category
        categoryDemands.forEach { demand ->
            val totalDemand = (1..numSimulations).map {
                simulateDailyDemand(demand.avgDemand, demand.demandStdDev, days)
            }.average() // Calculate average demand over all simulations

            demandEstimates[demand.categoryName] = totalDemand.toFloat()
        }

        return demandEstimates
    }

    /**
     * Simulates daily demand over a given number of days using normal distribution.
     *
     * @param avgDemand Average daily demand for the category.
     * @param demandStdDev Standard deviation of daily demand for the category.
     * @param days Number of days to simulate.
     * @return Total demand over the simulated days.
     */
    private fun simulateDailyDemand(avgDemand: Double, demandStdDev: Double, days: Int): Double {
        return (1..days).sumOf {
            val dailyDemand = Random.nextGaussian(avgDemand, demandStdDev)
            dailyDemand.coerceAtLeast(0.0) // Ensure demand is non-negative
        }
    }

    /**
     * Extension function to generate random numbers with a normal distribution.
     */
    private fun Random.nextGaussian(mean: Double, stdDev: Double): Double {
        // Using Box-Muller transform to generate a standard normal random value
        val stdNormal = nextDouble().let { r1 ->
            (-2.0 * ln(nextDouble())).run {
                if (r1 < 0.5) cos(Math.PI * r1) * sqrt(this)
                else sin(Math.PI * r1) * sqrt(this)
            }
        }
        // Scale with mean and standard deviation
        return mean + stdDev * stdNormal
    }
}
