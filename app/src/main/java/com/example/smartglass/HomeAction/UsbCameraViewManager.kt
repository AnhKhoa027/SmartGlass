package com.example.smartglass.HomeAction

import android.content.Context
import android.graphics.*
import android.view.SurfaceView
import android.widget.Toast
import com.example.smartglass.ObjectDetection.BoundingBox
import com.example.smartglass.ObjectDetection.OverlayView
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.camera.bean.CameraRequest
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory

/**
 * Quản lý camera USB (AUSBC 3.2.10)
 * - Hiển thị preview lên SurfaceView
 * - Lấy frame NV21 gửi sang YOLO xử lý
 * - Vẽ Overlay (bounding box)
 */
class UsbCameraViewManager(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val overlayView: OverlayView,
    private val detectionManager: DetectionManager?
) {
    private var cameraStrategy: CameraUvcStrategy? = null
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    private var previewWidth = 1280
    private var previewHeight = 720
    private var previewFps = 30

    fun startCamera() {
        try {
            cameraStrategy = CameraUvcStrategy(context).apply {
                addPreviewDataCallBack(object : IPreviewDataCallBack {
                    override fun onPreviewData(data: ByteArray?, format: IPreviewDataCallBack.DataFormat) {
                        if (data == null || format != IPreviewDataCallBack.DataFormat.NV21) return

                        scope.launch {
                            try {
                                val yuvImage = YuvImage(data, ImageFormat.NV21, previewWidth, previewHeight, null)
                                val out = ByteArrayOutputStream()
                                yuvImage.compressToJpeg(Rect(0, 0, previewWidth, previewHeight), 85, out)
                                val jpegBytes = out.toByteArray()
                                val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

                                //Gửi frame qua YOLO detect
                                detectionManager?.detectFrame(bitmap)

                                // (Phần Overlay được cập nhật thông qua DetectionManager.setOverlayResults)
                                // → không cần vẽ thủ công ở đây, OverlayView sẽ tự vẽ bounding boxes.

                                bitmap.recycle()
                                out.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                })

                // ✅ Bắt đầu preview lên SurfaceView
                startPreview(getCameraRequest(), surfaceView.holder)
            }

        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi mở camera: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /** Gọi từ DetectionManager để vẽ bounding box */
    fun setOverlayResults(results: List<BoundingBox>) {
        overlayView.setResults(results)
    }

    fun getOverlayWidth() = overlayView.width
    fun getOverlayHeight() = overlayView.height

    fun stopCamera() {
        try {
            cameraStrategy?.stopPreview()
        } catch (_: Exception) { }
        cameraStrategy = null
        job.cancel()
    }

    fun release() {
        stopCamera()
    }

    private fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setFrontCamera(false)
            .setPreviewWidth(previewWidth)
            .setPreviewHeight(previewHeight)
            .setContinuousAFModel(true)
            .setContinuousAutoModel(true)
            .create()
    }
}
