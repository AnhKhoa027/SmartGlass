package com.example.smartglass.VisionGGApi

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import VisionRequest
import VisionImage
import VisionImageRequest
import VisionFeature
import VisionResponse
import ImageContext

object VisionRepository {

    fun detectObjects(base64Image: String, apiKey: String, callback: (VisionResponse?) -> Unit) {

        println("VISION >> Sending request... base64=${base64Image.length}")

        val request = VisionRequest(
            requests = listOf(
                VisionImageRequest(
                    image = VisionImage(content = base64Image),
                    features = listOf(
                        VisionFeature("OBJECT_LOCALIZATION", 10)
                    ),
                    imageContext = ImageContext()
                )
            )
        )

        VisionClient.instance.detectObjects(apiKey, request)
            .enqueue(object : Callback<VisionResponse> {

                override fun onResponse(
                    call: Call<VisionResponse>,
                    response: Response<VisionResponse>
                ) {
                    println("VISION >> HTTP ${response.code()}")

                    if (!response.isSuccessful) {
                        println("VISION >> ERROR BODY = ${response.errorBody()?.string()}")
                        callback(null)
                        return
                    }

                    val body = response.body()
                    if (body == null) {
                        println("VISION >> EMPTY BODY")
                        callback(null)
                        return
                    }

                    val count = body.responses
                        ?.firstOrNull()
                        ?.localizedObjectAnnotations
                        ?.size ?: 0

                    println("VISION >> Google Vision returned $count objects")

                    callback(body)
                }

                override fun onFailure(call: Call<VisionResponse>, t: Throwable) {
                    println("VISION >> FAILURE: ${t.message}")
                    callback(null)
                }
            })
    }
}
