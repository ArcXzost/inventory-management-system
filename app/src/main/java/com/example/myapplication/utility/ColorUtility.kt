package com.example.myapplication.utility

import android.content.res.ColorStateList
import android.graphics.Color

object ColorUtility {
    fun getProgressColorStateList(progress: Int): ColorStateList {
        val color = when {
            progress < 50 -> interpolateColor(Color.RED, Color.YELLOW, progress / 50f)
            else -> interpolateColor(Color.YELLOW, Color.GREEN, (progress - 50) / 50f)
        }
        return ColorStateList.valueOf(color)
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val startA = Color.alpha(startColor)
        val startR = Color.red(startColor)
        val startG = Color.green(startColor)
        val startB = Color.blue(startColor)

        val endA = Color.alpha(endColor)
        val endR = Color.red(endColor)
        val endG = Color.green(endColor)
        val endB = Color.blue(endColor)

        val a = (startA + (endA - startA) * fraction).toInt()
        val r = (startR + (endR - startR) * fraction).toInt()
        val g = (startG + (endG - startG) * fraction).toInt()
        val b = (startB + (endB - startB) * fraction).toInt()

        return Color.argb(a, r, g, b)
    }
}