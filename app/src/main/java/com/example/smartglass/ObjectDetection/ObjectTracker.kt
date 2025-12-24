package com.example.smartglass.ObjectDetection

import android.graphics.Color
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

class ObjectTracker(
    private val maxObjects: Int = 10,
    private val iouThreshold: Float = 0.45f,
    private val smoothFactor: Float = 0.4f,
    private val maxInactiveTime: Long = 1500L
) {

    private val trackedObjects = mutableMapOf<Int, TrackedObject>()
    private var nextId = 0
    private fun getDirection(cx: Float): String {
        return when {
            cx < 0.33f -> "left"
            cx > 0.66f -> "right"
            else -> "center"
        }
    }

    fun update(detections: List<BoundingBox>): List<TrackedObject> {
        val now = SystemClock.uptimeMillis()
        val updatedIds = mutableSetOf<Int>()

        val sortedDetections = detections.sortedByDescending { it.cnf }

        for (det in sortedDetections) {
            var matchedId: Int? = null
            var bestIoU = 0f

            for ((id, tracked) in trackedObjects) {
                if (id in updatedIds) continue

                val iou = calculateIoU(det, tracked.box)
                if (iou > iouThreshold && iou > bestIoU) {
                    bestIoU = iou
                    matchedId = id
                }
            }

            if (matchedId != null) {
                // ========= VẬT CŨ =========
                val obj = trackedObjects[matchedId]!!
                val old = obj.smoothBox

                val isVerified =
                    old.boxColor == Color.BLUE || old.boxColor == Color.WHITE

                val finalLabel = if (isVerified) old.clsName else det.clsName
                val finalConf = if (isVerified) old.cnf else det.cnf
                val finalColor = if (isVerified) old.boxColor else det.boxColor

                var smX1 = old.x1 * (1 - smoothFactor) + det.x1 * smoothFactor
                var smY1 = old.y1 * (1 - smoothFactor) + det.y1 * smoothFactor
                var smX2 = old.x2 * (1 - smoothFactor) + det.x2 * smoothFactor
                var smY2 = old.y2 * (1 - smoothFactor) + det.y2 * smoothFactor

                smX1 = smX1.coerceIn(0f, 1f)
                smY1 = smY1.coerceIn(0f, 1f)
                smX2 = max(smX1 + 0.01f, smX2.coerceIn(0f, 1f))
                smY2 = max(smY1 + 0.01f, smY2.coerceIn(0f, 1f))

                val w = smX2 - smX1
                val h = smY2 - smY1
                val cx = smX1 + w / 2f
                val cy = smY1 + h / 2f

                val smooth = BoundingBox(
                    x1 = smX1, y1 = smY1, x2 = smX2, y2 = smY2,
                    cx = cx, cy = cy, w = w, h = h,
                    cnf = finalConf,
                    cls = det.cls,
                    clsName = finalLabel,
                    boxColor = finalColor,
                    source = det.source
                )

                obj.box = det
                obj.smoothBox = smooth
                obj.lastSeen = now
                obj.direction = getDirection(cx)

                updatedIds.add(matchedId)

            } else {
                // ========= VẬT MỚI =========
                val w = det.x2 - det.x1
                val h = det.y2 - det.y1
                val cx = det.x1 + w / 2f
                val cy = det.y1 + h / 2f

                val fixed = det.copy(
                    w = w,
                    h = h,
                    cx = cx,
                    cy = cy
                )

                val obj = TrackedObject(
                    id = nextId,
                    box = fixed,
                    smoothBox = fixed,
                    lastSeen = now,
                    status = "đứng yên",
                    direction = getDirection(cx)
                )

                trackedObjects[nextId] = obj
                updatedIds.add(nextId)
                nextId++
            }
        }

        // ========= DỌN RÁC =========
        val expired = trackedObjects
            .filter { now - it.value.lastSeen > maxInactiveTime }
            .keys

        expired.forEach { trackedObjects.remove(it) }

        return trackedObjects.values.toList()
    }

    private fun calculateIoU(b1: BoundingBox, b2: BoundingBox): Float {
        val x1 = max(b1.x1, b2.x1)
        val y1 = max(b1.y1, b2.y1)
        val x2 = min(b1.x2, b2.x2)
        val y2 = min(b1.y2, b2.y2)

        if (x2 <= x1 || y2 <= y1) return 0f

        val inter = (x2 - x1) * (y2 - y1)
        val a1 = (b1.x2 - b1.x1) * (b1.y2 - b1.y1)
        val a2 = (b2.x2 - b2.x1) * (b2.y2 - b2.y1)

        return inter / (a1 + a2 - inter)
    }
}
