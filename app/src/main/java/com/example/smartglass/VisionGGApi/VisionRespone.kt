data class VisionResponse(
    val responses: List<VisionDetectionResult>? = null
)

data class VisionDetectionResult(
    val localizedObjectAnnotations: List<DetectedObject>? = null
)

data class DetectedObject(
    val name: String,
    val score: Float,
    val boundingPoly: BoundingPoly
)

data class BoundingPoly(
    val normalizedVertices: List<Vertex>
)

data class Vertex(
    val x: Float?,
    val y: Float?
)