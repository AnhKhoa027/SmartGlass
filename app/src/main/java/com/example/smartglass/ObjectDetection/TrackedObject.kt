package com.example.smartglass.ObjectDetection

data class TrackedObject(
    var box: BoundingBox,
    var lastSeen: Long,
    var smoothBox: BoundingBox,
    var direction: String? = null,
    var status: String = "standing",
    var lastDeepCheckTime: Long = 0
)

object ModelPaths {

    // ================================
    // 🔹 YOLOv8 Object Detection
    // ================================
    const val YOLO_MODEL = "yolov8n_int8.tflite"
    const val YOLO_LABEL = "example_label_file.txt"

    // ================================
    // 🔹 Image Classification (CellPhone, Mouse, Tree)
    // ================================
    const val CLASSIFY_MODEL = "model_meta.tflite"
    const val CLASSIFY_LABEL = "label_model.txt"
}