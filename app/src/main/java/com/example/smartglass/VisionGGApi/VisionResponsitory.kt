import com.example.smartglass.gps.RetrofitClient
import com.example.smartglass.VisionGGApi.VisionInterface
import retrofit2.Call

object VisionRepository {

    fun detectObjects(base64Image: String, apiKey: String, callback: (VisionResponse?) -> Unit) {

        val request = VisionRequest(
            requests = listOf(
                VisionImageRequest(
                    image = VisionImage(content = base64Image),
                    features = listOf(
                        VisionFeature("OBJECT_LOCALIZATION", 10)
                    )
                )
            )
        )

        VisionClient.instance.detectObjects(apiKey, request)
            .enqueue(object : retrofit2.Callback<VisionResponse> {
                override fun onResponse(
                    call: Call<VisionResponse>,
                    response: retrofit2.Response<VisionResponse>
                ) {
                    callback(response.body())
                }

                override fun onFailure(call: Call<VisionResponse>, t: Throwable) {
                    callback(null)
                }
            })
    }
}
