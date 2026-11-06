package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class FileManagerRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("file_manager_prefs", Context.MODE_PRIVATE)

    val rootDir: File by lazy {
        val external = context.getExternalFilesDir(null)
        val base = if (external != null && external.canWrite()) external else context.filesDir
        val workspace = File(base, "Storage")
        if (!workspace.exists()) {
            workspace.mkdirs()
        }
        workspace
    }

    private val favoritesSet: MutableSet<String>
        get() = prefs.getStringSet("favorite_paths", emptySet())?.toMutableSet() ?: mutableSetOf()

    init {
        initializeSampleWorkspaceIfNeeded()
    }

    private fun initializeSampleWorkspaceIfNeeded() {
        val initializedKey = "workspace_initialized_v2"
        if (!prefs.getBoolean(initializedKey, false)) {
            try {
                // Create subdirectories
                val docsDir = File(rootDir, "Documents").apply { mkdirs() }
                val imagesDir = File(rootDir, "Images").apply { mkdirs() }
                val codeDir = File(rootDir, "Code").apply { mkdirs() }
                val notesDir = File(rootDir, "Notes").apply { mkdirs() }

                // Create Welcome Guide
                val welcome = File(rootDir, "Welcome_Guide.txt")
                welcome.writeText(
                    """
                    ========================================
                    WELCOME TO FILE MANAGER
                    ========================================

                    Explore, manage, and inspect files with speed and elegance:

                    * Navigate: Tap folders to browse, tap breadcrumbs to jump back.
                    * View & Edit: Open any text or code file to view or edit directly.
                    * Media: Tap photos to preview in high resolution or play audio clips.
                    * Actions: Long-press or tap item menus to Rename, Copy, Move, Share, or Delete.
                    * Multi-Select: Tap selection icons to perform batch actions.
                    * Storage Analyzer: Tap the top storage indicator to see category breakdowns.
                    * Import / Export: Use the '+' button to import files directly from your Android device.

                    Created with Material 3 & Jetpack Compose.
                    """.trimIndent()
                )

                // Create Project Roadmap Markdown
                val roadmap = File(docsDir, "Project_Roadmap.md")
                roadmap.writeText(
                    """
                    # Mobile Architecture Roadmap 🚀

                    ## Objectives
                    - Modern reactive UI using Jetpack Compose
                    - Strict adherence to Material Design 3 guidelines
                    - High performance local I/O and zero-permission storage

                    ## Milestones
                    1. [x] Core File Explorer architecture
                    2. [x] In-app Text & Code editor
                    3. [x] Image inspection and zoom preview
                    4. [x] Storage category analytics
                    5. [x] Storage Access Framework (SAF) integration

                    ## Notes
                    All files are safely stored in app-scoped and device-accessible storage.
                    """.trimIndent()
                )

                // Create Budget CSV
                val budget = File(docsDir, "Quarterly_Budget.csv")
                budget.writeText(
                    """
                    Category,Q1 Budget,Q2 Budget,Actual Spent,Status
                    Engineering,$45000,$48000,$42500,Under Budget
                    Design & UX,$15000,$16000,$15200,On Track
                    Cloud Infrastructure,$12000,$13500,$12800,On Track
                    Marketing,$20000,$22000,$21400,On Track
                    Total,$92000,$99500,$91900,Healthy
                    """.trimIndent()
                )

                // Create Code file
                val codeConfig = File(codeDir, "AppConfig.json")
                codeConfig.writeText(
                    """
                    {
                      "appName": "File Manager",
                      "version": "1.0.0",
                      "theme": "material_you",
                      "features": {
                        "textEditor": true,
                        "imageViewer": true,
                        "audioPlayer": true,
                        "storageAnalytics": true,
                        "multiSelect": true
                      },
                      "cacheLimitMb": 256
                    }
                    """.trimIndent()
                )

                // Create Kotlin script
                val script = File(codeDir, "BackupTask.kt")
                script.writeText(
                    """
                    package com.example.tasks

                    import java.io.File

                    object BackupTask {
                        fun execute(target: File): Boolean {
                            println("Executing incremental backup to " + target.name)
                            return true
                        }
                    }
                    """.trimIndent()
                )

                // Create Notes file
                val notes = File(notesDir, "QuickNotes.txt")
                notes.writeText(
                    """
                    Team Sync Highlights:
                    - Release candidate scheduled for end of week
                    - Testing edge-to-edge window insets on Android 15
                    - Verified Google Play policies compliance
                    """.trimIndent()
                )

                // Copy sample wallpaper image if available in resources
                try {
                    val resId = context.resources.getIdentifier("sample_nature_wallpaper_1793978583567", "drawable", context.packageName)
                    if (resId != 0) {
                        context.resources.openRawResource(resId).use { input ->
                            val targetImg = File(imagesDir, "Mountain_Twilight.jpg")
                            FileOutputStream(targetImg).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (_: Exception) { }

                prefs.edit().putBoolean(initializedKey, true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun listFiles(
        directory: File,
        searchQuery: String = "",
        categoryFilter: FileCategory = FileCategory.ALL
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }

        val rawFiles = directory.listFiles() ?: return@withContext emptyList()
        val favorites = favoritesSet

        rawFiles.map { file ->
            val isDir = file.isDirectory
            val ext = if (isDir) "" else file.extension.lowercase()
            val category = FileCategory.fromExtension(ext, isDir)
            val count = if (isDir) (file.listFiles()?.size ?: 0) else 0
            val size = if (isDir) calculateFolderSize(file) else file.length()
            val mime = getMimeType(file)

            FileItem(
                id = file.absolutePath,
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                sizeBytes = size,
                lastModified = file.lastModified(),
                extension = ext,
                mimeType = mime,
                category = category,
                isFavorite = favorites.contains(file.absolutePath),
                itemCount = count
            )
        }.filter { item ->
            // Search filter
            val matchesQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.extension.contains(searchQuery, ignoreCase = true)

            // Category filter
            val matchesCategory = when (categoryFilter) {
                FileCategory.ALL -> true
                FileCategory.FOLDERS -> item.isDirectory
                else -> !item.isDirectory && item.category == categoryFilter
            }

            matchesQuery && matchesCategory
        }
    }

    suspend fun getAllFolders(root: File = rootDir): List<File> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<File>()
        folders.add(root)
        fun collect(dir: File) {
            dir.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                folders.add(sub)
                collect(sub)
            }
        }
        collect(root)
        folders
    }

    private fun calculateFolderSize(folder: File): Long {
        var length = 0L
        val files = folder.listFiles() ?: return 0L
        for (file in files) {
            length += if (file.isFile) file.length() else calculateFolderSize(file)
        }
        return length
    }

    suspend fun createFolder(parent: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        val sanitized = sanitizeFilename(name)
        if (sanitized.isBlank()) return@withContext Result.failure(IllegalArgumentException("Invalid folder name"))
        val target = File(parent, sanitized)
        if (target.exists()) return@withContext Result.failure(IllegalStateException("Folder already exists"))
        if (target.mkdirs()) Result.success(target) else Result.failure(Exception("Failed to create folder"))
    }

    suspend fun createFile(parent: File, name: String, content: String = ""): Result<File> = withContext(Dispatchers.IO) {
        val sanitized = sanitizeFilename(name)
        if (sanitized.isBlank()) return@withContext Result.failure(IllegalArgumentException("Invalid file name"))
        val target = File(parent, sanitized)
        if (target.exists()) return@withContext Result.failure(IllegalStateException("File already exists"))
        try {
            if (target.createNewFile()) {
                if (content.isNotEmpty()) {
                    target.writeText(content)
                }
                Result.success(target)
            } else {
                Result.failure(Exception("Failed to create file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        val sanitized = sanitizeFilename(newName)
        if (sanitized.isBlank()) return@withContext Result.failure(IllegalArgumentException("Invalid name"))
        val target = File(file.parentFile, sanitized)
        if (target.exists()) return@withContext Result.failure(IllegalStateException("A file with this name already exists"))
        if (file.renameTo(target)) {
            // Update favorites if needed
            val favs = favoritesSet
            if (favs.remove(file.absolutePath)) {
                favs.add(target.absolutePath)
                prefs.edit().putStringSet("favorite_paths", favs).apply()
            }
            Result.success(target)
        } else {
            Result.failure(Exception("Rename failed"))
        }
    }

    suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) {
        val favs = favoritesSet
        favs.remove(file.absolutePath)
        prefs.edit().putStringSet("favorite_paths", favs).apply()
        file.deleteRecursively()
    }

    suspend fun copy(source: File, targetDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val target = getUniqueTarget(targetDir, source.name)
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = true)
            } else {
                source.copyTo(target, overwrite = true)
            }
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun move(source: File, targetDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val target = getUniqueTarget(targetDir, source.name)
            if (source.renameTo(target)) {
                Result.success(target)
            } else {
                // Fallback copy then delete
                val copyResult = copy(source, targetDir)
                if (copyResult.isSuccess) {
                    source.deleteRecursively()
                    copyResult
                } else {
                    Result.failure(Exception("Move failed"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readText(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) return@withContext ""
        try {
            file.readText()
        } catch (e: Exception) {
            "Unable to read file as text: ${e.message}"
        }
    }

    suspend fun writeText(file: File, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importFromUri(uri: Uri, targetDir: File, fileName: String?): Result<File> = withContext(Dispatchers.IO) {
        try {
            val name = sanitizeFilename(fileName ?: "Imported_${System.currentTimeMillis()}")
            val target = getUniqueTarget(targetDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open input stream"))
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun toggleFavorite(path: String): Boolean {
        val favs = favoritesSet
        val isFav = if (favs.contains(path)) {
            favs.remove(path)
            false
        } else {
            favs.add(path)
            true
        }
        prefs.edit().putStringSet("favorite_paths", favs).apply()
        return isFav
    }

    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(rootDir.path)
            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

            val categorySizes = mutableMapOf<FileCategory, Long>()
            var filesCount = 0
            var foldersCount = 0

            fun scanDir(dir: File) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        foldersCount++
                        scanDir(file)
                    } else {
                        filesCount++
                        val size = file.length()
                        val cat = FileCategory.fromExtension(file.extension, false)
                        categorySizes[cat] = (categorySizes[cat] ?: 0L) + size
                    }
                }
            }

            scanDir(rootDir)

            StorageStats(
                totalBytes = totalBytes,
                usedBytes = usedBytes,
                freeBytes = freeBytes,
                categorySizes = categorySizes,
                totalFiles = filesCount,
                totalFolders = foldersCount
            )
        } catch (e: Exception) {
            StorageStats()
        }
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        try {
            val cache = context.cacheDir
            val size = calculateFolderSize(cache)
            cache.deleteRecursively()
            cache.mkdirs()
            size
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun calculateChecksum(file: File, algorithm: String = "MD5"): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) return@withContext "N/A"
        try {
            val digest = MessageDigest.getInstance(algorithm)
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun getUniqueTarget(dir: File, originalName: String): File {
        var target = File(dir, originalName)
        if (!target.exists()) return target

        val nameWithoutExt = target.nameWithoutExtension
        val ext = target.extension
        var counter = 1
        while (target.exists()) {
            val newName = if (ext.isNotEmpty()) "$nameWithoutExt ($counter).$ext" else "$nameWithoutExt ($counter)"
            target = File(dir, newName)
            counter++
        }
        return target
    }

    private fun sanitizeFilename(name: String): String {
        return name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) return "inode/directory"
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
