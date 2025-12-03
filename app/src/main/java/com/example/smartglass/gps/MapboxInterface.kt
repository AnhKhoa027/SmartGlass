package com.example.smartglass.gps
import MapboxResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
interface MapboxInterface {
    @GET("directions/v5/mapbox/walking/{coordinates}")
    fun getDirections(
        @Path("coordinates") coordinates:String,
        @Query("steps") steps: Boolean=true,
        @Query("geometries") geometries: String="geojson",//định dạng dữ liệu hình học (geojson) hoặc (polyline)
        @Query("access_token") token: String,
        @Query("language")  language:String="vi"
    ): Call<MapboxResponse>
    @GET("geocoding/v5/mapbox.places/{place}.json")
    fun getGeocoding(
        @Path("place") place: String,
        @Query("access_token") token:String,

        ): Call<GeocodingResponse>
}