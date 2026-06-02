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

    /**
     * Lista todos los archivos de media soportados en [dir] (no recursivo),
     * ordenados por nombre. Archivos con extensiones no reconocidas son ignorados.
     */
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

    /**
     * Guarda bytes en [dir] con nombre [name].
     * Path traversal prevention incluido.
     * IMPORTANTE: El llamante es responsable de validar que la extensión
     * sea un tipo soportado (usa [mediaTypeOf]) antes de llamar este método.
     */
    fun saveMedia(dir: File, name: String, bytes: ByteArray) {
        val safe = File(name).name
        File(dir, safe).writeBytes(bytes)
    }
}
