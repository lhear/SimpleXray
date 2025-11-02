package com.simplexray.an.common

/**
 * Fixed-size moving average for Float values.
 */
class MovingAverage(private val window: Int) {
    private val buffer = ArrayDeque<Float>(window)
    private var sum = 0f

    fun add(value: Float): Float {
        buffer.addLast(value)
        sum += value
        if (buffer.size > window) {
            sum -= buffer.removeFirst()
        }
        return average()
    }

    fun average(): Float = if (buffer.isEmpty()) 0f else sum / buffer.size
    fun clear() { buffer.clear(); sum = 0f }
}

