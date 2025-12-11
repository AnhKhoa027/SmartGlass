package com.example.smartglass.VisionGGApi

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object ToBase64 {
    fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
