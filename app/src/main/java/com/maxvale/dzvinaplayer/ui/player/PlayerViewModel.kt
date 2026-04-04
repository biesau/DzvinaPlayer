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

    var externalAudioUri: Uri? = null
    var externalAudioFileName: String? = null
    var currentSubtitleConfigurations: List<MediaItem.SubtitleConfiguration>? = null

    fun playFile(path: String) {
        currentPath = path
        externalAudioUri = null
        externalAudioFileName = null
        currentSubtitleConfigurations = null
        reloadMedia()
    }

    fun reloadMedia() {
        val path = currentPath ?: return
        viewModelScope.launch {
            val recent = recentDao.getRecent(path)
            val pos = if (player.currentPosition > 0) player.currentPosition else (recent?.lastPositionMs ?: 0L)

            val videoItemBuilder = MediaItem.Builder().setUri(Uri.parse(path))
            if (currentSubtitleConfigurations != null) {
                videoItemBuilder.setSubtitleConfigurations(currentSubtitleConfigurations!!)
            }
            val videoItem = videoItemBuilder.build()

            if (externalAudioUri == null) {
                player.setMediaItem(videoItem)
            } else {
                val factory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(getApplication<Application>())
                    .setDataSourceFactory(com.maxvale.dzvinaplayer.network.CustomDataSourceFactory(getApplication<Application>()))
                val videoSource = factory.createMediaSource(videoItem)
                val audioSource = factory.createMediaSource(MediaItem.fromUri(externalAudioUri!!))
                val mergedSource = androidx.media3.exoplayer.source.MergingMediaSource(true, videoSource, audioSource)
                player.setMediaSource(mergedSource)
                
                player.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        val audioGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
                        if (audioGroups.size > 1) {
                            val lastGroup = audioGroups.last()
                            if (!lastGroup.isSelected) {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(lastGroup.mediaTrackGroup, 0))
                                    .build()
                            }
                        }
                        player.removeListener(this)
                    }
                })
            }

            player.seekTo(pos)
            player.prepare()
            player.play()
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
