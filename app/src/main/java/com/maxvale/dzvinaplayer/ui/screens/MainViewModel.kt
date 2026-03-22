package com.maxvale.dzvinaplayer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.maxvale.dzvinaplayer.data.AppDatabase
import com.maxvale.dzvinaplayer.data.FavoriteLocation
import com.maxvale.dzvinaplayer.data.RecentVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import android.os.Environment

enum class BrowseScope {
    HOME, LOCAL, FTP_ROOT, FTP_BROWSE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database =AppDatabase.getDatabase(application)
    private val favoriteDao = database.favoriteDao()
    private val recentVideoDao = database.recentVideoDao()
    private val ftpServerDao = database.ftpServerDao()

    var navController: NavController? = null

    private val _browseScope = MutableStateFlow(BrowseScope.HOME)
    val browseScope = _browseScope.asStateFlow()

    fun setBrowseScope(scope: BrowseScope) {
        _browseScope.value = scope
    }

    private val _currentDir = MutableStateFlow(Environment.getExternalStorageDirectory())
    val currentDir = _currentDir.asStateFlow()

    fun setCurrentDir(file: File) {
        _currentDir.value = file
    }

    val favorites = favoriteDao.getAllFavorites().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recents = recentVideoDao.getAllRecents().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addFavorite(path: String, name: String) {
        viewModelScope.launch {
            favoriteDao.insertFavorite(FavoriteLocation(path, name))
        }
    }

    val ftpServers = ftpServerDao.getAllServers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addFtpServer(server: com.maxvale.dzvinaplayer.data.FtpServer) {
        viewModelScope.launch {
            ftpServerDao.insertServer(server)
        }
    }

    fun removeFtpServer(server: com.maxvale.dzvinaplayer.data.FtpServer) {
        viewModelScope.launch {
            ftpServerDao.deleteServer(server)
        }
    }

    fun removeFavorite(favoriteLocation: FavoriteLocation) {
        viewModelScope.launch {
            favoriteDao.deleteFavorite(favoriteLocation)
        }
    }

    val ftpManager = com.maxvale.dzvinaplayer.network.FtpClientManager()

    private val _ftpFiles = MutableStateFlow<List<org.apache.commons.net.ftp.FTPFile>>(emptyList())
    val ftpFiles = _ftpFiles.asStateFlow()

    private val _ftpCurrentPath = MutableStateFlow("/")
    val ftpCurrentPath = _ftpCurrentPath.asStateFlow()

    fun connectToFtp(server: com.maxvale.dzvinaplayer.data.FtpServer) {
        viewModelScope.launch {
            val success = ftpManager.connect(server)
            if (success) {
                _ftpCurrentPath.value = ftpManager.getCurrentDir()
                _ftpFiles.value = ftpManager.listFiles()
                setBrowseScope(BrowseScope.FTP_BROWSE)
            }
        }
    }

    fun browseFtpDir(path: String) {
        viewModelScope.launch {
            _ftpFiles.value = ftpManager.listFiles(path)
            _ftpCurrentPath.value = ftpManager.getCurrentDir()
        }
    }

    fun deleteFtpFile(name: String, isDir: Boolean) {
        viewModelScope.launch {
            ftpManager.deleteFile(name, isDir)
            _ftpFiles.value = ftpManager.listFiles("") // refresh current
        }
    }

    fun ftpGoUp() {
        viewModelScope.launch {
            if (_ftpCurrentPath.value == "/") {
                ftpManager.disconnect()
                setBrowseScope(BrowseScope.FTP_ROOT)
                return@launch
            }
            val success = ftpManager.changeDirUp()
            if (success) {
                _ftpFiles.value = ftpManager.listFiles("")
                _ftpCurrentPath.value = ftpManager.getCurrentDir()
            } else {
                ftpManager.disconnect()
                setBrowseScope(BrowseScope.FTP_ROOT)
            }
        }
    }

    fun removeRecent(recentVideo: RecentVideo) {
        viewModelScope.launch {
            recentVideoDao.deleteRecent(recentVideo)
        }
    }
}
