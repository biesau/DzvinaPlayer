package com.maxvale.dzvinaplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
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

    // Volume / Brightness indicator state
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var volumePercent by remember { mutableFloatStateOf(0f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessPercent by remember { mutableFloatStateOf(0.5f) }
    var volumeAccumulator by remember { mutableFloatStateOf(0f) }

    // Start playing immediately
    LaunchedEffect(videoPath) {
        viewModel.playFile(videoPath)
    }

    // Pause when audio output switches to phone speakers (e.g. headphones unplugged)
    DisposableEffect(Unit) {
        val noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    viewModel.player.pause()
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(noisyReceiver, filter)
        }
        onDispose {
            context.unregisterReceiver(noisyReceiver)
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        
        insetsController?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())

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
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            viewModel.saveRecent()
            viewModel.player.removeListener(listener)
            viewModel.player.stop()
            // Reset brightness when leaving
            activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }

    // Progress updater
    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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
                    onDragStart = { startOffset ->
                        val isLeftSide = startOffset.x < size.width / 2
                        if (isLeftSide) {
                            // Initialize brightness indicator
                            val currentBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
                            brightnessPercent = if (currentBrightness < 0) 0.5f else currentBrightness
                            showBrightnessIndicator = true
                        } else {
                            // Initialize volume indicator
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            volumePercent = if (maxVol > 0) currentVol.toFloat() / maxVol else 0f
                            volumeAccumulator = 0f
                            showVolumeIndicator = true
                        }
                    },
                    onDragEnd = {
                        showBrightnessIndicator = false
                        showVolumeIndicator = false
                    },
                    onDragCancel = {
                        showBrightnessIndicator = false
                        showVolumeIndicator = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val yDelta = dragAmount.y
                        val isLeftSide = change.position.x < size.width / 2

                        if (isLeftSide) {
                            // Brightness — reduced sensitivity (0.7x instead of 2x)
                            activity?.let {
                                val lp = it.window.attributes
                                var newBrightness = lp.screenBrightness
                                if (newBrightness < 0) {
                                    newBrightness = 0.5f
                                }
                                newBrightness -= (yDelta / size.height) * 0.7f
                                newBrightness = newBrightness.coerceIn(0.01f, 1f)
                                lp.screenBrightness = newBrightness
                                it.window.attributes = lp
                                brightnessPercent = newBrightness
                                showBrightnessIndicator = true
                            }
                        } else {
                            // Volume — improved sensitivity with accumulator
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            
                            volumeAccumulator -= yDelta
                            val pxPerStep = size.height / (maxVol.coerceAtLeast(1) * 2f) // Full height = half volume range for comfortable control
                            
                            if (kotlin.math.abs(volumeAccumulator) >= pxPerStep) {
                                val steps = (volumeAccumulator / pxPerStep).toInt()
                                val newVol = (currentVol + steps).coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                volumePercent = if (maxVol > 0) newVol.toFloat() / maxVol else 0f
                                volumeAccumulator -= steps * pxPerStep
                            }
                            showVolumeIndicator = true
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

        // Brightness indicator overlay (left side)
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 40.dp)
        ) {
            AdjustmentIndicator(
                icon = when {
                    brightnessPercent > 0.66f -> Icons.Filled.BrightnessHigh
                    brightnessPercent > 0.33f -> Icons.Filled.BrightnessMedium
                    else -> Icons.Filled.BrightnessLow
                },
                value = brightnessPercent,
                label = "${(brightnessPercent * 100).toInt()}%"
            )
        }

        // Volume indicator overlay (right side)
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp)
        ) {
            AdjustmentIndicator(
                icon = when {
                    volumePercent <= 0f -> Icons.Filled.VolumeOff
                    volumePercent < 0.33f -> Icons.Filled.VolumeMute
                    volumePercent < 0.66f -> Icons.Filled.VolumeDown
                    else -> Icons.Filled.VolumeUp
                },
                value = volumePercent,
                label = "${(volumePercent * 100).toInt()}%"
            )
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
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                var fileName = "External Audio"
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index != -1) {
                                fileName = cursor.getString(index)
                            }
                        }
                    }
                } else {
                    fileName = uri.path?.substringAfterLast('/') ?: fileName
                }
                viewModel.externalAudioFileName = fileName
                viewModel.externalAudioUri = uri
                viewModel.reloadMedia()
            }
            showAudioDialog = false
        }
        TrackSelectionDialog(
            viewModel = viewModel,
            trackType = C.TRACK_TYPE_AUDIO,
            title = "Audio Tracks",
            onDismiss = { showAudioDialog = false },
            onPickExternal = { launcher.launch("audio/*") }
        )
    }

    if (showSubtitleDialog) {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                var fileName = "External Subtitle"
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index != -1) {
                                fileName = cursor.getString(index)
                            }
                        }
                    }
                } else {
                    fileName = uri.path?.substringAfterLast('/') ?: fileName
                }

                val ext = fileName.substringAfterLast('.', "").lowercase()
                val mimeType = when(ext) {
                    "srt" -> MimeTypes.APPLICATION_SUBRIP
                    "ssa", "ass" -> MimeTypes.TEXT_SSA
                    "vtt" -> MimeTypes.TEXT_VTT
                    "ttml" -> MimeTypes.APPLICATION_TTML
                    else -> MimeTypes.APPLICATION_SUBRIP
                }

                val config = MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(mimeType)
                    .setLanguage("en")
                    .setLabel(fileName)
                    .setId("ext_sub")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                viewModel.currentSubtitleConfigurations = listOf(config)
                viewModel.reloadMedia()
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
            Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                val trackGroups = viewModel.player.currentTracks.groups.filter { it.type == trackType }
                if (trackGroups.isEmpty()) {
                    Text("No tracks found.")
                } else {
                    trackGroups.forEach { group ->
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val isSelected = group.isTrackSelected(i)
                            val langCode = format.language
                            val labelName = format.label
                            var label = if (langCode != null) {
                                java.util.Locale(langCode).displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                            } else {
                                "Track ${i + 1}"
                            }
                            if (trackType == C.TRACK_TYPE_AUDIO && viewModel.externalAudioFileName != null && group == trackGroups.last()) {
                                label = viewModel.externalAudioFileName!!
                            }
                            if (!labelName.isNullOrEmpty()) {
                                label += " ($labelName)"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.player.trackSelectionParameters = viewModel.player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i))
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
            if (trackType == C.TRACK_TYPE_TEXT || trackType == C.TRACK_TYPE_AUDIO) {
                TextButton(onClick = onPickExternal) {
                    Text("Pick File")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun AdjustmentIndicator(
    icon: ImageVector,
    value: Float,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .width(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Vertical bar indicator
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp
        )
    }
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
