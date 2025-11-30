/*
Viết data class con để hứng dữ liệu từ mapbox trả về
Dạng json mà mapbox sẽ trả về
{
  "routes": [
    {
      "legs": [
        {
          "steps": [ ... ]
        }
      ]
    }
  ]
}


 */
//Gốc json
data class MapboxResponse(
    val routes: List<MapboxRoute>
)
//Một tuyến đường hoàn chỉnh
data class MapboxRoute(
    val distance: Double, // Tổng khoảng cách (meters)
    val duration: Double, // Tổng thời gian (seconds)
    val legs: List<MapboxLeg>
)
//Một chặn của tuyến đường
data class MapboxLeg(
    val steps: List<MapboxStep>
)
//Một hành động cụ thể
data class MapboxStep(
    val distance: Double,
    val duration: Double,
    val name: String?,
    val mode: String?,
    val maneuver: MapboxManeuver
)
//Nội dung chỉ dẫn
data class MapboxManeuver(
    val instruction: String,
    val location: List<Double> // [lon, lat] của điểm ngoặt
)
