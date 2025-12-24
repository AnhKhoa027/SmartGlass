package com.example.smartglass.DetectResponse

object LabelTranslator {

    private val labelMap = mapOf(
        "Person" to "người",
        "Man" to "người đàn ông",
        "Woman" to "phụ nữ",
        "Child" to "trẻ em",
        "Car" to "ô tô",
        "Bus" to "xe buýt",
        "Truck" to "xe tải",
        "Motorcycle" to "xe máy",
        "Bicycle" to "xe đạp",
        "Dog" to "chó",
        "Cat" to "mèo",
        "Chair" to "ghế",
        "Table" to "bàn",
        "Mobile phone" to "điện thoại",
        "Laptop" to "Laptop",
        "Backpack" to "ba lô",
        "Bottle" to "chai nước",
        "Traffic light" to "đèn giao thông",
        "Stop sign" to "biển báo dừng",
        "Glasses" to "kính",
        "Plastic" to "bao ni lông",
        "Mouse" to "chuột máy tính",
    )

    fun toVietnamese(label: String?): String {
        if (label.isNullOrBlank()) return "vật thể"
        return labelMap[label.lowercase()] ?: label.lowercase()
    }
}
