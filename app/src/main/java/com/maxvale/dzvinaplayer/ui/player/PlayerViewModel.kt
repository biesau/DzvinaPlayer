package com.maxvale.dzvinaplayer.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.maxvale.dzvinaplayer.data.AppDatabase
import com.maxvale.dzvinaplayer.data.RecentVideo
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val player = ExoPlayer.Builder(application).build()
    private val recentDao = AppDatabase.getDatabase(application).recentVideoDao()
    var currentPath: String? = null

    fun playFile(path: String) {
        currentPath = path
        viewModelScope.launch {
            val recent = recentDao.getRecent(path)
            val mediaItem = MediaItem.fromUri(Uri.parse(path))
            player.setMediaItem(mediaItem)
            if (recent != null && recent.lastPositionMs > 0) {
                player.seekTo(recent.lastPositionMs)
            }
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun saveRecent() {
        val path = currentPath ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        if (dur > 0) {
            viewModelScope.launch {
                val name = path.substringAfterLast("/")
                recentDao.insertRecent(
                    RecentVideo(
                        path = path,
                        name = name,
                        lastPositionMs = pos,
                        durationMs = dur
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
