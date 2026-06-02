package com.freekiosk.app

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `listPdfs returns only pdf files sorted by name`() {
        val dir = tmp.newFolder("pdfs")
        File(dir, "b.pdf").createNewFile()
        File(dir, "a.pdf").createNewFile()
        File(dir, "image.png").createNewFile()

        val result = FileManager.listPdfs(dir)

        assertEquals(2, result.size)
        assertEquals("a.pdf", result[0].name)
        assertEquals("b.pdf", result[1].name)
    }

    @Test
    fun `listPdfs returns empty list when dir is empty`() {
        val dir = tmp.newFolder("empty")
        assertEquals(emptyList<File>(), FileManager.listPdfs(dir))
    }

    @Test
    fun `deleteFile removes file from disk`() {
        val dir = tmp.newFolder("pdfs")
        val file = File(dir, "test.pdf").also { it.createNewFile() }

        FileManager.deleteFile(file)

        assertFalse(file.exists())
    }

    @Test
    fun `savePdf writes bytes to destination`() {
        val dir = tmp.newFolder("pdfs")
        val bytes = "PDF content".toByteArray()

        FileManager.savePdf(dir, "new.pdf", bytes)

        val saved = File(dir, "new.pdf")
        assertTrue(saved.exists())
        assertArrayEquals(bytes, saved.readBytes())
    }

    @Test
    fun `mediaTypeOf returns PDF for pdf extension`() {
        assertEquals(MediaType.PDF, FileManager.mediaTypeOf(File("doc.pdf")))
    }

    @Test
    fun `mediaTypeOf returns IMAGE for jpg, png, webp`() {
        assertEquals(MediaType.IMAGE, FileManager.mediaTypeOf(File("photo.jpg")))
        assertEquals(MediaType.IMAGE, FileManager.mediaTypeOf(File("photo.PNG")))
        assertEquals(MediaType.IMAGE, FileManager.mediaTypeOf(File("photo.webp")))
    }

    @Test
    fun `mediaTypeOf returns VIDEO for mp4, mov, mkv`() {
        assertEquals(MediaType.VIDEO, FileManager.mediaTypeOf(File("clip.mp4")))
        assertEquals(MediaType.VIDEO, FileManager.mediaTypeOf(File("clip.MOV")))
        assertEquals(MediaType.VIDEO, FileManager.mediaTypeOf(File("clip.mkv")))
    }

    @Test
    fun `mediaTypeOf returns null for unknown extension`() {
        assertNull(FileManager.mediaTypeOf(File("file.docx")))
    }

    @Test
    fun `listMedia returns PDFs images and videos sorted by name`() {
        val dir = tmp.newFolder("media")
        File(dir, "c.mp4").createNewFile()
        File(dir, "b.jpg").createNewFile()
        File(dir, "a.pdf").createNewFile()
        File(dir, "ignore.docx").createNewFile()

        val result = FileManager.listMedia(dir)

        assertEquals(3, result.size)
        assertEquals("a.pdf", result[0].file.name)
        assertEquals("b.jpg", result[1].file.name)
        assertEquals("c.mp4", result[2].file.name)
        assertEquals(MediaType.PDF,   result[0].type)
        assertEquals(MediaType.IMAGE, result[1].type)
        assertEquals(MediaType.VIDEO, result[2].type)
    }

    @Test
    fun `saveMedia writes bytes and prevents path traversal`() {
        val dir = tmp.newFolder("media")
        val bytes = "data".toByteArray()

        FileManager.saveMedia(dir, "../evil.pdf", bytes)

        assertFalse(File(dir.parent, "evil.pdf").exists())
        assertTrue(File(dir, "evil.pdf").exists())
    }

    @Test
    fun `mediaTypeOf returns null for file with no extension`() {
        assertNull(FileManager.mediaTypeOf(File("noextension")))
    }

    @Test
    fun `mediaTypeOf covers all image extensions`() {
        listOf("jpeg", "gif", "bmp").forEach { ext ->
            assertEquals(MediaType.IMAGE, FileManager.mediaTypeOf(File("file.$ext")))
        }
    }

    @Test
    fun `mediaTypeOf covers all video extensions`() {
        listOf("avi", "webm", "3gp").forEach { ext ->
            assertEquals(MediaType.VIDEO, FileManager.mediaTypeOf(File("file.$ext")))
        }
    }
}
