package com.example.smartglass.gps

import com.google.gson.annotations.SerializedName

// --- LỚP CHÍNH CỦA PHẢN HỒI ---
data class GoongDirectionResponse(
    // Giữ lại status để kiểm tra lỗi
    val status: String?,
    val routes: List<GoongRoute>?
)

// --- LỚP ROUTE ---
data class GoongRoute(
    // Lớp này không chứa distance/duration trong format Google/Mẫu của bạn
    val legs: List<GoongLeg>?
)

// --- LỚP LEG (CHẶNG ĐƯỜNG) ---
data class GoongLeg(
    // CHỨA TỔNG KHOẢNG CÁCH VÀ THỜI GIAN CỦA CHẶNG ĐƯỜNG (Dạng đối tượng)
    val distance: GoongValue?,
    val duration: GoongValue?,

    // Danh sách các bước chỉ đường
    val steps: List<GoongStep>?
)

// --- LỚP BƯỚC ĐI (STEP) ---
data class GoongStep(
    // KHOẢNG CÁCH VÀ THỜI GIAN CỦA BƯỚC (Dạng đối tượng)
    val distance: GoongValue?,
    val duration: GoongValue?,

    // HƯỚNG DẪN RẼ BẰNG VĂN BẢN
    @SerializedName("html_instructions")
    val htmlInstructions: String?,

    // ĐIỂM BẮT ĐẦU VÀ KẾT THÚC CỦA BƯỚC
    @SerializedName("start_location")
    val startLocation: GoongRouteLocation?, // SỬ DỤNG TÊN ĐẶC BIỆT ĐỂ TRÁNH TRÙNG LỚP GoongLocation của Geocoding
    @SerializedName("end_location")
    val endLocation: GoongRouteLocation? // SỬ DỤNG TÊN ĐẶC BIỆT ĐỂ TRÁNH TRÙNG LỚP GoongLocation của Geocoding
)

// --- LỚP DỮ LIỆU GIÁ TRỊ (CHO DISTANCE/DURATION TEXT VÀ VALUE) ---
data class GoongValue(
    // Trường 'value' (số) là cần thiết để tính toán chính xác (meters/seconds)
    val value: Int?,
    // Trường 'text' để đọc (ví dụ: "200 m")
    val text: String? = null
)

// --- LỚP VỊ TRÍ CHO DIRECTIONS (CẦN TẠO LẠI ĐỂ TRÁNH TRÙNG VỚI GEOCoding) ---
// Giả định bạn đã đổi tên lớp GoongLocation trong Geocoding thành GoongGeocoingLocation.
data class GoongRouteLocation(
    val lat: Double, // Vĩ độ (Latitude)
    val lng: Double  // Kinh độ (Longitude)
)