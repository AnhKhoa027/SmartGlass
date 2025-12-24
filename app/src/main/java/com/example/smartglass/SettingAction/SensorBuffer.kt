package com.example.smartglass.SettingAction

import java.util.ArrayDeque

class SensorBuffer(
    private val maxSize: Int = 10
) {

    private val buffer = ArrayDeque<SensorSample>()

    @Synchronized
    fun add(sample: SensorSample) {
        if (buffer.size >= maxSize) {
            buffer.removeFirst()
        }
        buffer.addLast(sample)
    }

    /**
     * Lấy giá trị distance hợp lệ gần nhất
     */
    @Synchronized
    fun getLatestValid(
        maxAgeMs: Long = 3000
    ): SensorSample? {

        val now = System.currentTimeMillis()
        val iterator = buffer.descendingIterator()

        while (iterator.hasNext()) {
            val sample = iterator.next()

            if (
                sample.distanceMm in 10..1800 &&
                now - sample.timestamp <= maxAgeMs
            ) {
                return sample
            }
        }
        return null
    }

    fun clear() {
        buffer.clear()
    }
}
