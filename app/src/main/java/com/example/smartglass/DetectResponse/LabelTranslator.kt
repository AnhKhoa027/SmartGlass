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
        "mobile phone" to "điện thoại",
        "laptop" to "laptop",
        "backpack" to "ba lô",
        "bottle" to "chai nước",
        "traffic light" to "đèn giao thông",
        "stop sign" to "biển báo dừng",
        "glasses" to "kính",
        "plastic" to "bao ni lông",
        "mouse" to "chuột máy tính"
    )

    fun toVietnamese(label: String?): String {
        if (label.isNullOrBlank()) return "vật thể"

        val key = label.trim().lowercase()

        labelMap.entries.firstOrNull {
            key.contains(it.key)
        }?.let {
            return it.value
        }
        return key
    }
}