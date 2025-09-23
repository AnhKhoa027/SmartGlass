package com.example.smartglass.ObjectDetection

import android.os.SystemClock
import kotlin.math.abs

class ObjectTracker(
    private val maxObjects: Int = 5,
    private val iouThreshold: Float = 0.5f,
    private val smoothFactor: Float = 0.15f,
    private val maxInactiveTime: Long = 2000L // ms
) {
    private val trackedObjects = mutableMapOf<Int, TrackedObject>()
    private var nextId = 0

    fun update(detections: List<BoundingBox>): List<TrackedObject> {
        val now = SystemClock.uptimeMillis()
        val results = mutableListOf<TrackedObject>()

        // Giữ top N object theo confidence
        val sortedDetections = detections.sortedByDescending { it.cnf }.take(maxObjects)

        for (det in sortedDetections) {
            var matchedId: Int? = null
            var bestIoU = 0f

            // Tìm object cũ khớp IoU
            for ((id, tracked) in trackedObjects) {
                val iou = calculateIoU(det, tracked.box)
                if (iou > iouThreshold && iou > bestIoU) {
                    bestIoU = iou
                    matchedId = id
                }
            }

            val id = matchedId ?: nextId++

            val tracked = trackedObjects[id]

            // Smoothing bbox
            val smoothBox = if (tracked != null) {
                BoundingBox(
                    x1 = tracked.smoothBox.x1 * (1 - smoothFactor) + det.x1 * smoothFactor,
                    y1 = tracked.smoothBox.y1 * (1 - smoothFactor) + det.y1 * smoothFactor,
                    x2 = tracked.smoothBox.x2 * (1 - smoothFactor) + det.x2 * smoothFactor,
                    y2 = tracked.smoothBox.y2 * (1 - smoothFactor) + det.y2 * smoothFactor,
                    cx = 0f, cy = 0f,
                    w = 0f, h = 0f,
                    cnf = det.cnf,
                    cls = det.cls,
                    clsName = det.clsName
                )
            } else det

            // Tính delta center để xác định hướng
            val (prevCx, prevCy) = if (tracked != null) {
                val pb = tracked.smoothBox
                (pb.x1 + pb.x2)/2f to (pb.y1 + pb.y2)/2f
            } else {
                val b = det
                (b.x1 + b.x2)/2f to (b.y1 + b.y2)/2f
            }

            val cx = (smoothBox.x1 + smoothBox.x2)/2f
            val cy = (smoothBox.y1 + smoothBox.y2)/2f
            val dx = cx - prevCx
            val dy = cy - prevCy

            // Xác định hướng và trạng thái
            val status: String
            val direction: String? = if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                status = "di chuyển"
                if (abs(dx) > abs(dy) * 1.5) if (dx > 0) "Phải" else "Trái"
                else if (abs(dy) > abs(dx) * 1.5) if (dy > 0) "Trên" else "Dưới"
                else tracked?.direction
            } else {
                status = "Đứng yên"
                tracked?.direction
            }

            trackedObjects[id] = TrackedObject(
                box = det,
                lastSeen = now,
                smoothBox = smoothBox,
                direction = direction,
                status = status
            )

            results.add(trackedObjects[id]!!)
        }

        // Xóa object không xuất hiện > maxInactiveTime
        val expired = trackedObjects.filter { now - it.value.lastSeen > maxInactiveTime }.keys
        expired.forEach { trackedObjects.remove(it) }

        return results
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)

        val interArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        return interArea / (box1Area + box2Area - interArea)
    }
}
