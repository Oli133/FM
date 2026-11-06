package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.FileItem
import com.example.model.SortOption
import com.example.model.ViewerType
import com.example.model.ViewMode
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.CategoryFilterBar
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.FileDetailsDialog
import com.example.ui.components.FileGridItem
import com.example.ui.components.FileListItem
import com.example.ui.components.FolderDestinationDialog
import com.example.ui.components.RenameDialog
import com.example.ui.components.StorageSummaryCard
import com.example.ui.viewers.AudioPlayerSheet
import com.example.ui.viewers.ImageViewerScreen
import com.example.ui.viewers.TextEditorScreen
import com.example.viewmodel.FileManagerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerApp(
    viewModel: FileManagerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentDir by viewModel.currentDirectory.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.breadcrumbs.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val activeFile by viewModel.activeFile.collectAsStateWithLifecycle()
    val activeFileContent by viewModel.activeFileContent.collectAsStateWithLifecycle()
    val activeFileChecksum by viewModel.activeFileChecksum.collectAsStateWithLifecycle()
    val viewerType by viewModel.viewerType.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var destinationActionType by remember { mutableStateOf<String?>(null) } // "COPY" or "MOVE"
    var showFabMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // SAF Import Launcher
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    // Collect snackbars
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Back handling
    BackHandler(enabled = true) {
        when {
            viewerType != ViewerType.NONE -> viewModel.closeViewer()
            selectedPaths.isNotEmpty() -> viewModel.clearSelection()
            isSearchActive -> {
                isSearchActive = false
                viewModel.setSearchQuery("")
            }
            viewModel.navigateUp() -> { /* handled */ }
            else -> {
                // At root, let system handle or finish
                (context as? android.app.Activity)?.finish()
            }
        }
    }

    // Fullscreen Viewers
    if (viewerType == ViewerType.TEXT_EDITOR && activeFile != null) {
        TextEditorScreen(
            fileItem = activeFile!!,
            initialContent = activeFileContent,
            onSave = { updated -> viewModel.saveActiveFileContent(updated) },
            onClose = { viewModel.closeViewer() }
        )
        return
    }

    if (viewerType == ViewerType.IMAGE_VIEWER && activeFile != null) {
        ImageViewerScreen(
            fileItem = activeFile!!,
            onClose = { viewModel.closeViewer() },
            onShare = { shareFile(context, activeFile!!) }
        )
        return
    }

    val isMultiSelect = selectedPaths.isNotEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search files & folders...", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_input"),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else if (isMultiSelect) {
                        Text(
                            text = "${selectedPaths.size} selected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        val isRoot = currentDir.absolutePath == viewModel.rootDir.absolutePath
                        Text(
                            text = if (isRoot) "File Manager" else currentDir.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    } else if (isMultiSelect) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    } else {
                        val isRoot = currentDir.absolutePath == viewModel.rootDir.absolutePath
                        if (!isRoot) {
                            IconButton(onClick = { viewModel.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                            }
                        }
                    }
                },
                actions = {
                    if (!isSearchActive && !isMultiSelect) {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag("search_toggle_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }

                        IconButton(
                            onClick = { viewModel.toggleViewMode() },
                            modifier = Modifier.testTag("view_mode_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle view mode"
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("sort_menu_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option.title,
                                                fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                                color = if (option == sortOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortOption(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { viewModel.selectAll() },
                            modifier = Modifier.testTag("select_mode_button")
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select items")
                        }
                    } else if (isMultiSelect) {
                        IconButton(onClick = {
                            if (selectedPaths.size == files.size) viewModel.clearSelection() else viewModel.selectAll()
                        }) {
                            Text(
                                text = if (selectedPaths.size == files.size) "Deselect" else "All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (isMultiSelect) {
                BottomAppBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    actions = {
                        IconButton(
                            onClick = {
                                destinationActionType = "COPY"
                                viewModel.prepareMoveOrCopy()
                            },
                            modifier = Modifier.testTag("multi_copy_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }

                        IconButton(
                            onClick = {
                                destinationActionType = "MOVE"
                                viewModel.prepareMoveOrCopy()
                            },
                            modifier = Modifier.testTag("multi_move_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move")
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            modifier = Modifier.testTag("multi_delete_btn")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isMultiSelect) {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(
                        visible = showFabMenu,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    showCreateFolderDialog = true
                                },
                                icon = { Icon(Icons.Default.CreateNewFolder, null) },
                                text = { Text("New Folder") },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag("fab_new_folder")
                            )

                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    showCreateFileDialog = true
                                },
                                icon = { Icon(Icons.Default.NoteAdd, null) },
                                text = { Text("New File") },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag("fab_new_file")
                            )

                            ExtendedFloatingActionButton(
                                onClick = {
                                    showFabMenu = false
                                    importFilesLauncher.launch(arrayOf("*/*"))
                                },
                                icon = { Icon(Icons.Default.FileUpload, null) },
                                text = { Text("Import from Device") },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .testTag("fab_import_files")
                            )
                        }
                    }

                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.testTag("main_fab")
                    ) {
                        Icon(
                            imageVector = if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = if (showFabMenu) "Close menu" else "Add item"
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Breadcrumbs
            BreadcrumbBar(
                breadcrumbs = breadcrumbs,
                rootDir = viewModel.rootDir,
                onNavigate = { viewModel.navigateTo(it) }
            )

            // Storage Overview
            StorageSummaryCard(
                stats = storageStats,
                onCleanCache = { viewModel.cleanCache() }
            )

            // Category Chips
            CategoryFilterBar(
                selectedCategory = selectedCategory,
                onSelectCategory = { viewModel.setSelectedCategory(it) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Main List or Grid
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No files match '$searchQuery'" else "This folder is empty",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the + button to create a folder, file, or import from your device.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else if (viewMode == ViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("files_list_view"),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(files, key = { it.path }) { item ->
                        FileListItem(
                            item = item,
                            isSelected = selectedPaths.contains(item.path),
                            isMultiSelectMode = isMultiSelect,
                            onClick = { viewModel.openFile(item) },
                            onLongClick = { viewModel.toggleSelection(item.path) },
                            onToggleSelect = { viewModel.toggleSelection(item.path) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onRename = { itemToRename = item },
                            onCopy = {
                                viewModel.toggleSelection(item.path)
                                destinationActionType = "COPY"
                                viewModel.prepareMoveOrCopy()
                            },
                            onMove = {
                                viewModel.toggleSelection(item.path)
                                destinationActionType = "MOVE"
                                viewModel.prepareMoveOrCopy()
                            },
                            onDelete = { itemToDelete = item },
                            onShare = { shareFile(context, item) },
                            onDetails = { viewModel.showDetails(item) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .testTag("files_grid_view"),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.path }) { item ->
                        FileGridItem(
                            item = item,
                            isSelected = selectedPaths.contains(item.path),
                            isMultiSelectMode = isMultiSelect,
                            onClick = { viewModel.openFile(item) },
                            onLongClick = { viewModel.toggleSelection(item.path) },
                            onToggleSelect = { viewModel.toggleSelection(item.path) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                            onRename = { itemToRename = item },
                            onCopy = {
                                viewModel.toggleSelection(item.path)
                                destinationActionType = "COPY"
                                viewModel.prepareMoveOrCopy()
                            },
                            onMove = {
                                viewModel.toggleSelection(item.path)
                                destinationActionType = "MOVE"
                                viewModel.prepareMoveOrCopy()
                            },
                            onDelete = { itemToDelete = item },
                            onShare = { shareFile(context, item) },
                            onDetails = { viewModel.showDetails(item) }
                        )
                    }
                }
            }

            // Audio Player Sheet (if active)
            if (viewerType == ViewerType.AUDIO_PLAYER && activeFile != null) {
                AudioPlayerSheet(
                    fileItem = activeFile!!,
                    onClose = { viewModel.closeViewer() }
                )
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                showCreateFolderDialog = false
                viewModel.createFolder(name)
            }
        )
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { name, content ->
                showCreateFileDialog = false
                viewModel.createFile(name, content)
            }
        )
    }

    itemToRename?.let { item ->
        RenameDialog(
            initialName = item.name,
            onDismiss = { itemToRename = null },
            onConfirm = { newName ->
                itemToRename = null
                viewModel.rename(item, newName)
            }
        )
    }

    itemToDelete?.let { item ->
        DeleteConfirmDialog(
            itemCount = 1,
            singleItemName = item.name,
            onDismiss = { itemToDelete = null },
            onConfirm = {
                itemToDelete = null
                viewModel.deleteItem(item)
            }
        )
    }

    if (showDeleteSelectedDialog) {
        DeleteConfirmDialog(
            itemCount = selectedPaths.size,
            onDismiss = { showDeleteSelectedDialog = false },
            onConfirm = {
                showDeleteSelectedDialog = false
                viewModel.deleteSelected()
            }
        )
    }

    destinationActionType?.let { action ->
        FolderDestinationDialog(
            folders = allFolders,
            title = if (action == "COPY") "Copy to Folder" else "Move to Folder",
            onDismiss = { destinationActionType = null },
            onSelectFolder = { targetFolder ->
                if (action == "COPY") {
                    viewModel.copySelected(targetFolder)
                } else {
                    viewModel.moveSelected(targetFolder)
                }
                destinationActionType = null
            }
        )
    }

    if (viewerType == ViewerType.DETAILS_DIALOG && activeFile != null) {
        FileDetailsDialog(
            item = activeFile!!,
            checksum = activeFileChecksum,
            onDismiss = { viewModel.closeViewer() },
            onShare = { shareFile(context, activeFile!!) },
            onOpenExternal = { openWithExternalApp(context, activeFile!!) }
        )
    }
}

private fun getFileUri(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
}

private fun shareFile(context: Context, item: FileItem) {
    try {
        val file = File(item.path)
        val uri = getFileUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${item.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openWithExternalApp(context: Context, item: FileItem) {
    try {
        val file = File(item.path)
        val uri = getFileUri(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with..."))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
    }
}
