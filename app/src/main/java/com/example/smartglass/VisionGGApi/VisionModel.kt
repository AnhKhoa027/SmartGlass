data class VisionRequest(
    val requests: List<VisionImageRequest>
)

data class VisionImageRequest(
    val image: VisionImage,
    val features: List<VisionFeature>
)

data class VisionImage(
    val content: String
)

data class VisionFeature(
    val type: String,
    val maxResults: Int
)
