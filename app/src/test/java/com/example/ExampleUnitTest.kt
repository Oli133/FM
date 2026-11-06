package com.example

import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFileCategoryClassification() {
        assertEquals(FileCategory.FOLDERS, FileCategory.fromExtension("", isDir = true))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromExtension("pdf", isDir = false))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromExtension("md", isDir = false))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromExtension("csv", isDir = false))
        assertEquals(FileCategory.IMAGES, FileCategory.fromExtension("jpg", isDir = false))
        assertEquals(FileCategory.IMAGES, FileCategory.fromExtension("png", isDir = false))
        assertEquals(FileCategory.AUDIO, FileCategory.fromExtension("mp3", isDir = false))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromExtension("mp4", isDir = false))
        assertEquals(FileCategory.CODE, FileCategory.fromExtension("kt", isDir = false))
        assertEquals(FileCategory.CODE, FileCategory.fromExtension("json", isDir = false))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromExtension("zip", isDir = false))
        assertEquals(FileCategory.OTHER, FileCategory.fromExtension("unknown_ext", isDir = false))
    }

    @Test
    fun testFormattedFileSize() {
        assertEquals("0 items", FileItem.formatFileSize(0, isDir = true, count = 0))
        assertEquals("1 item", FileItem.formatFileSize(0, isDir = true, count = 1))
        assertEquals("5 items", FileItem.formatFileSize(0, isDir = true, count = 5))

        assertEquals("0 B", FileItem.formatFileSize(0, isDir = false))
        assertEquals("500 B", FileItem.formatFileSize(500, isDir = false))
        assertEquals("1.0 KB", FileItem.formatFileSize(1024, isDir = false))
        assertEquals("2.5 MB", FileItem.formatFileSize(2621440, isDir = false))
    }

    @Test
    fun testFileSorting() {
        val f1 = FileItem(
            id = "1", name = "beta.txt", path = "/beta.txt", isDirectory = false,
            sizeBytes = 200, lastModified = 1000L, extension = "txt", mimeType = "text/plain",
            category = FileCategory.DOCUMENTS
        )
        val f2 = FileItem(
            id = "2", name = "alpha.txt", path = "/alpha.txt", isDirectory = false,
            sizeBytes = 500, lastModified = 2000L, extension = "txt", mimeType = "text/plain",
            category = FileCategory.DOCUMENTS
        )
        val dir = FileItem(
            id = "3", name = "docs", path = "/docs", isDirectory = true,
            sizeBytes = 0, lastModified = 1500L, extension = "", mimeType = "inode/directory",
            category = FileCategory.FOLDERS
        )

        val list = listOf(f1, f2, dir)

        // Folders always pinned to top
        val (folders, files) = list.partition { it.isDirectory }
        val sortedByName = folders.sortedBy { it.name.lowercase() } + files.sortedBy { it.name.lowercase() }
        assertEquals("docs", sortedByName[0].name)
        assertEquals("alpha.txt", sortedByName[1].name)
        assertEquals("beta.txt", sortedByName[2].name)

        val sortedBySizeDesc = folders + files.sortedByDescending { it.sizeBytes }
        assertEquals("docs", sortedBySizeDesc[0].name)
        assertEquals("alpha.txt", sortedBySizeDesc[1].name) // 500 bytes
        assertEquals("beta.txt", sortedBySizeDesc[2].name)  // 200 bytes
    }
}
