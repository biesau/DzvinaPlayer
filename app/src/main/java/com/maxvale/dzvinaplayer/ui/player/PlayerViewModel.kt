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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val player = ExoPlayer.Builder(application)
        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(application).setDataSourceFactory(com.maxvale.dzvinaplayer.network.CustomDataSourceFactory(application)))
        .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(application).setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
        .build()
    private val recentDao = AppDatabase.getDatabase(application).recentVideoDao()
    var currentPath: String? = null

    var audioOffsetMs: Long = 0
        private set
    var subtitleOffsetMs: Long = 0
        private set

    fun setAudioOffset(offset: Long) {
        audioOffsetMs = offset
        // In a real scenario, this requires a customized DefaultAudioSink config.
    }

    fun setSubtitleOffset(offset: Long) {
        subtitleOffsetMs = offset
        // This requires a customized TextRenderer config.
    }

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
