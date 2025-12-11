
data class VisionRequest(
    val requests: List<VisionImageRequest>
)

data class VisionImageRequest(
    val image: VisionImage,
    val features: List<VisionFeature>,
    val imageContext: ImageContext? = null
)

data class VisionImage(
    val content: String
)

data class VisionFeature(
    val type: String,
    val maxResults: Int
)

data class ImageContext(
    val languageHints: List<String>? = null
)
