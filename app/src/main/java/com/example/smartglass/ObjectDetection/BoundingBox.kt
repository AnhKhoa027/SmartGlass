package com.example.smartglass.ObjectDetection

import android.graphics.Color

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val cnf: Float,
    val cls: Int,
    val clsName: String,
    val source: DetectSource = DetectSource.YOLO,
    // Thêm màu sắc (Mặc định xanh lá)
    val boxColor: Int = Color.GREEN
)

enum class DetectSource {
    YOLO,
    CLASSIFIER,
    API
}