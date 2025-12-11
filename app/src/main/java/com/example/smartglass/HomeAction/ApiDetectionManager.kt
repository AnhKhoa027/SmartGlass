package com.example.smartglass.HomeAction

import android.graphics.Bitmap
import com.example.smartglass.VisionGGApi.ToBase64
import com.example.smartglass.VisionGGApi.VisionRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ApiObjectResult(
    val label: String,
    val score: Float
)

class ApiDetectionManager(private val apiKey: String) {

    suspend fun detectFrame(bitmap: Bitmap): List<ApiObjectResult> =
        suspendCancellableCoroutine { cont ->

            println("API >> Bitmap input size = ${bitmap.width}x${bitmap.height}")

            val base64 = ToBase64.bitmapToBase64(bitmap)
            println("API >> Base64 length = ${base64.length}")

            VisionRepository.detectObjects(base64, apiKey) { response ->
                if (response == null) {
                    println("API >> RESPONSE NULL")
                    cont.resume(emptyList())
                    return@detectObjects
                }

                val annotations =
                    response.responses
                        ?.firstOrNull()
                        ?.localizedObjectAnnotations
                        ?: emptyList()

                println("API >> Returned ${annotations.size} objects")

                val result = annotations.map {
                    ApiObjectResult(
                        label = it.name,
                        score = it.score
                    )
                }

                cont.resume(result)
            }
        }
}
