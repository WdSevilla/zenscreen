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
}
