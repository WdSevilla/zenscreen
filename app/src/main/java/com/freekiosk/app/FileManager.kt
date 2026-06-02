package com.freekiosk.app

import android.content.Context
import java.io.File

object FileManager {

    fun getPdfsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "pdfs")
        dir.mkdirs()
        return dir
    }

    fun listPdfs(dir: File): List<File> =
        dir.listFiles { f -> f.extension.lowercase() == "pdf" }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun deleteFile(file: File) {
        file.delete()
    }

    fun savePdf(dir: File, name: String, bytes: ByteArray) {
        val safe = File(name).name  // prevent path traversal
        File(dir, safe).writeBytes(bytes)
    }
}
