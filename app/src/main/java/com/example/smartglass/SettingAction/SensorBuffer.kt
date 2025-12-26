package com.example.smartglass.SettingAction

import java.util.ArrayDeque
import kotlin.math.abs

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

    // =========================
    // USER MOVEMENT INFERENCE
    // =========================

    /**
     * Suy luận chuyển động tiến / lùi dựa trên thay đổi distance
     */
    @Synchronized
    fun getUserMoveDirY(): String {
        if (buffer.size < 2) return "STAY"

        val curr = buffer.last()
        val prev = buffer.elementAt(buffer.size - 2)

        val delta = curr.distanceMm - prev.distanceMm

        return when {
            delta < -100 -> "FORWARD" // khoảng cách giảm → người tiến lên
            delta > 100  -> "BACK"    // khoảng cách tăng → người lùi lại
            else         -> "STAY"
        }
    }

    /**
     * Hiện tại chưa có IMU X → giữ STAY
     */
    fun getUserMoveDirX(): String {
        return "STAY"
    }

    fun clear() {
        buffer.clear()
    }
}
