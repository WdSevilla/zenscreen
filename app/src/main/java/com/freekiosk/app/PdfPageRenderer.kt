package com.freekiosk.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

class PdfPageRenderer(file: File) : Closeable {

    private val fd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(fd)

    val pageCount: Int get() = renderer.pageCount

    fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap {
        val page = renderer.openPage(pageIndex)
        val pageW = page.width.toFloat()
        val pageH = page.height.toFloat()

        // Escalar para llenar el ancho completo — elimina barras negras laterales
        // Si la página es más alta que la pantalla, escalar por altura en su lugar
        val scaleByWidth = targetWidth / pageW
        val scaleByHeight = targetHeight / pageH
        val scale = maxOf(scaleByWidth, scaleByHeight)

        val bmpW = (pageW * scale).toInt().coerceAtLeast(1)
        val bmpH = (pageH * scale).toInt().coerceAtLeast(1)

        val pageBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        pageBitmap.eraseColor(Color.WHITE)
        page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        // Centrar — si el PDF es más alto que la pantalla se recorta levemente
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        result.eraseColor(Color.BLACK)
        val canvas = Canvas(result)
        val left = ((targetWidth - bmpW) / 2f)
        val top = ((targetHeight - bmpH) / 2f)
        canvas.drawBitmap(pageBitmap, left, top, null)
        pageBitmap.recycle()

        return result
    }

    override fun close() {
        renderer.close()
        fd.close()
    }
}
