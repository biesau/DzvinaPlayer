package com.maxvale.dzvinaplayer.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object AllFiles : Screen("all_files", "All files", Icons.Filled.Folder)
    object Favorites : Screen("favorites", "Favorites", Icons.Filled.Favorite)
    object Recent : Screen("recent", "Recent watched", Icons.Filled.History)
    object Player : Screen("player/{videoPath}", "Player", Icons.Filled.Folder) {
        fun createRoute(videoPath: String) = "player/${Uri.encode(videoPath)}"
    }
}

val bottomNavigationItems = listOf(
    Screen.AllFiles,
    Screen.Favorites,
    Screen.Recent
)
