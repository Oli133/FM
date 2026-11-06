package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FileManagerRepository
import com.example.model.FileCategory
import com.example.model.FileItem
import com.example.model.SortOption
import com.example.model.StorageStats
import com.example.model.ViewerType
import com.example.model.ViewMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileManagerRepository(application)

    private val _currentDirectory = MutableStateFlow<File>(repository.rootDir)
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<File>>(listOf(repository.rootDir))
    val breadcrumbs: StateFlow<List<File>> = _breadcrumbs.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NAME_ASC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _activeFile = MutableStateFlow<FileItem?>(null)
    val activeFile: StateFlow<FileItem?> = _activeFile.asStateFlow()

    private val _activeFileContent = MutableStateFlow("")
    val activeFileContent: StateFlow<String> = _activeFileContent.asStateFlow()

    private val _activeFileChecksum = MutableStateFlow("")
    val activeFileChecksum: StateFlow<String> = _activeFileChecksum.asStateFlow()

    private val _viewerType = MutableStateFlow(ViewerType.NONE)
    val viewerType: StateFlow<ViewerType> = _viewerType.asStateFlow()

    private val _allFolders = MutableStateFlow<List<File>>(emptyList())
    val allFolders: StateFlow<List<File>> = _allFolders.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val rootDir: File get() = repository.rootDir

    init {
        loadCurrentDirectory()
        refreshStorageStats()
    }

    fun loadCurrentDirectory() {
        viewModelScope.launch {
            val list = repository.listFiles(
                directory = _currentDirectory.value,
                searchQuery = _searchQuery.value,
                categoryFilter = _selectedCategory.value
            )
            _files.value = sortFiles(list, _sortOption.value)
            updateBreadcrumbs()
        }
    }

    private fun updateBreadcrumbs() {
        val list = mutableListOf<File>()
        var curr: File? = _currentDirectory.value
        val root = repository.rootDir

        while (curr != null) {
            list.add(0, curr)
            if (curr.absolutePath == root.absolutePath) break
            curr = curr.parentFile
        }

        // If current dir is not within root (e.g. external directory opened via SAF), make sure root is at beginning or keep list as is
        _breadcrumbs.value = list
    }

    private fun sortFiles(items: List<FileItem>, option: SortOption): List<FileItem> {
        // Folders always pinned to top for standard file explorer UX
        val (folders, regularFiles) = items.partition { it.isDirectory }

        val comparator = when (option) {
            SortOption.NAME_ASC -> compareBy<FileItem> { it.name.lowercase() }
            SortOption.NAME_DESC -> compareByDescending<FileItem> { it.name.lowercase() }
            SortOption.DATE_DESC -> compareByDescending<FileItem> { it.lastModified }
            SortOption.DATE_ASC -> compareBy<FileItem> { it.lastModified }
            SortOption.SIZE_DESC -> compareByDescending<FileItem> { it.sizeBytes }
            SortOption.SIZE_ASC -> compareBy<FileItem> { it.sizeBytes }
        }

        return folders.sortedWith(comparator) + regularFiles.sortedWith(comparator)
    }

    fun navigateTo(folder: File) {
        if (folder.exists() && folder.isDirectory) {
            _currentDirectory.value = folder
            clearSelection()
            loadCurrentDirectory()
        }
    }

    fun navigateUp(): Boolean {
        val current = _currentDirectory.value
        val parent = current.parentFile
        val root = repository.rootDir

        if (current.absolutePath != root.absolutePath && parent != null && parent.exists()) {
            _currentDirectory.value = parent
            clearSelection()
            loadCurrentDirectory()
            return true
        }
        return false
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadCurrentDirectory()
    }

    fun setSelectedCategory(category: FileCategory) {
        _selectedCategory.value = category
        loadCurrentDirectory()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        _files.value = sortFiles(_files.value, option)
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
    }

    fun toggleSelection(path: String) {
        val set = _selectedPaths.value.toMutableSet()
        if (set.contains(path)) set.remove(path) else set.add(path)
        _selectedPaths.value = set
    }

    fun selectAll() {
        _selectedPaths.value = _files.value.map { it.path }.toSet()
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
    }

    fun toggleFavorite(item: FileItem) {
        val newFav = repository.toggleFavorite(item.path)
        _files.value = _files.value.map {
            if (it.path == item.path) it.copy(isFavorite = newFav) else it
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.createFolder(_currentDirectory.value, name).fold(
                onSuccess = {
                    showMessage("Folder '${it.name}' created")
                    loadCurrentDirectory()
                    refreshStorageStats()
                },
                onFailure = {
                    showMessage("Error: ${it.message}")
                }
            )
        }
    }

    fun createFile(name: String, content: String = "") {
        viewModelScope.launch {
            repository.createFile(_currentDirectory.value, name, content).fold(
                onSuccess = {
                    showMessage("File '${it.name}' created")
                    loadCurrentDirectory()
                    refreshStorageStats()
                },
                onFailure = {
                    showMessage("Error: ${it.message}")
                }
            )
        }
    }

    fun rename(item: FileItem, newName: String) {
        viewModelScope.launch {
            val file = File(item.path)
            repository.rename(file, newName).fold(
                onSuccess = {
                    showMessage("Renamed to '${it.name}'")
                    loadCurrentDirectory()
                },
                onFailure = {
                    showMessage("Rename failed: ${it.message}")
                }
            )
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val paths = _selectedPaths.value
            var count = 0
            paths.forEach { path ->
                val f = File(path)
                if (repository.delete(f)) count++
            }
            clearSelection()
            showMessage("Deleted $count item(s)")
            loadCurrentDirectory()
            refreshStorageStats()
        }
    }

    fun deleteItem(item: FileItem) {
        viewModelScope.launch {
            if (repository.delete(File(item.path))) {
                showMessage("Deleted '${item.name}'")
                loadCurrentDirectory()
                refreshStorageStats()
            }
        }
    }

    fun prepareMoveOrCopy() {
        viewModelScope.launch {
            _allFolders.value = repository.getAllFolders()
        }
    }

    fun copySelected(targetDir: File) {
        viewModelScope.launch {
            val paths = _selectedPaths.value
            var count = 0
            paths.forEach { path ->
                if (repository.copy(File(path), targetDir).isSuccess) count++
            }
            clearSelection()
            showMessage("Copied $count item(s) to ${targetDir.name}")
            loadCurrentDirectory()
            refreshStorageStats()
        }
    }

    fun moveSelected(targetDir: File) {
        viewModelScope.launch {
            val paths = _selectedPaths.value
            var count = 0
            paths.forEach { path ->
                if (repository.move(File(path), targetDir).isSuccess) count++
            }
            clearSelection()
            showMessage("Moved $count item(s) to ${targetDir.name}")
            loadCurrentDirectory()
            refreshStorageStats()
        }
    }

    fun openFile(item: FileItem) {
        if (item.isDirectory) {
            navigateTo(File(item.path))
            return
        }

        _activeFile.value = item
        when (item.category) {
            FileCategory.DOCUMENTS, FileCategory.CODE -> {
                viewModelScope.launch {
                    _activeFileContent.value = repository.readText(File(item.path))
                    _viewerType.value = ViewerType.TEXT_EDITOR
                }
            }
            FileCategory.IMAGES -> {
                _viewerType.value = ViewerType.IMAGE_VIEWER
            }
            FileCategory.AUDIO -> {
                _viewerType.value = ViewerType.AUDIO_PLAYER
            }
            else -> {
                // Show file details/inspect dialog for other files
                showDetails(item)
            }
        }
    }

    fun showDetails(item: FileItem) {
        _activeFile.value = item
        viewModelScope.launch {
            _activeFileChecksum.value = repository.calculateChecksum(File(item.path), "MD5")
            _viewerType.value = ViewerType.DETAILS_DIALOG
        }
    }

    fun closeViewer() {
        _viewerType.value = ViewerType.NONE
        _activeFile.value = null
        _activeFileContent.value = ""
    }

    fun saveActiveFileContent(newContent: String) {
        val item = _activeFile.value ?: return
        viewModelScope.launch {
            if (repository.writeText(File(item.path), newContent)) {
                _activeFileContent.value = newContent
                showMessage("Saved changes to '${item.name}'")
                loadCurrentDirectory()
                refreshStorageStats()
            } else {
                showMessage("Failed to save changes")
            }
        }
    }

    fun importUris(uris: List<Uri>) {
        viewModelScope.launch {
            var count = 0
            uris.forEach { uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/')
                if (repository.importFromUri(uri, _currentDirectory.value, name).isSuccess) {
                    count++
                }
            }
            showMessage("Imported $count file(s)")
            loadCurrentDirectory()
            refreshStorageStats()
        }
    }

    fun cleanCache() {
        viewModelScope.launch {
            val freed = repository.clearCache()
            val formatted = FileItem.formatFileSize(freed, false)
            showMessage("Cleaned $formatted of temporary cache")
            refreshStorageStats()
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getStorageStats()
        }
    }

    private fun showMessage(msg: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(msg)
        }
    }
}
