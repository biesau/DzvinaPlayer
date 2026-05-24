package com.maxvale.dzvinaplayer.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.maxvale.dzvinaplayer.data.AppDatabase
import com.maxvale.dzvinaplayer.data.RecentVideo
import kotlinx.coroutines.launch
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.Firebase

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    val player = ExoPlayer.Builder(application)
        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(application).setDataSourceFactory(com.maxvale.dzvinaplayer.network.CustomDataSourceFactory(application)))
        .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(application).setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
        .build()
    private val recentDao = AppDatabase.getDatabase(application).recentVideoDao()
    private val analytics = Firebase.analytics
    private val crashlytics = Firebase.crashlytics
    
    init {
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                crashlytics.recordException(error)
                analytics.logEvent("media_playback_error") {
                    param("error_code", error.errorCode.toLong())
                    param("message", error.message ?: "unknown")
                }
            }
        })
    }

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
        if (currentPath == path && player.mediaItemCount > 0) return
        currentPath = path
        externalAudioUri = null
        externalAudioFileName = null
        currentSubtitleConfigurations = null
        
        analytics.logEvent("media_playback_start") {
            param(FirebaseAnalytics.Param.ITEM_NAME, path.substringAfterLast("/"))
            param("extension", path.substringAfterLast(".", "unknown"))
        }
        
        player.playWhenReady = true
        reloadMedia()
    }

    fun reloadMedia() {
        val path = currentPath ?: return
        val wasPlaying = player.playWhenReady
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
            
            // Restore track selection from recent
            if (recent != null && externalAudioUri == null) {
                player.addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                        
                        var newParams = player.trackSelectionParameters.buildUpon()
                        
                        // Restore Audio
                        if (recent.audioTrackIndex != -1) {
                            var flatAudioIndex = 0
                            audio_outer@for (group in audioGroups) {
                                for (i in 0 until group.length) {
                                    if (flatAudioIndex == recent.audioTrackIndex) {
                                        newParams = newParams.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                        break@audio_outer
                                    }
                                    flatAudioIndex++
                                }
                            }
                        }
                        
                        // Restore Subtitles
                        if (recent.subtitleTrackIndex != -1) {
                            var flatTextIndex = 0
                            text_outer@for (group in textGroups) {
                                for (i in 0 until group.length) {
                                    if (flatTextIndex == recent.subtitleTrackIndex) {
                                        newParams = newParams.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        break@text_outer
                                    }
                                    flatTextIndex++
                                }
                            }
                        } else {
                            // If no subtitle was selected, disable them
                            newParams = newParams.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        }
                        
                        player.trackSelectionParameters = newParams.build()
                        player.removeListener(this)
                    }
                })
            }

            if (wasPlaying) {
                player.play()
            }
        }
    }

    fun saveRecent() {
        val path = currentPath ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        
        // Find selected track indices
        val currentTracks = player.currentTracks
        
        var selectedAudioIndex = -1
        val audioGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        var flatAudioIndex = 0
        audio_outer@for (group in audioGroups) {
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) {
                    selectedAudioIndex = flatAudioIndex
                    break@audio_outer
                }
                flatAudioIndex++
            }
        }
        
        var selectedSubtitleIndex = -1
        val textGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        var flatTextIndex = 0
        text_outer@for (group in textGroups) {
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) {
                    selectedSubtitleIndex = flatTextIndex
                    break@text_outer
                }
                flatTextIndex++
            }
        }

        if (dur > 0) {
            viewModelScope.launch {
                val name = path.substringAfterLast("/")
                recentDao.insertRecent(
                    RecentVideo(
                        path = path,
                        name = name,
                        lastPositionMs = pos,
                        durationMs = dur,
                        audioTrackIndex = selectedAudioIndex,
                        subtitleTrackIndex = selectedSubtitleIndex,
                        lastWatchedAt = System.currentTimeMillis()
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
