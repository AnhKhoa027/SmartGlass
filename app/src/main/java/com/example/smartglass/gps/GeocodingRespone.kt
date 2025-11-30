package com.example.smartglass.gps
/*
{
  "features": [
    {
      "place_name": "Bệnh viện Đà Nẵng, Hải Châu, Việt Nam",
      "center": [108.2235, 16.0713]
    }
  ]
}
Viết data class con để xử lí dữ liệu chuển hóa địa điểm thành tọa độ
 */

data class GeocodingResponse(
    val features: List<GeocodingFeature>
)

data class GeocodingFeature(
    val place_name: String,
    val center: List<Double> // [lon, lat]
)