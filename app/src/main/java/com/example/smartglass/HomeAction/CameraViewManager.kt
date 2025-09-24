package com.example.smartglass.HomeAction


import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import com.example.smartglass.ObjectDetection.OverlayView
import com.example.smartglass.ObjectDetection.BoundingBox

/**
 * CameraViewManager
 * -------------------
 * File này quản lý giao diện camera (WebView stream từ Xiaocam 32S3) và icon "glasses".
 * Được gọi trong HomeFragment.
 *
 * - showWebView(): hiển thị video stream từ Xiaocam 32S3.
 * - showGlassIcon(): hiển thị icon mặc định khi chưa kết nối.
 * - release(): giải phóng WebView khi fragment bị hủy.
 *
 * Liên quan: HomeFragment (chỗ điều phối), OverlayView (hiển thị bounding boxes).
 */
class CameraViewManager(
    private val webView: WebView,
    private val glassIcon: ImageView,
    private val overlayView: OverlayView
) {
    /** Hiển thị WebView (ESP32 stream qua IP:81/stream) */
    fun showWebView(ip: String) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = WebViewClient()
        webView.visibility = View.VISIBLE
        glassIcon.visibility = View.GONE
        webView.loadUrl("http://$ip:81/stream")
    }

    /** Hiển thị icon kính (dùng khi chưa kết nối hoặc ngắt kết nối) */
    fun showGlassIcon() {
        webView.visibility = View.GONE
        glassIcon.visibility = View.VISIBLE
        overlayView.clear()
    }

    /** Giải phóng WebView để tránh memory leak */
    fun release() {
        webView.apply {
            stopLoading()
            clearHistory()
            loadUrl("about:blank")
            removeAllViews()
            destroy()
        }
    }

    /** Xóa overlay (khung nhận diện) */
    fun clearOverlay() {
        overlayView.clear()
    }

    /** Cập nhật overlay với bounding box từ YOLO */
    fun setOverlayResults(results: List<BoundingBox>) {
        overlayView.setResults(results)
    }

    fun getOverlayWidth() = overlayView.width
    fun getOverlayHeight() = overlayView.height
}
