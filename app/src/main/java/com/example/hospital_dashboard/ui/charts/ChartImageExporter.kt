package com.example.hospital_dashboard.ui.charts

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.widget.Toast
import androidx.core.view.drawToBitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChartImageExporter {

    fun exportChartAsJpeg(
        activity: Activity,
        chartBounds: androidx.compose.ui.geometry.Rect?,
        chartTitle: String,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val window = activity.window
        val decorView = window.decorView

        fun doSave(bmp: Bitmap) {
            saveBitmapToGallery(
                context = activity,
                bitmap = bmp,
                title = chartTitle,
                onSuccess = { path ->
                    Toast.makeText(activity, "📷 圖表圖片已儲存至：$path", Toast.LENGTH_LONG).show()
                    onSuccess(path)
                },
                onError = { err ->
                    Toast.makeText(activity, "❌ 儲存圖片失敗：$err", Toast.LENGTH_LONG).show()
                    onError(err)
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && chartBounds != null && chartBounds.width > 20 && chartBounds.height > 20) {
            val width = chartBounds.width.toInt().coerceAtMost(decorView.width)
            val height = chartBounds.height.toInt().coerceAtMost(decorView.height)
            val left = chartBounds.left.toInt().coerceAtLeast(0)
            val top = chartBounds.top.toInt().coerceAtLeast(0)

            val destBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val srcRect = Rect(left, top, left + width, top + height)

            try {
                PixelCopy.request(window, srcRect, destBitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        doSave(destBitmap)
                    } else {
                        // Fallback: drawToBitmap
                        val fallback = decorView.drawToBitmap()
                        val safeW = width.coerceAtMost(fallback.width - left)
                        val safeH = height.coerceAtMost(fallback.height - top)
                        if (safeW > 0 && safeH > 0) {
                            val cropped = Bitmap.createBitmap(fallback, left, top, safeW, safeH)
                            doSave(cropped)
                        } else {
                            doSave(fallback)
                        }
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                val fallback = decorView.drawToBitmap()
                doSave(fallback)
            }
        } else {
            val full = decorView.drawToBitmap()
            doSave(full)
        }
    }

    private fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        title: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.TAIWAN).format(Date())
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]"), "_").take(20)
            val filename = "Chart_${safeTitle}_${timeStamp}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HospitalDashboard")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    onSuccess("Pictures/HospitalDashboard/$filename")
                } else {
                    onError("無法建立媒體檔案 URI")
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "HospitalDashboard")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { os ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)
                }
                onSuccess(file.absolutePath)
            }
        } catch (e: Exception) {
            onError(e.message ?: "儲存過程發生例外")
        }
    }
}
