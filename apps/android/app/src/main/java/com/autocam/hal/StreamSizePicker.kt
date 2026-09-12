package com.autocam.hal

import android.util.Size
import kotlin.math.abs

object StreamSizePicker {
    fun closest(sizes: Array<Size>, targetW: Int, targetH: Int): Size? {
        if (sizes.isEmpty()) return null
        val target = targetW.toLong() * targetH
        return sizes.minBy { s ->
            val pixels = abs(s.width.toLong() * s.height - target)
            val aspect = abs(s.width.toDouble() / s.height - targetW.toDouble() / targetH)
            pixels + (aspect * 1_000_000).toLong()
        }
    }

    fun atMostPixels(sizes: Array<Size>, maxPixels: Int, preferAspect: Double? = null): Size? {
        val fit = sizes.filter { it.width.toLong() * it.height <= maxPixels }
        val pool = if (fit.isNotEmpty()) fit else sizes.toList()
        if (pool.isEmpty()) return null
        return if (preferAspect != null) {
            pool.minBy { abs(it.width.toDouble() / it.height - preferAspect) }
        } else {
            pool.maxBy { it.width.toLong() * it.height }
        }
    }

    fun nearestPixels(sizes: Array<Size>, targetPixels: Int, preferAspect: Double? = null): Size? {
        if (sizes.isEmpty()) return null
        val pool = if (preferAspect != null) {
            val close = sizes.filter { abs(it.width.toDouble() / it.height - preferAspect) < 0.08 }
            if (close.isNotEmpty()) close else sizes.toList()
        } else {
            sizes.toList()
        }
        return pool.minBy { abs(it.width.toLong() * it.height - targetPixels) }
    }
}
