package com.maxvale.dzvinaplayer

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.maxvale.dzvinaplayer.ui.screens.MainScreen
import com.maxvale.dzvinaplayer.ui.screens.MainViewModel
import com.maxvale.dzvinaplayer.ui.theme.DzvinaplayerTheme


class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DzvinaplayerTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_AUDIO,
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_AUDIO,
                        android.Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    // Logic to refresh content if partial access is granted
                    val isPartial = permissions[android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
                    val isFullVideo = permissions[android.Manifest.permission.READ_MEDIA_VIDEO] == true
                    if (isPartial || isFullVideo) {
                        // In a real app, you might trigger a ViewModel refresh here
                    }
                }

                LaunchedEffect(Unit) {
                    launcher.launch(permissionsToRequest)
                }

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = mainViewModel)
                }
            }
        }
    }
}
