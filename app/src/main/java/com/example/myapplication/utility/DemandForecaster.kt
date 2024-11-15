package com.example.myapplication.utility

import android.content.Context
import android.util.Log
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.IValue
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ForecastInput(
    val date: Date,
    val productId: String,
    val currentStock: Int,
    val restockAmount: Int,
    val promotion: Int,
    val holiday: Int,
    val peakSeason: Int,
    val reorderPoint: Date,
    val demandLag1: Int,
    val demandLag2: Int,
    val demandLag3: Int,
    val rollingMean: Float,
    val rollingStd: Float
)

class DemandForecaster(private val context: Context) {
    private var model: Module? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "DemandForecaster"
        private const val SEQUENCE_LENGTH = 512
        private const val MODEL_FILE = "distilBERT_demand_model.pth"

        // Constants for input normalization
        private const val MAX_STOCK = 10000f
        private const val MAX_DEMAND = 1000f
        private const val MAX_ROLLING = 100f
    }

    suspend fun initializeModel() = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                val modelFile = copyModelToCache()

                if (isMemoryAvailable()) {
                    model = Module.load(modelFile.absolutePath)
                    isInitialized = true
                    Log.d(TAG, "Model initialized successfully")
                    runWarmUpPrediction()
                } else {
                    throw OutOfMemoryError("Insufficient memory to load model")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model", e)
            handleModelError(e)
            throw e
        }
    }

    private fun isMemoryAvailable(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val availableMemory = maxMemory - usedMemory
        return availableMemory > 200 * 1024 * 1024
    }

    private fun copyModelToCache(): File {
        val cacheFile = File(context.cacheDir, MODEL_FILE)
        if (!cacheFile.exists()) {
            context.assets.open(MODEL_FILE).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return cacheFile
    }

    private suspend fun runWarmUpPrediction() = withContext(Dispatchers.Default) {
        try {
            val dummyInput = ForecastInput(
                Date(), "dummy", 0, 0, 0, 0, 0, Date(), 0, 0, 0, 0.0f, 0.0f
            )
            predict(dummyInput)
        } catch (e: Exception) {
            Log.w(TAG, "Warm-up prediction failed", e)
        }
    }

    suspend fun predict(input: ForecastInput): Float = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            throw IllegalStateException("Model not initialized. Call initializeModel() first.")
        }

        var inputTensor: Tensor? = null
        var attentionMaskTensor: Tensor? = null
        var outputTensor: Tensor? = null

        try {
            // Create input tensors
            inputTensor = createInputTensor(input)
            attentionMaskTensor = createAttentionMask()

            // Create input IValues
            val inputIValue = IValue.from(inputTensor)
            val attentionIValue = IValue.from(attentionMaskTensor)

            // Forward pass with both tensors
            val outputIValue = model?.forward(inputIValue, attentionIValue)
            outputTensor = outputIValue?.toTensor()

            return@withContext outputTensor?.getDataAsFloatArray()?.get(0) ?: 0.0f
        } catch (e: Exception) {
            Log.e(TAG, "Error during prediction", e)
            throw e
        }
    }

    private fun createInputTensor(input: ForecastInput): Tensor {
        val normalizedInputs = floatArrayOf(
            getDayOfYear(input.date).toFloat() / 366f,
            input.productId.hashCode().toFloat() / Int.MAX_VALUE,
            input.currentStock.toFloat() / MAX_STOCK,
            input.restockAmount.toFloat() / MAX_STOCK,
            input.promotion.toFloat(),
            input.holiday.toFloat(),
            input.peakSeason.toFloat(),
            getDayOfYear(input.reorderPoint).toFloat() / 366f,
            input.demandLag1.toFloat() / MAX_DEMAND,
            input.demandLag2.toFloat() / MAX_DEMAND,
            input.demandLag3.toFloat() / MAX_DEMAND,
            input.rollingMean / MAX_ROLLING,
            input.rollingStd / MAX_ROLLING
        )

        val paddedArray = padSequence(normalizedInputs)
        return Tensor.fromBlob(
            paddedArray,
            longArrayOf(1L, SEQUENCE_LENGTH.toLong())
        )
    }

    private fun createAttentionMask(): Tensor {
        val attentionMask = FloatArray(SEQUENCE_LENGTH) { 1.0f }
        return Tensor.fromBlob(
            attentionMask,
            longArrayOf(1L, SEQUENCE_LENGTH.toLong())
        )
    }

    private fun padSequence(input: FloatArray): FloatArray {
        return FloatArray(SEQUENCE_LENGTH) { i ->
            if (i < input.size) input[i] else 0.0f
        }
    }

    private fun getDayOfYear(date: Date): Int {
        return java.util.Calendar.getInstance().apply {
            time = date
        }.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun handleModelError(error: Exception) {
        when (error) {
            is OutOfMemoryError -> {
                System.gc()
                close()
            }
//            is com.facebook.jni.CppException -> {
//                if (error.message?.contains("Unknown builtin op") == true) {
//                    Log.e(TAG, "Incompatible model version. Please update model file.", error)
//                }
//            }
        }
    }

    fun close() {
        try {
//            model?.close()
            model = null
            isInitialized = false
            System.gc()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}