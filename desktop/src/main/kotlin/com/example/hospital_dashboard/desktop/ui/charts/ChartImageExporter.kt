package com.example.hospital_dashboard.desktop.ui.charts

import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import java.awt.Color
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalGraphicsContext

@Composable
fun rememberGraphicsLayer(): GraphicsLayer {
    val graphicsContext = LocalGraphicsContext.current
    val layer = remember(graphicsContext) {
        graphicsContext.createGraphicsLayer()
    }
    DisposableEffect(graphicsContext, layer) {
        onDispose {
            graphicsContext.releaseGraphicsLayer(layer)
        }
    }
    return layer
}

object ChartImageExporter {

    suspend fun captureToBufferedImage(graphicsLayer: GraphicsLayer): BufferedImage? {
        return try {
            val imageBitmap = graphicsLayer.toImageBitmap()
            val awtImage = imageBitmap.toAwtImage()
            val rgbImage = BufferedImage(awtImage.width, awtImage.height, BufferedImage.TYPE_INT_RGB)
            val g2d = rgbImage.createGraphics()
            g2d.color = Color.WHITE
            g2d.fillRect(0, 0, awtImage.width, awtImage.height)
            g2d.drawImage(awtImage, 0, 0, null)
            g2d.dispose()
            rgbImage
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportChartAsJpeg(
        bufferedImage: BufferedImage,
        chartTitle: String,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.TAIWAN).format(Date())
        val safeTitle = chartTitle.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]"), "_").take(20)
        val defaultName = "Chart_${safeTitle}_${timeStamp}.jpg"

        SwingUtilities.invokeLater {
            try {
                val dialog = FileDialog(null as Frame?, "儲存圖表圖片 (JPEG)", FileDialog.SAVE)
                dialog.file = defaultName
                dialog.isVisible = true
                val f = dialog.file
                val d = dialog.directory
                if (f != null && d != null) {
                    var outFile = File(d, f)
                    if (!outFile.name.lowercase().endsWith(".jpg") && !outFile.name.lowercase().endsWith(".jpeg")) {
                        outFile = File(d, "${f}.jpg")
                    }
                    val ok = saveJpegToFile(bufferedImage, outFile)
                    if (ok) {
                        onSuccess(outFile.absolutePath)
                    } else {
                        onError("無法將圖檔寫入：${outFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "儲存過程發生例外")
            }
        }
    }

    fun saveJpegToFile(bufferedImage: BufferedImage, targetFile: File): Boolean {
        return try {
            ImageIO.write(bufferedImage, "JPEG", targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun copyToClipboard(
        bufferedImage: BufferedImage,
        onSuccess: () -> Unit = {}
    ) {
        try {
            val transferable = object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
                override fun getTransferData(flavor: DataFlavor): Any {
                    if (flavor == DataFlavor.imageFlavor) return bufferedImage
                    throw UnsupportedFlavorException(flavor)
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
            onSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
