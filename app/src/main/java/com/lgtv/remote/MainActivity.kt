package com.lgtv.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lgtv.remote.ui.RemoteScreen
import com.lgtv.remote.ui.RemoteViewModel

private val RemoteDarkColors = darkColorScheme(
    primary = Color(0xFFEF4444),
    background = Color(0xFF050507),
    surface = Color(0xFF121318),
    onPrimary = Color.White,
    onBackground = Color(0xFFF4F4F5),
    onSurface = Color(0xFFF4F4F5)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pitchBlack = Color(0xFF050507).toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(pitchBlack),
            navigationBarStyle = SystemBarStyle.dark(pitchBlack)
        )
        setContent {
            MaterialTheme(colorScheme = RemoteDarkColors) {
                val remoteViewModel: RemoteViewModel = viewModel()
                RemoteScreen(viewModel = remoteViewModel)
            }
        }
    }
}
