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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database =AppDatabase.getDatabase(application)
    private val favoriteDao = database.favoriteDao()
    private val recentVideoDao = database.recentVideoDao()

    var navController: NavController? = null

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

    fun removeFavorite(favoriteLocation: FavoriteLocation) {
        viewModelScope.launch {
            favoriteDao.deleteFavorite(favoriteLocation)
        }
    }
    
    fun removeRecent(recentVideo: RecentVideo) {
        viewModelScope.launch {
            recentVideoDao.deleteRecent(recentVideo)
        }
    }
}
