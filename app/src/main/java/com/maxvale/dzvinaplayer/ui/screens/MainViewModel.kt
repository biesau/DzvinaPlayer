package com.maxvale.dzvinaplayer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.maxvale.dzvinaplayer.data.AppDatabase
import com.maxvale.dzvinaplayer.data.FavoriteLocation
import com.maxvale.dzvinaplayer.data.RecentVideo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database =AppDatabase.getDatabase(application)
    private val favoriteDao = database.favoriteDao()
    private val recentVideoDao = database.recentVideoDao()

    var navController: NavController? = null

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
