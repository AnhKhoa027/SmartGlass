package com.example.smartglass.gps

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interface định nghĩa các API Endpoint của Goong Map.
 * Base URL: https://rs.goong.io/
 */
interface GoongInterface {

    @GET("v2/geocode")
    fun getGeocoding(
        @Query("address") address: String,
        @Query("api_key") apiKey: String,
        @Query("location") location: String
    ): Call<GoongGeocodingResponse>

    @GET("v2/direction")
    fun getDirections(
        // Tọa độ điểm bắt đầu (format: lon,lat)
        @Query("origin") origin: String,
        // Tọa độ điểm đích (format: lon,lat)
        @Query("destination") destination: String,
        // Loại phương tiện: car, motorcycle, bike, foot
        @Query("vehicle") vehicle: String,
        @Query("api_key") apiKey: String,
        // Có thể thêm tham số 'language' nếu Goong hỗ trợ.
    ): Call<GoongDirectionResponse>
}