package com.example.smartglass.HomeAction

import android.graphics.*
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import com.example.smartglass.ObjectDetection.BoundingBox
import com.example.smartglass.ObjectDetection.OverlayView
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class CameraViewManager(
    private val textureView: TextureView,
    private val glassIcon: ImageView,
    private val overlayView: OverlayView
) : TextureView.SurfaceTextureListener {

    private var ws: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var lastFrameTime = 0L
    private var surface: Surface? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var currentIp: String? = null
    private var isConnecting = false

    init {
        textureView.surfaceTextureListener = this
    }

    /** Hiển thị stream từ ESP32 và chạy YOLO detect song song */
    fun showStream(
        ip: String,
        detectionManager: DetectionManager? = null,
        onConnected: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        if (isConnecting) return
        isConnecting = true
        currentIp = ip

        // reset canvas và lastFrame khi connect lại
        surface?.let { s ->
            val canvas = s.lockCanvas(null)
            canvas?.let {
                it.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
                s.unlockCanvasAndPost(it)
            }
        }
        detectionManager?.lastFrame = null

        textureView.visibility = View.VISIBLE
        glassIcon.visibility = View.GONE

        val request = Request.Builder().url("ws://$ip:81/").build()
        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("WebSocket connected to ESP32 ($ip)")
                isConnecting = false
                onConnected?.invoke()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("WebSocket failure: ${t.message}")
                isConnecting = false
                onFailed?.invoke()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                println("⚠WebSocket closing: $reason")
                webSocket.close(1000, null)
                isConnecting = false
                onFailed?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val now = System.currentTimeMillis()
                if (now - lastFrameTime < 50) return
                lastFrameTime = now

                val s = surface ?: return

                scope.launch {
                    try {
                        // Decode frame từ ESP32
                        val bitmap =
                            BitmapFactory.decodeStream(ByteArrayInputStream(bytes.toByteArray()))
                                ?: return@launch

                        // Lưu ảnh gốc cho YOLO (không lật)
                        detectionManager?.lastFrame = bitmap

                        // --- YOLO detect ---
                        launch(Dispatchers.Default) {
                            detectionManager?.detectFrame(bitmap)
                        }

                        // --- Vẽ ảnh (đã lật) lên TextureView ---
                        withContext(Dispatchers.Main) {
                            try {
                                val canvas = s.lockCanvas(null)
                                if (canvas != null) {
                                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)

                                    // ✅ Lật ngang ảnh khi hiển thị
                                    val matrix = Matrix().apply {
                                        preScale(-1f, 1f, canvas.width / 2f, canvas.height / 2f)
                                    }
                                    canvas.setMatrix(matrix)

                                    canvas.drawBitmap(
                                        bitmap,
                                        null,
                                        Rect(0, 0, canvas.width, canvas.height),
                                        paint
                                    )

                                    s.unlockCanvasAndPost(canvas)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Không recycle bitmap (YOLO còn dùng)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    fun showGlassIcon() {
        textureView.visibility = View.GONE
        glassIcon.visibility = View.VISIBLE
        overlayView.clear()
        disconnect()
    }

    fun release() {
        disconnect()
        scope.cancel()
        surface?.release()
        surface = null
    }

    private fun disconnect() {
        ws?.close(1000, "User disconnect")
        ws = null
        isConnecting = false
    }

    fun setOverlayResults(results: List<BoundingBox>) {
        overlayView.setResults(results)
    }

    fun getOverlayWidth() = overlayView.width
    fun getOverlayHeight() = overlayView.height

    override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
        surface = Surface(st)
    }

    override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
    override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
        surface?.release()
        surface = null
        return true
    }
    override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}

    /** Reconnect nếu mất kết nối */
    private fun reconnectWithDelay(detectionManager: DetectionManager?) {
        scope.launch {
            delay(3000)
            currentIp?.let { showStream(it, detectionManager) }
        }
    }
}
