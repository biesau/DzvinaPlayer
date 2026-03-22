package com.maxvale.dzvinaplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun PlayerScreen(
    videoPath: String,
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity
    
    var showControls by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showRemainingTime by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showInfoOverlay by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    // Start playing immediately
    LaunchedEffect(videoPath) {
        viewModel.playFile(videoPath)
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = viewModel.player.duration.coerceAtLeast(0L)
                }
            }
        }
        viewModel.player.addListener(listener)
        onDispose {
            viewModel.saveRecent()
            viewModel.player.removeListener(listener)
            viewModel.player.stop()
            // Reset brightness when leaving
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    // Progress updater
    LaunchedEffect(isPlaying, showControls) {
        while (true) {
            if (showControls) {
                currentPosition = viewModel.player.currentPosition.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val isMiddle = offset.x > size.width * 0.33f && offset.x < size.width * 0.66f
                        val isLeft = offset.x <= size.width * 0.33f
                        val isRight = offset.x >= size.width * 0.66f

                        if (isMiddle) {
                            if (isPlaying) viewModel.player.pause() else viewModel.player.play()
                        } else if (isLeft) {
                            val newPos = (viewModel.player.currentPosition - 10000).coerceAtLeast(0)
                            viewModel.player.seekTo(newPos)
                            currentPosition = newPos
                        } else if (isRight) {
                            val dur = viewModel.player.duration.coerceAtLeast(0)
                            val newPos = if (dur > 0) (viewModel.player.currentPosition + 10000).coerceAtMost(dur) else viewModel.player.currentPosition + 10000
                            viewModel.player.seekTo(newPos)
                            currentPosition = newPos
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { /* can show overlay indicating vol/brightness change starts */ },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val yDelta = dragAmount.y
                        val isLeftSide = change.position.x < size.width / 2

                        if (isLeftSide) {
                            // Brightness
                            activity?.let {
                                val lp = it.window.attributes
                                var newBrightness = lp.screenBrightness
                                if (newBrightness < 0) {
                                  newBrightness = 0.5f // Default assuming mid if not set
                                }
                                newBrightness -= (yDelta / size.height) * 2f // Sensitivity multiplier
                                lp.screenBrightness = newBrightness.coerceIn(0.01f, 1f)
                                it.window.attributes = lp
                            }
                        } else {
                            // Volume
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            
                            // Map drag to volume steps. Negative drag is up swipe (increase volume)
                            val volDelta = -(yDelta / (size.height / maxVol)).toInt()
                            if (volDelta != 0) {
                                val newVol = (currentVol + volDelta).coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            }
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    useController = false
                    this.resizeMode = resizeMode
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                // Top controls (back, filename)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = videoPath.substringAfterLast("/"),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                    IconButton(onClick = { showInfoOverlay = !showInfoOverlay; if(showInfoOverlay) showControls = false }) {
                        Icon(Icons.Filled.Info, contentDescription = "Info", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        ) {
                            DropdownMenuItem(text = { Text("Audio tracks") }, onClick = { showOptionsMenu = false; showAudioDialog = true; showControls = false })
                            DropdownMenuItem(text = { Text("Subtitle tracks") }, onClick = { showOptionsMenu = false; showSubtitleDialog = true; showControls = false })
                        }
                    }
                }

                // Center Play/Pause
                IconButton(
                    onClick = {
                        if (isPlaying) viewModel.player.pause() else viewModel.player.play()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                            onValueChange = { newVal ->
                                val target = (newVal * duration).toLong()
                                currentPosition = target
                                viewModel.player.seekTo(target)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = if (showRemainingTime) "-${formatTime(duration - currentPosition)}" else formatTime(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }) {
                            Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                        }
                    }
                }
            }
        }

        if (showInfoOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.4f)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp)
            ) {
                val videoFormat = viewModel.player.videoFormat
                val audioFormat = viewModel.player.audioFormat
                Column(modifier = Modifier.fillMaxSize()) {
                    Text("Video Info", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Path: ${videoPath.substringAfterLast("/")}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Resolution: ${videoFormat?.width ?: "?"} x ${videoFormat?.height ?: "?"}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text("Video Codec: ${videoFormat?.sampleMimeType ?: "?"}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text("Bitrate: ${videoFormat?.bitrate ?: "?"} bps", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Audio Codec: ${audioFormat?.sampleMimeType ?: "?"}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text("Audio Channels: ${audioFormat?.channelCount ?: "?"}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text("Audio Sample Rate: ${audioFormat?.sampleRate ?: "?"} Hz", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { showInfoOverlay = false },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showAudioDialog) {
        TrackSelectionDialog(
            viewModel = viewModel,
            trackType = C.TRACK_TYPE_AUDIO,
            title = "Audio Tracks",
            onDismiss = { showAudioDialog = false },
            onPickExternal = {
                // Not supported for audio right now in simple ExoPlayer setup
                showAudioDialog = false
            }
        )
    }

    if (showSubtitleDialog) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val newMediaItem = MediaItem.Builder()
                    .setUri(android.net.Uri.parse(videoPath))
                    .setSubtitleConfigurations(listOf(
                        MediaItem.SubtitleConfiguration.Builder(uri)
                            .setMimeType(if (uri.path?.endsWith("srt", true) == true) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                            .setLanguage("en")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    )).build()
                val pos = viewModel.player.currentPosition
                viewModel.player.setMediaItem(newMediaItem)
                viewModel.player.seekTo(pos)
                viewModel.player.prepare()
                viewModel.player.play()
            }
            showSubtitleDialog = false
        }
        TrackSelectionDialog(
            viewModel = viewModel,
            trackType = C.TRACK_TYPE_TEXT,
            title = "Subtitle Tracks",
            onDismiss = { showSubtitleDialog = false },
            onPickExternal = { launcher.launch("*/*") }
        )
    }

    BackHandler {
        onNavigateBack()
    }
}

@Composable
fun TrackSelectionDialog(
    viewModel: PlayerViewModel,
    trackType: @androidx.media3.common.C.TrackType Int,
    title: String,
    onDismiss: () -> Unit,
    onPickExternal: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                val trackGroups = viewModel.player.currentTracks.groups.filter { it.type == trackType }
                if (trackGroups.isEmpty()) {
                    Text("No tracks found.")
                } else {
                    trackGroups.forEach { group ->
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            val label = format.language ?: "Track ${i + 1}"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.player.trackSelectionParameters = viewModel.player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (trackType == C.TRACK_TYPE_TEXT) {
                    TextButton(onClick = {
                        viewModel.player.trackSelectionParameters = viewModel.player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        onDismiss()
                    }) {
                        Text("Disable")
                    }
                }
            }
        },
        confirmButton = {
            if (trackType == C.TRACK_TYPE_TEXT) {
                TextButton(onClick = onPickExternal) {
                    Text("Pick File/Offset")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
