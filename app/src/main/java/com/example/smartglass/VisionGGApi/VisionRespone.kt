data class VisionResponse(
    val responses: List<VisionDetectionResult>
)

data class VisionDetectionResult(
    val localizedObjectAnnotations: List<DetectedObject>?
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
