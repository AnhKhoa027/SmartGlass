package com.example.smartglass.VisionGGApi
import VisionRequest
import VisionResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
interface VisionInterface {
    @POST("images:annotate")
    fun detectObjects(
        @Query("key") apiKey: String,
        @Body body: VisionRequest
    ): Call<VisionResponse>
}



