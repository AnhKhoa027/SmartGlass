//package com.example.smartglass.DetectResponse
//
//object VisionContext {
//
//    data class SeenObject(
//        val name: String,
//        val direction: String,
//        val distance: String,
//        val movement: String,
//        val time: Long = System.currentTimeMillis()
//    )
//
//    private val objects = mutableListOf<SeenObject>()
//
//    private const val TTL = 2000L // 2 giây
//
//    @Synchronized
//    fun update(newObjects: List<SeenObject>) {
//        objects.removeAll { System.currentTimeMillis() - it.time > TTL }
//        objects.addAll(newObjects)
//    }
//
//    @Synchronized
//    fun snapshot(): List<SeenObject> {
//        objects.removeAll { System.currentTimeMillis() - it.time > TTL }
//        return objects.toList()
//    }
//
//    fun clear() {
//        objects.clear()
//    }
//}
//
package com.example.smartglass.DetectResponse

object VisionContext {

    data class SeenObject(
        val name: String,
        val direction: String,
        val distance: String,
        val movement: String
    )

    private var lastObjects: List<SeenObject> = emptyList()
    private var lastUpdateTime: Long = 0
    private const val TTL = 3000L

    fun update(objects: List<SeenObject>) {
        lastObjects = objects
        lastUpdateTime = System.currentTimeMillis()
    }

    fun getContext(): List<SeenObject> {
        return if (System.currentTimeMillis() - lastUpdateTime < TTL)
            lastObjects
        else emptyList()
    }
}
