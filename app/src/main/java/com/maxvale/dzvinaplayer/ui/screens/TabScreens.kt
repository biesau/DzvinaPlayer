package com.maxvale.dzvinaplayer.ui.screens

import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.maxvale.dzvinaplayer.data.FavoriteLocation
import com.maxvale.dzvinaplayer.data.FtpServer
import com.maxvale.dzvinaplayer.data.RecentVideo
import com.maxvale.dzvinaplayer.ui.navigation.Screen
import com.maxvale.dzvinaplayer.ui.theme.PrimaryDarkRed
import com.maxvale.dzvinaplayer.utils.MediaStoreHelper.deleteFile
import com.maxvale.dzvinaplayer.utils.MediaStoreHelper.getMediaInFolder
import java.io.File

private const val buyMeACoffee = "https://buymeacoffee.com/zmicier"

@Composable
fun AllFilesScreen(viewModel: MainViewModel) {
    val browseScope by viewModel.browseScope.collectAsState()

    when (browseScope) {
        BrowseScope.HOME -> SourcesHomeScreen(viewModel)
        BrowseScope.LOCAL -> LocalFilesScreen(viewModel)
        BrowseScope.FTP_ROOT -> FtpServersScreen(viewModel)
        BrowseScope.FTP_BROWSE -> FtpBrowseScreen(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LocalFilesScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val internalStorage = Environment.getExternalStorageDirectory()
    val currentDir by viewModel.currentDir.collectAsState()
    var files by remember(currentDir) { mutableStateOf(getFiles(context, currentDir)) }
    
    // Refresh files when returning to foreground (in case user changed Selected Photos)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            files = getFiles(context, currentDir)
        }
    }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<File>() }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showPermissionDialog by remember { mutableStateOf(false) }

    val performDelete = { targets: List<File> ->
        val requiresAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
        if (requiresAllFilesAccess) {
            showPermissionDialog = true
        } else {
            scope.launch(Dispatchers.IO) {
                // Delete actual files and folders recursively
                targets.forEach { target ->
                    try {
                        if (target.isDirectory) {
                            target.deleteRecursively()
                        } else {
                            target.delete()
                        }
                    } catch (e: Exception) {}
                }

                // Also delete their references in the MediaStore database
                val allFiles = mutableListOf<File>()
                targets.forEach { target ->
                    if (target.isDirectory) {
                        target.walkTopDown().forEach { file ->
                            if (file.isFile) allFiles.add(file)
                        }
                    } else {
                        allFiles.add(target)
                    }
                }
                
                allFiles.forEach { file ->
                    try {
                        val uri = com.maxvale.dzvinaplayer.utils.MediaStoreHelper.getUriForFile(context, file)
                        if (uri != null) {
                            context.contentResolver.delete(uri, null, null)
                        }
                    } catch (e: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    files = getFiles(context, currentDir)
                    if (selectionMode) {
                        selectionMode = false
                        selectedFiles.clear()
                    }
                }
            }
        }
    }

    BackHandler(enabled = currentDir != internalStorage && currentDir.parentFile != null || selectionMode) {
        if (selectionMode) {
            selectionMode = false
            selectedFiles.clear()
        } else {
            viewModel.setCurrentDir(currentDir.parentFile!!)
        }
    }

    BackHandler(enabled = currentDir == internalStorage && !selectionMode) {
        viewModel.setBrowseScope(BrowseScope.HOME)
    }

    Scaffold(
        containerColor = PrimaryDarkRed,
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedFiles.size} selected" else if (currentDir == internalStorage) "Internal Storage" else currentDir.name) },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { selectionMode = false; selectedFiles.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    } else if (currentDir != internalStorage) {
                        IconButton(onClick = { viewModel.setCurrentDir(currentDir.parentFile!!) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            performDelete(selectedFiles.toList())
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
            val favorites by viewModel.favorites.collectAsState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }

            items(files) { file ->
                val isSelected = selectedFiles.contains(file)
                val isFavorite = favorites.any { it.path == file.absolutePath }
                FileListItem(file = file, selected = isSelected, isFavorite = isFavorite, onFavoriteToggle = {
                    if (isFavorite) {
                        favorites.firstOrNull { it.path == file.absolutePath }?.let { viewModel.removeFavorite(it) }
                    } else {
                        viewModel.addFavorite(path = file.absolutePath, name = file.name)
                    }
                }, onClick = {
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
                }, onDeleteClick = if (selectionMode) null else { {
                    performDelete(listOf(file))
                } })
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
            }
        }

        if (showPermissionDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permission Required") },
                text = { Text("To delete files and folders, DzvinaPlayer requires \"All Files Access\" permission. Please enable it in the system settings.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showPermissionDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

fun getFiles(context: android.content.Context, dir: File): List<File> {
    return getMediaInFolder(context, dir.absolutePath).map { it.toFile() }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: File,
    isParent: Boolean = false,
    selected: Boolean = false,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
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
            imageVector = if (file.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
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
        } else {
            if (!isParent && onFavoriteToggle != null) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)
                    )
                }
            }
            if (!isParent && onDeleteClick != null) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { expanded = false; onDeleteClick() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesHomeScreen(viewModel: MainViewModel) {
    Scaffold(
        containerColor = PrimaryDarkRed,
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                ListItemRow(title = "Local Storage", icon = Icons.Filled.Storage, onClick = {
                    viewModel.setCurrentDir(Environment.getExternalStorageDirectory())
                    viewModel.setBrowseScope(BrowseScope.LOCAL)
                })
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                ListItemRow(title = "FTP Servers", icon = Icons.Filled.Cloud, onClick = {
                    viewModel.setBrowseScope(BrowseScope.FTP_ROOT)
                })
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }
    }
}

@Composable
fun ListItemRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, onDeleteClick: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        
        if (onDeleteClick != null) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { expanded = false; onDeleteClick() })
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(viewModel: MainViewModel) {
    val favorites by viewModel.favorites.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }
    val selectedFavorites = remember { mutableStateListOf<FavoriteLocation>() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshFavoritesAvailability()
    }

    Scaffold(
        containerColor = PrimaryDarkRed,
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedFavorites.size} selected" else "Favorites") },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { selectionMode = false; selectedFavorites.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            selectedFavorites.forEach { viewModel.removeFavorite(it) }
                            selectionMode = false
                            selectedFavorites.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove selected")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No favorite locations yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                                        val file = File(favorite.path)
                                        if (file.isDirectory) {
                                            viewModel.setBrowseScope(BrowseScope.LOCAL)
                                            viewModel.setCurrentDir(file)
                                            viewModel.navController?.navigate(Screen.AllFiles.route) {
                                                viewModel.navController?.graph?.findStartDestination()?.id?.let { id ->
                                                    popUpTo(id) { saveState = true }
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        } else {
                                            viewModel.navController?.navigate(Screen.Player.createRoute(file.absolutePath))
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
                        Icon(if (File(favorite.path).isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(favorite.name, style = MaterialTheme.typography.bodyLarge)
                            Text(favorite.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
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
    val selectedRecents = remember { mutableStateListOf<RecentVideo>() }

    Scaffold(
        containerColor = PrimaryDarkRed,
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "${selectedRecents.size} selected" else "Recent") },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = { selectionMode = false; selectedRecents.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            selectedRecents.forEach { viewModel.removeRecent(it) }
                            selectionMode = false
                            selectedRecents.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove selected")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
        if (recents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recent videos.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
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
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpServersScreen(viewModel: MainViewModel) {
    val servers by viewModel.ftpServers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.setBrowseScope(BrowseScope.HOME)
    }

    Scaffold(
        containerColor = PrimaryDarkRed,
        topBar = {
            TopAppBar(
                title = { Text("FTP Servers") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setBrowseScope(BrowseScope.HOME) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
        if (servers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No FTP servers configured", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(servers) { server ->
                    ListItemRow(title = server.name, icon = Icons.Filled.Cloud, onClick = {
                        viewModel.connectToFtp(server)
                    }, onDeleteClick = {
                        viewModel.removeFtpServer(server)
                    })
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }
        }

        if (showAddDialog) {
            var name by remember { mutableStateOf("") }
            var host by remember { mutableStateOf("") }
            var port by remember { mutableStateOf("21") }
            var user by remember { mutableStateOf("anonymous") }
            var pass by remember { mutableStateOf("") }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add FTP Server") },
                text = {
                    Column {
                        androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                        androidx.compose.material3.OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host (IP or URL)") })
                        androidx.compose.material3.OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
                        androidx.compose.material3.OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("User") })
                        androidx.compose.material3.OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") })
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.addFtpServer(
                            FtpServer(
                                name = name.ifEmpty { host },
                                host = host,
                                port = port.toIntOrNull() ?: 21,
                                user = user,
                                pass = pass
                            )
                        )
                        showAddDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpBrowseScreen(viewModel: MainViewModel) {
    val currentPath by viewModel.ftpCurrentPath.collectAsState()
    val files by viewModel.ftpFiles.collectAsState()

    BackHandler {
        viewModel.ftpGoUp()
    }

    Scaffold(
        containerColor = PrimaryDarkRed,
        topBar = {
            TopAppBar(
                title = { Text(currentPath) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.ftpGoUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files) { file ->
                val isDir = file.isDirectory
                val icon = if (isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile
                ListItemRow(title = file.name, icon = icon, onClick = {
                    if (isDir) {
                        val newPath = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                        viewModel.browseFtpDir(newPath)
                    } else {
                        // TODO: Streaming FTP requires a custom ExoPlayer data source.
                        // For now we will encode standard URL for PlayerScreen
                        val server = viewModel.ftpManager.currentServer
                        if (server != null) {
                            val prefix = if (server.user.isNotEmpty() && server.pass.isNotEmpty()) {
                                "ftp://${server.user}:${server.pass}@${server.host}:${server.port}"
                            } else "ftp://${server.host}:${server.port}"
                            val fullPath = if (currentPath == "/") "/${file.name}" else "$currentPath/${file.name}"
                            viewModel.navController?.navigate(Screen.Player.createRoute(prefix + fullPath))
                        }
                    }
                }, onDeleteClick = {
                    viewModel.deleteFtpFile(file.name, isDir)
                })
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        containerColor = PrimaryDarkRed,
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Dźvina Player", style = MaterialTheme.typography.headlineLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Created by Źmicier Biesau", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text("A simple, beautiful video player for Android.", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        }
    }
}
