package com.example.smartglass.ObjectDetection

data class TrackedObject(
    var box: BoundingBox,
    var lastSeen: Long,
    var smoothBox: BoundingBox,
    var direction: String? = null,
    var status: String = "standing"
)
