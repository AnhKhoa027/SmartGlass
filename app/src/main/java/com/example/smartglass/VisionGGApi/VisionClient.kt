import com.example.smartglass.VisionGGApi.VisionInterface
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object VisionClient {
    private const val BASE_URL = "https://vision.googleapis.com/v1/"

    val instance: VisionInterface by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VisionInterface::class.java)
    }
}
