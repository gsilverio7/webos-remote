package com.lgtv.remote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    val state by viewModel.tvState.collectAsState()
    val irBlinking by viewModel.irLedBlinking.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    // Fundo externo preto profundo
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050507))
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Corpo Físico do Controle Remoto (Chassi Preto Fosco com Borda Chanfrada)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .shadow(20.dp, RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF14151A),
                            Color(0xFF0B0C0F),
                            Color(0xFF0B0C0F),
                            Color(0xFF14151A)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF333640),
                            Color(0xFF181920),
                            Color(0xFF0E0F13)
                        )
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==================== TOPO: LED IR + BOTÃO POWER VERMELHO + CONFIG ====================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pequeno emissor IR / LED físico no topo do chassi
                val ledColor = when {
                    irBlinking -> Color(0xFFFF2E2E)
                    state.prompting -> Color(0xFFF59E0B)
                    state.connected && state.paired -> Color(0xFF22C55E)
                    else -> Color(0xFF52525B)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .shadow(if (irBlinking || state.connected) 6.dp else 0.dp, CircleShape, spotColor = ledColor)
                            .background(ledColor, CircleShape)
                            .border(1.dp, Color(0xFF27272A), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.connected && state.paired) "TV CONECTADA" else "STANDBY",
                        color = Color(0xFF71717A),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                }

                // Linha Principal Superior: Botão Power Vermelho Unificado + Visor OLED + Botão Config
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Botão Único Vermelho Físico (Liga via WoL / Desliga via SSAP)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhysicalRedPowerButton(
                            isTvOnline = state.connected && state.paired,
                            onClick = { viewModel.onPowerToggle() },
                            size = 58.dp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "POWER",
                            color = Color(0xFFA1A1AA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    // Visor OLED embutido no controle (mostra status, volume e último comando)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .background(Color(0xFF050608), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1F2128), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.feedbackMessage,
                            color = Color(0xFFE4E4E7),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val volLabel = if (state.muted) "MUDO" else "VOL ${state.volume}"
                        Text(
                            text = "${state.ipAddress} • $volLabel",
                            color = Color(0xFF71717A),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }

                    // Tecla Física de Configurações (IP / MAC / SSDP)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhysicalKeyButton(
                            onClick = { showSettings = true },
                            shape = CircleShape,
                            modifier = Modifier.size(46.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurações",
                                tint = Color(0xFFD4D4D8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "CFG",
                            color = Color(0xFF71717A),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Aviso se a TV estiver pedindo confirmação de pareamento na tela
                if (state.prompting) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF451A03), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFD97706), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Olhe para a TV e selecione \"Permitir\" para parear!",
                            color = Color(0xFFFEF3C7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==================== D-PAD CIRCULAR FÍSICO + OK ====================
            PhysicalCircularDPad(
                onUp = { viewModel.onNavigationButton("UP", "▲ Cima") },
                onDown = { viewModel.onNavigationButton("DOWN", "▼ Baixo") },
                onLeft = { viewModel.onNavigationButton("LEFT", "◀ Esquerda") },
                onRight = { viewModel.onNavigationButton("RIGHT", "▶ Direita") },
                onOk = { viewModel.onNavigationButton("ENTER", "● OK") },
                wheelSize = 188.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ==================== LINHA DE NAVEGAÇÃO (VOLTAR / HOME) ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhysicalKeyButton(
                    onClick = { viewModel.onNavigationButton("BACK", "↩ Voltar") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Voltar",
                            tint = Color(0xFFE4E4E7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VOLTAR",
                            color = Color(0xFFE4E4E7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                PhysicalKeyButton(
                    onClick = { viewModel.onNavigationButton("HOME", "⌂ Home") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = Color(0xFFE4E4E7),
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HOME",
                            color = Color(0xFFE4E4E7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==================== LINHA DE ÁUDIO FÍSICA (VOL - / MUDO / VOL +) ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // VOL -
                PhysicalKeyButton(
                    onClick = { viewModel.onVolumeDown() },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Volume Menos",
                            tint = Color(0xFFE4E4E7),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "VOL",
                            color = Color(0xFFD4D4D8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // MUDO
                PhysicalKeyButton(
                    onClick = { viewModel.onMuteToggle() },
                    modifier = Modifier
                        .weight(0.9f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    surfaceTopColor = if (state.muted) Color(0xFF451A1A) else Color(0xFF2B2D33),
                    surfaceBottomColor = if (state.muted) Color(0xFF2A1010) else Color(0xFF1A1B20)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (state.muted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = "Mudo",
                            tint = if (state.muted) Color(0xFFFCA5A5) else Color(0xFFD4D4D8),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "MUDO",
                            color = if (state.muted) Color(0xFFFCA5A5) else Color(0xFFA1A1AA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // VOL +
                PhysicalKeyButton(
                    onClick = { viewModel.onVolumeUp() },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "VOL",
                            color = Color(0xFFD4D4D8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Volume Mais",
                            tint = Color(0xFFE4E4E7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==================== GRADE 3x3 DE TECLAS FÍSICAS DE APPS / CANAIS ====================
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                viewModel.appShortcuts.forEach { rowShortcuts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        rowShortcuts.forEach { shortcut ->
                            PhysicalKeyButton(
                                onClick = { viewModel.onLaunchShortcut(shortcut) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(13.dp),
                                surfaceTopColor = Color(0xFF2B2D34),
                                surfaceBottomColor = Color(0xFF191A1F),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = shortcut.iconRes),
                                        contentDescription = shortcut.label,
                                        modifier = Modifier.size(18.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = shortcut.label,
                                        color = Color(0xFFE4E4E7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gravação metálica na base do controle físico
            Text(
                text = "LG webOS SMART REMOTE",
                color = Color(0xFF52525B),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.4.sp
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            initialIp = state.ipAddress,
            initialMac = state.macAddress,
            initialBroadcast = state.broadcastAddress,
            onDismiss = { showSettings = false },
            onDiscover = { onFound -> viewModel.onDiscoverTv(onFound) },
            onSave = { ip, mac, broadcast ->
                viewModel.onSaveSettings(ip, mac, broadcast)
            }
        )
    }
}
