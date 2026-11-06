package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class FileCategory(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val extensions: Set<String>
) {
    ALL(
        title = "All",
        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
        color = Color(0xFF64748B),
        extensions = emptySet()
    ),
    FOLDERS(
        title = "Folders",
        icon = Icons.Default.Folder,
        color = Color(0xFFF59E0B), // Warm Amber
        extensions = emptySet()
    ),
    DOCUMENTS(
        title = "Documents",
        icon = Icons.Default.Description,
        color = Color(0xFF3B82F6), // Royal Blue
        extensions = setOf("txt", "pdf", "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx", "md", "rtf", "odt")
    ),
    IMAGES(
        title = "Images",
        icon = Icons.Default.Image,
        color = Color(0xFF8B5CF6), // Purple
        extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg")
    ),
    AUDIO(
        title = "Audio",
        icon = Icons.Default.AudioFile,
        color = Color(0xFFEC4899), // Pink
        extensions = setOf("mp3", "wav", "m4a", "ogg", "flac", "aac")
    ),
    VIDEOS(
        title = "Videos",
        icon = Icons.Default.VideoFile,
        color = Color(0xFFEF4444), // Crimson
        extensions = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp")
    ),
    CODE(
        title = "Code",
        icon = Icons.Default.Code,
        color = Color(0xFF10B981), // Emerald
        extensions = setOf("kt", "java", "json", "xml", "html", "css", "js", "ts", "py", "sh", "sql", "c", "cpp", "yaml", "yml", "gradle")
    ),
    ARCHIVES(
        title = "Archives",
        icon = Icons.Default.Archive,
        color = Color(0xFFF97316), // Orange
        extensions = setOf("zip", "rar", "7z", "tar", "gz")
    ),
    OTHER(
        title = "Other",
        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
        color = Color(0xFF6B7280),
        extensions = emptySet()
    );

    companion object {
        fun fromExtension(ext: String, isDir: Boolean): FileCategory {
            if (isDir) return FOLDERS
            val lower = ext.lowercase()
            return entries.firstOrNull { it != ALL && it != OTHER && it != FOLDERS && it.extensions.contains(lower) }
                ?: OTHER
        }
    }
}

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val category: FileCategory,
    val isFavorite: Boolean = false,
    val itemCount: Int = 0
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes, isDirectory, itemCount)

    companion object {
        fun formatFileSize(bytes: Long, isDir: Boolean, count: Int = 0): String {
            if (isDir) {
                return if (count == 1) "1 item" else "$count items"
            }
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0
            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }
            return if (unitIndex == 0) "${bytes} B" else String.format("%.1f %s", size, units[unitIndex])
        }
    }
}

enum class SortOption(val title: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    SIZE_DESC("Size (Largest first)"),
    SIZE_ASC("Size (Smallest first)")
}

enum class ViewMode {
    LIST,
    GRID
}

enum class ViewerType {
    NONE,
    TEXT_EDITOR,
    IMAGE_VIEWER,
    AUDIO_PLAYER,
    DETAILS_DIALOG
}

data class StorageStats(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val categorySizes: Map<FileCategory, Long> = emptyMap(),
    val totalFiles: Int = 0,
    val totalFolders: Int = 0
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
