package com.example.smartglass.DetectResponse

object LabelTranslator {

    private val labelMap = mapOf(
        "person" to "người",
        "man" to "người đàn ông",
        "woman" to "phụ nữ",
        "child" to "trẻ em",
        "car" to "ô tô",
        "bus" to "xe buýt",
        "truck" to "xe tải",
        "motorcycle" to "xe máy",
        "bicycle" to "xe đạp",
        "dog" to "chó",
        "cat" to "mèo",
        "chair" to "ghế",
        "table" to "bàn",
        "phone" to "điện thoại",
        "laptop" to "máy tính xách tay",
        "backpack" to "ba lô",
        "bottle" to "chai nước",
        "traffic light" to "đèn giao thông",
        "stop sign" to "biển dừng",
        "glasses" to "kính",
        "plastic" to "bao ni lông"
    )

    fun toVietnamese(label: String?): String {
        if (label.isNullOrBlank()) return "vật thể"
        return labelMap[label.lowercase()] ?: label.lowercase()
    }
}
