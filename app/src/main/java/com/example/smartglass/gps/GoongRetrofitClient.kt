package com.example.smartglass.gps

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val GOONG_BASE_URL = "https://rsapi.goong.io/"

object GoongRetrofitClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Khởi tạo Retrofit cho Goong
    private val goongRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl(GOONG_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Service Goong (Service mới cần dùng)
    val goongApi: GoongInterface by lazy {
        goongRetrofit.create(GoongInterface::class.java)
    }
}