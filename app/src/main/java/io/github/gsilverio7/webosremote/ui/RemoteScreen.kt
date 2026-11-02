package io.github.gsilverio7.webosremote.ui

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
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gsilverio7.webosremote.data.ShortcutActionType

@Composable
fun RemoteScreen(viewModel: RemoteViewModel) {
    val state by viewModel.tvState.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val hardwareVolumeEnabled by viewModel.hardwareVolumeKeysEnabled.collectAsState()
    val irBlinking by viewModel.irLedBlinking.collectAsState()
    val shortcuts by viewModel.shortcuts.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var editingShortcutSlot by remember { mutableStateOf<Int?>(null) }
    var showKeyboardDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalHapticEnabled provides hapticEnabled) {
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
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ==================== TOPO: LED IR + BOTÃO POWER VERMELHO + CONFIG ====================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val ledColor = when {
                        irBlinking -> Color(0xFFFF2E2E)
                        state.prompting -> Color(0xFFF59E0B)
                        state.connected && state.paired -> Color(0xFF22C55E)
                        else -> Color(0xFF52525B)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .shadow(
                                    if (irBlinking || state.connected) 6.dp else 0.dp,
                                    CircleShape,
                                    spotColor = ledColor
                                )
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PhysicalRedPowerButton(
                                isTvOnline = state.connected && state.paired,
                                onClick = { viewModel.onPowerToggle() },
                                size = 56.dp
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

                        // Visor OLED embutido no controle (subido ligeiramente para liberar espaço)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                                .background(Color(0xFF050608), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF1F2128), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
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

                        // Tecla Física de Configurações (IP / MAC / Vibração / Atalhos)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PhysicalKeyButton(
                                onClick = { showSettings = true },
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp),
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
                                text = "CONFIG",
                                color = Color(0xFF71717A),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

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

                // Espaço uniforme acima do D-Pad
                Spacer(modifier = Modifier.height(16.dp))

                // ==================== D-PAD CIRCULAR FÍSICO + OK ====================
                PhysicalCircularDPad(
                    onUp = { viewModel.onNavigationButton("UP", "▲ Cima") },
                    onDown = { viewModel.onNavigationButton("DOWN", "▼ Baixo") },
                    onLeft = { viewModel.onNavigationButton("LEFT", "◀ Esquerda") },
                    onRight = { viewModel.onNavigationButton("RIGHT", "▶ Direita") },
                    onOk = { viewModel.onNavigationButton("ENTER", "● OK") },
                    wheelSize = 186.dp
                )

                // Espaço uniforme abaixo do D-Pad (idêntico ao espaço acima: 16.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // ==================== GRADE 3x3 DE COMANDOS (MESMA LARGURA) ====================
                // Linha 1: VOLTAR, VOZ/IA, HOME
                // Linha 2: CH+, TECLADO, VOL+
                // Linha 3: CH-, MUDO, VOL-
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // LINHA 1: VOLTAR | VOZ / IA | HOME
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // VOLTAR
                        PhysicalKeyButton(
                            onClick = { viewModel.onNavigationButton("BACK", "↩ Voltar") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Voltar",
                                    tint = Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "VOLTAR",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // VOZ / IA
                        PhysicalKeyButton(
                            onClick = { showVoiceDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Controle por Voz / IA",
                                    tint = Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "VOZ / IA",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // HOME
                        PhysicalKeyButton(
                            onClick = { viewModel.onNavigationButton("HOME", "⌂ Home") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home",
                                    tint = Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "HOME",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // LINHA 2: CH + | TECLADO | VOL +
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // CH +
                        PhysicalKeyButton(
                            onClick = { viewModel.onChannelUp() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Canal Mais",
                                    tint = Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "CH +",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // TECLADO
                        PhysicalKeyButton(
                            onClick = { showKeyboardDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = "Teclado para TV",
                                    tint = Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "TECLADO",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // VOL +
                        PhysicalKeyButton(
                            onClick = { viewModel.onVolumeUp() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Volume Mais",
                                    tint = Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "VOL +",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // LINHA 3: CH − | MUDO | VOL −
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // CH −
                        PhysicalKeyButton(
                            onClick = { viewModel.onChannelDown() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Canal Menos",
                                    tint = Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "CH −",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // MUDO
                        PhysicalKeyButton(
                            onClick = { viewModel.onMuteToggle() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            surfaceTopColor = if (state.muted) Color(0xFF451A1A) else Color(0xFF32343B),
                            surfaceBottomColor = if (state.muted) Color(0xFF2A1010) else Color(0xFF1E2025),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (state.muted) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = "Mudo",
                                    tint = if (state.muted) Color(0xFFFCA5A5) else Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "MUDO",
                                    color = if (state.muted) Color(0xFFFCA5A5) else Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // VOL −
                        PhysicalKeyButton(
                            onClick = { viewModel.onVolumeDown() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Volume Menos",
                                    tint = Color(0xFFE4E4E7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "VOL −",
                                    color = Color(0xFFE4E4E7),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==================== GRADE 3x3 DE TECLAS FÍSICAS DE APPS / CANAIS CONFIGURÁVEIS ====================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shortcuts.chunked(3).forEach { rowShortcuts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowShortcuts.forEach { shortcut ->
                                PhysicalKeyButton(
                                    onClick = { viewModel.onLaunchShortcut(shortcut) },
                                    onLongClick = { editingShortcutSlot = shortcut.slotIndex },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(13.dp),
                                    surfaceTopColor = Color(0xFF2B2D34),
                                    surfaceBottomColor = Color(0xFF191A1F),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        ShortcutIconView(
                                            customIconPath = shortcut.customIconPath,
                                            fallbackRes = shortcut.iconRes,
                                            contentDescription = shortcut.label,
                                            isYouTubeLive = shortcut.actionType == ShortcutActionType.YOUTUBE_LIVE,
                                            modifier = Modifier.size(18.dp)
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

                Spacer(modifier = Modifier.height(14.dp))

                // Rodapé do controle (posicionado mais abaixo)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "LG webOS SMART REMOTE",
                        color = Color(0xFF52525B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.4.sp
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            initialIp = state.ipAddress,
            initialMac = state.macAddress,
            initialBroadcast = state.broadcastAddress,
            initialHapticEnabled = hapticEnabled,
            initialHardwareVolumeEnabled = hardwareVolumeEnabled,
            onDismiss = { showSettings = false },
            onDiscover = { onFound -> viewModel.onDiscoverTv(onFound) },
            onOpenShortcutsEditor = { editingShortcutSlot = 0 },
            onSave = { ip, mac, broadcast, haptic, hwVolume ->
                viewModel.onSaveSettings(ip, mac, broadcast, haptic, hwVolume)
            }
        )
    }

    editingShortcutSlot?.let { slotIdx ->
        ShortcutEditDialog(
            initialSlotIndex = slotIdx,
            allShortcuts = shortcuts,
            installedApps = installedApps,
            isLoadingApps = isLoadingApps,
            onRefreshInstalledApps = { viewModel.onRefreshInstalledApps() },
            onSaveShortcut = { idx, label, type, target, iconPath ->
                viewModel.onSaveShortcut(idx, label, type, target, iconPath)
            },
            onResetDefaults = { viewModel.onResetShortcutsToDefault() },
            onDismiss = { editingShortcutSlot = null }
        )
    }

    if (showKeyboardDialog) {
        TvKeyboardDialog(
            onSendText = { text, replace, pressEnter ->
                viewModel.onSendTextToTv(text, replace, pressEnter)
            },
            onBackspace = { viewModel.onDeleteTvChar() },
            onEnterKey = { viewModel.onSendTvEnter() },
            onSearchYouTube = { query ->
                viewModel.onSearchYouTube(query)
            },
            onDismiss = { showKeyboardDialog = false }
        )
    }

    if (showVoiceDialog) {
        VoiceControlDialog(
            onExecuteVoiceCommand = { spoken ->
                viewModel.onVoiceCommand(spoken)
            },
            onDictateTextToTv = { spoken ->
                viewModel.onDictateTextToTv(spoken)
            },
            onDismiss = { showVoiceDialog = false }
        )
    }
}
