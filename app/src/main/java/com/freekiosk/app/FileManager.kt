package com.freekiosk.app

import android.content.Context
import java.io.File

enum class MediaType { PDF, IMAGE, VIDEO }

data class MediaFile(val file: File, val type: MediaType)

object FileManager {

    private val PDF_EXTS   = setOf("pdf")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")

    fun getPdfsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "pdfs")
        dir.mkdirs()
        return dir
    }

    fun mediaTypeOf(file: File): MediaType? {
        return when (file.extension.lowercase()) {
            in PDF_EXTS   -> MediaType.PDF
            in IMAGE_EXTS -> MediaType.IMAGE
            in VIDEO_EXTS -> MediaType.VIDEO
            else          -> null
        }
    }

    fun listMedia(dir: File): List<MediaFile> =
        dir.listFiles()
            ?.mapNotNull { f -> mediaTypeOf(f)?.let { MediaFile(f, it) } }
            ?.sortedBy { it.file.name }
            ?: emptyList()

    fun listPdfs(dir: File): List<File> =
        listMedia(dir).filter { it.type == MediaType.PDF }.map { it.file }

    fun deleteFile(file: File) {
        file.delete()
    }

    fun savePdf(dir: File, name: String, bytes: ByteArray) = saveMedia(dir, name, bytes)

    fun saveMedia(dir: File, name: String, bytes: ByteArray) {
        val safe = File(name).name
        File(dir, safe).writeBytes(bytes)
    }
}
