package com.maxvale.dzvinaplayer.ui.screens

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.height
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import com.maxvale.dzvinaplayer.ui.navigation.Screen
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AllFilesScreen(viewModel: MainViewModel) {
    val internalStorage = Environment.getExternalStorageDirectory()
    val currentDir by viewModel.currentDir.collectAsState()
    var files by remember(currentDir) { mutableStateOf(getFiles(currentDir)) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<File>() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedFiles.size} selected") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        val canFavorite = selectedFiles.all { it.isDirectory }
                        if (canFavorite && selectedFiles.isNotEmpty()) {
                            IconButton(onClick = {
                                selectedFiles.forEach { viewModel.addFavorite(it.absolutePath, it.name) }
                                selectionMode = false
                                selectedFiles.clear()
                            }) {
                                Icon(Icons.Filled.Star, contentDescription = "Favorite")
                            }
                        }
                        IconButton(onClick = {
                            selectedFiles.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
                            files = getFiles(currentDir)
                            selectionMode = false
                            selectedFiles.clear()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(currentDir.name.ifEmpty { "Internal Storage" }) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        if (currentDir != internalStorage && currentDir.parentFile != null) {
                            // Back functionality is in the list item for now
                        }
                    },
                    actions = {
                        var expanded by remember { mutableStateOf(false) }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Buy me a coffee") },
                                onClick = {
                                    expanded = false
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.buymeacoffee.com/"))
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    expanded = false
                                    viewModel.navController?.navigate(Screen.About.route)
                                }
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentDir != internalStorage && currentDir.parentFile != null) {
                item {
                    FileListItem(file = currentDir.parentFile!!, isParent = true, selected = false, onClick = {
                        if (selectionMode) {
                            // Don't select parent dir
                        } else {
                            viewModel.setCurrentDir(currentDir.parentFile!!)
                        }
                    }, onLongClick = {})
                    Divider()
                }
            }

            items(files) { file ->
                val isSelected = selectedFiles.contains(file)
                FileListItem(file = file, selected = isSelected, onClick = {
                    if (selectionMode) {
                        if (isSelected) selectedFiles.remove(file) else selectedFiles.add(file)
                        if (selectedFiles.isEmpty()) selectionMode = false
                    } else {
                        if (file.isDirectory) {
                            viewModel.setCurrentDir(file)
                        } else {
                            // Very basic extension check for media
                            if (file.extension.lowercase() in listOf("mp4", "mkv", "avi", "webm", "mp3", "flac", "wav")) {
                                viewModel.navController?.navigate(Screen.Player.createRoute(file.absolutePath))
                            }
                        }
                    }
                }, onLongClick = {
                    if (!selectionMode) {
                        selectionMode = true
                        selectedFiles.add(file)
                    }
                })
                Divider()
            }
        }
    }
}

fun getFiles(dir: File): List<File> {
    val files = dir.listFiles() ?: return emptyList()
    return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileListItem(file: File, isParent: Boolean = false, selected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (isParent) ".." else file.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    val favorites by viewModel.favorites.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }
    val selectedFavorites = remember { mutableStateListOf<com.maxvale.dzvinaplayer.data.FavoriteLocation>() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedFavorites.size} selected") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedFavorites.clear()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedFavorites.forEach { viewModel.removeFavorite(it) }
                            selectionMode = false
                            selectedFavorites.clear()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Favorites") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No favorite locations yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(favorites) { favorite ->
                    val isSelected = selectedFavorites.contains(favorite)
                    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        if (isSelected) selectedFavorites.remove(favorite) else selectedFavorites.add(favorite)
                                        if (selectedFavorites.isEmpty()) selectionMode = false
                                    } else {
                                        viewModel.setCurrentDir(File(favorite.path))
                                        viewModel.navController?.navigate(Screen.AllFiles.route) {
                                            viewModel.navController?.graph?.findStartDestination()?.id?.let { id ->
                                                popUpTo(id) {
                                                    saveState = true
                                                }
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedFavorites.add(favorite)
                                    }
                                }
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(favorite.name, style = MaterialTheme.typography.bodyLarge)
                            Text(favorite.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Divider()
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(viewModel: MainViewModel) {
    val recents by viewModel.recents.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }
    val selectedRecents = remember { mutableStateListOf<com.maxvale.dzvinaplayer.data.RecentVideo>() }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedRecents.size} selected") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        titleContentColor = MaterialTheme.colorScheme.onSecondary,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedRecents.clear()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            selectedRecents.forEach { viewModel.removeRecent(it) }
                            selectionMode = false
                            selectedRecents.clear()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Recent Watched") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    ) { innerPadding ->
        if (recents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No recent videos.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(recents) { recent ->
                    val isSelected = selectedRecents.contains(recent)
                    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        if (isSelected) selectedRecents.remove(recent) else selectedRecents.add(recent)
                                        if (selectedRecents.isEmpty()) selectionMode = false
                                    } else {
                                        viewModel.navController?.navigate(Screen.Player.createRoute(recent.path))
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedRecents.add(recent)
                                    }
                                }
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(recent.name, style = MaterialTheme.typography.bodyLarge)
                            val pos = recent.lastPositionMs / 1000
                            Text("Resumes at ${pos / 60}:${String.format("%02d", pos % 60)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Divider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("DźvinaPlayer", style = MaterialTheme.typography.headlineLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Created by Źmicier Biesau", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text("A simple, beautiful video player and file manager for Android.", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
