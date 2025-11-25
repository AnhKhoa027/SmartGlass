package com.example.smartglass.ObjectDetection

import android.graphics.Color
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min

class ObjectTracker(
    private val maxObjects: Int = 10,
    private val iouThreshold: Float = 0.45f, // Giảm nhẹ IoU để bắt dính tốt hơn
    private val smoothFactor: Float = 0.4f,  // Tăng smooth để box di chuyển mượt hơn
    private val maxInactiveTime: Long = 1500L // Thời gian "nhớ" vật thể khi bị che khuất (1.5 giây)
) {
    private val trackedObjects = mutableMapOf<Int, TrackedObject>()
    private var nextId = 0

    fun update(detections: List<BoundingBox>): List<TrackedObject> {
        val now = SystemClock.uptimeMillis()

        // Danh sách các ID đã được cập nhật trong frame này
        val updatedIds = mutableSetOf<Int>()

        // 1. Sắp xếp detections (Ưu tiên độ tin cậy cao)
        val sortedDetections = detections.sortedByDescending { it.cnf }

        for (det in sortedDetections) {
            var matchedId: Int? = null
            var bestIoU = 0f

            // Tìm đối tượng cũ khớp nhất
            for ((id, tracked) in trackedObjects) {
                // Chỉ match với những thằng chưa được update trong frame này
                if (id in updatedIds) continue

                val iou = calculateIoU(det, tracked.box) // So với box gốc lần trước
                if (iou > iouThreshold && iou > bestIoU) {
                    bestIoU = iou
                    matchedId = id
                }
            }

            if (matchedId != null) {
                // --- TRƯỜNG HỢP: TÌM THẤY NGƯỜI QUEN ---
                val id = matchedId
                val oldObj = trackedObjects[id]!!
                val oldBox = oldObj.smoothBox

                // LOGIC BẢO LƯU (QUAN TRỌNG NHẤT):
                // Nếu box cũ đã có màu XỊN (Xanh Dương/Trắng) -> Giữ nguyên Tên và Màu
                // Không để YOLO (thường là Xanh Lá hoặc Đỏ) ghi đè lên.
                val isOldBoxVerified = oldBox.boxColor == Color.BLUE || oldBox.boxColor == Color.WHITE

                val finalLabel = if (isOldBoxVerified) oldBox.clsName else det.clsName
                val finalConf = if (isOldBoxVerified) oldBox.cnf else det.cnf
                val finalColor = if (isOldBoxVerified) oldBox.boxColor else det.boxColor

                // Tính toán làm mượt tọa độ
                var smX1 = oldBox.x1 * (1 - smoothFactor) + det.x1 * smoothFactor
                var smY1 = oldBox.y1 * (1 - smoothFactor) + det.y1 * smoothFactor
                var smX2 = oldBox.x2 * (1 - smoothFactor) + det.x2 * smoothFactor
                var smY2 = oldBox.y2 * (1 - smoothFactor) + det.y2 * smoothFactor

                // An toàn tọa độ
                smX1 = smX1.coerceIn(0f, 1f)
                smY1 = smY1.coerceIn(0f, 1f)
                smX2 = max(smX1 + 0.01f, smX2.coerceIn(0f, 1f))
                smY2 = max(smY1 + 0.01f, smY2.coerceIn(0f, 1f))

                val smW = smX2 - smX1
                val smH = smY2 - smY1

                val newSmoothBox = BoundingBox(
                    x1 = smX1, y1 = smY1, x2 = smX2, y2 = smY2,
                    cx = smX1 + smW/2f, cy = smY1 + smH/2f, w = smW, h = smH,
                    cnf = finalConf,
                    cls = det.cls,
                    clsName = finalLabel, // Dùng tên đã bảo lưu
                    boxColor = finalColor // Dùng màu đã bảo lưu
                )

                // Cập nhật vào Map
                oldObj.box = det // Cập nhật box thô mới nhất
                oldObj.smoothBox = newSmoothBox
                oldObj.lastSeen = now

                updatedIds.add(id)

            } else {
                // --- TRƯỜNG HỢP: VẬT THỂ MỚI ---
                val id = nextId++

                // Vật mới thì phải chấp nhận thông tin từ YOLO
                val w = det.x2 - det.x1
                val h = det.y2 - det.y1
                val fixedDet = det.copy(w=w, h=h, cx=det.x1 + w/2f, cy=det.y1 + h/2f)

                val newObj = TrackedObject(
                    box = fixedDet,
                    lastSeen = now,
                    smoothBox = fixedDet,
                    status = "Không xác định được vị trí"
                )
                trackedObjects[id] = newObj
                updatedIds.add(id)
            }
        }

        // 2. Dọn dẹp rác (Xóa vật thể đã mất tích quá lâu)
        val expiredIds = trackedObjects.filter { (now - it.value.lastSeen) > maxInactiveTime }.keys
        expiredIds.forEach { trackedObjects.remove(it) }

        // 3. TRẢ VỀ KẾT QUẢ (Bao gồm cả những vật thể bị YOLO bỏ sót frame này nhưng chưa hết hạn)
        // Đây là bước giúp chống "Tắt phụt"
        return trackedObjects.values.toList()
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = max(box1.x1, box2.x1); val y1 = max(box1.y1, box2.y1)
        val x2 = min(box1.x2, box2.x2); val y2 = min(box1.y2, box2.y2)

        if (x2 <= x1 || y2 <= y1) return 0.0f

        val interArea = (x2 - x1) * (y2 - y1)
        val b1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val b2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)

        val union = b1Area + b2Area - interArea
        return if (union > 0) interArea / union else 0.0f
    }
}