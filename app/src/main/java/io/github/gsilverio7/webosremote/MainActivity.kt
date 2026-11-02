package io.github.gsilverio7.webosremote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.gsilverio7.webosremote.ui.RemoteScreen
import io.github.gsilverio7.webosremote.ui.RemoteViewModel

private val RemoteDarkColors = darkColorScheme(
    primary = Color(0xFFEF4444),
    background = Color(0xFF050507),
    surface = Color(0xFF121318),
    onPrimary = Color.White,
    onBackground = Color(0xFFF4F4F5),
    onSurface = Color(0xFFF4F4F5)
)

class MainActivity : ComponentActivity() {
    private val remoteViewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pitchBlack = Color(0xFF050507).toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(pitchBlack),
            navigationBarStyle = SystemBarStyle.dark(pitchBlack)
        )
        setContent {
            MaterialTheme(colorScheme = RemoteDarkColors) {
                RemoteScreen(viewModel = remoteViewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (remoteViewModel.hardwareVolumeKeysEnabled.value) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    remoteViewModel.onVolumeUp()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    remoteViewModel.onVolumeDown()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (remoteViewModel.hardwareVolumeKeysEnabled.value) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}
