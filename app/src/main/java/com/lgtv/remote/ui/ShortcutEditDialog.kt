package com.lgtv.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lgtv.remote.data.ShortcutActionType
import com.lgtv.remote.data.TvInstalledApp

@Composable
fun ShortcutEditDialog(
    initialSlotIndex: Int,
    allShortcuts: List<TvAppShortcut>,
    installedApps: List<TvInstalledApp>,
    isLoadingApps: Boolean,
    onRefreshInstalledApps: () -> Unit,
    onSaveShortcut: (
        slotIndex: Int,
        label: String,
        actionType: ShortcutActionType,
        target: String,
        customIconPath: String?
    ) -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSlot by remember { mutableIntStateOf(initialSlotIndex.coerceIn(0, 8)) }
    val currentShortcut = allShortcuts.getOrNull(selectedSlot) ?: allShortcuts.first()

    var actionType by remember(selectedSlot, currentShortcut) {
        mutableStateOf(currentShortcut.actionType)
    }
    var label by remember(selectedSlot, currentShortcut) {
        mutableStateOf(currentShortcut.label)
    }
    var target by remember(selectedSlot, currentShortcut) {
        val rawTarget = if (currentShortcut.actionType == ShortcutActionType.YOUTUBE_LIVE) {
            currentShortcut.target
                .removePrefix("https://www.youtube.com/")
                .removePrefix("http://www.youtube.com/")
                .removeSuffix("/live")
                .removePrefix("@")
        } else {
            currentShortcut.target
        }
        mutableStateOf(rawTarget)
    }
    var customIconPath by remember(selectedSlot, currentShortcut) {
        mutableStateOf(currentShortcut.customIconPath)
    }
    var appSearchQuery by remember { mutableStateOf("") }

    // Ao abrir a janela na aba de Apps da TV, se a lista estiver vazia, solicita sincronização automática
    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            onRefreshInstalledApps()
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFE4E4E7),
        focusedBorderColor = Color(0xFFEF4444),
        unfocusedBorderColor = Color(0xFF3F3F46),
        focusedLabelColor = Color(0xFFEF4444),
        unfocusedLabelColor = Color(0xFFA1A1AA),
        cursorColor = Color(0xFFEF4444)
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141519), RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFF2E3038), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Configurar Botão de Atalho",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Escolha a posição (1 a 9) e o App da TV ou Live",
                        color = Color(0xFFA1A1AA),
                        fontSize = 11.sp
                    )
                }
                TextButton(
                    onClick = onResetDefaults,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restaurar padrão",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Padrão", color = Color(0xFFA1A1AA), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Seletor rápido dos 9 botões (grade 3x3 compacta)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (col in 0 until 3) {
                            val idx = row * 3 + col
                            val item = allShortcuts.getOrNull(idx)
                            val isSelected = idx == selectedSlot
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFF3F1518) else Color(0xFF1F2026)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFFEF4444) else Color(0xFF2E3038),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedSlot = idx }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (item != null) {
                                        ShortcutIconView(
                                            customIconPath = item.customIconPath,
                                            fallbackRes = item.iconRes,
                                            contentDescription = item.label,
                                            isYouTubeLive = item.actionType == ShortcutActionType.YOUTUBE_LIVE,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = "${idx + 1}. ${item?.label.orEmpty()}",
                                        color = if (isSelected) Color.White else Color(0xFFD4D4D8),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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

            // Abas: App da TV vs Live do YouTube
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0D10), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isAppTab = actionType == ShortcutActionType.APP
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isAppTab) Color(0xFFDC2626) else Color.Transparent)
                        .clickable {
                            actionType = ShortcutActionType.APP
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "App da TV",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (!isAppTab) Color(0xFFDC2626) else Color.Transparent)
                        .clickable {
                            actionType = ShortcutActionType.YOUTUBE_LIVE
                            if (target.contains(".")) {
                                target = ""
                                customIconPath = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live YouTube",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (actionType == ShortcutActionType.APP) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = { Text("Filtrar apps da TV...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = onRefreshInstalledApps,
                        enabled = !isLoadingApps,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                    ) {
                        if (isLoadingApps) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFEF4444)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Buscar da TV",
                                tint = Color(0xFFE4E4E7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val filteredApps = remember(installedApps, appSearchQuery) {
                    val q = appSearchQuery.trim().lowercase()
                    if (q.isEmpty()) installedApps
                    else installedApps.filter {
                        it.title.lowercase().contains(q) || it.appId.lowercase().contains(q)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp, max = 185.dp)
                        .background(Color(0xFF0C0D10), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF23252C), RoundedCornerShape(12.dp))
                        .padding(6.dp)
                ) {
                    if (isLoadingApps && installedApps.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Buscando apps e ícones instalados na TV...",
                                color = Color(0xFFA1A1AA),
                                fontSize = 11.sp
                            )
                        }
                    } else if (filteredApps.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Ligue a TV e toque no botão Atualizar ao lado da busca para carregar todos os apps e ícones direto da TV.",
                                color = Color(0xFFA1A1AA),
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(filteredApps, key = { it.appId }) { app ->
                                val isChosen = target == app.appId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isChosen) Color(0xFF2D1619) else Color(0xFF17181E)
                                        )
                                        .clickable {
                                            target = app.appId
                                            label = app.title
                                            customIconPath = app.localIconPath
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ShortcutIconView(
                                        customIconPath = app.localIconPath,
                                        fallbackRes = RemoteViewModel.resolveBundledIcon(app.appId, app.title),
                                        contentDescription = app.title,
                                        isYouTubeLive = false,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.title,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.appId,
                                            color = Color(0xFF71717A),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isChosen) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selecionado",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nome exibido no botão") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Modo LIVE DO YOUTUBE: precisa apenas do nome do canal!
                Text(
                    text = "Digite apenas o nome/@ do canal no YouTube (ex: sbt, cazetv, ippinheiros, jovempannews). O app encontra a live ativa e o ícone automaticamente!",
                    color = Color(0xFFA1A1AA),
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = target,
                    onValueChange = { newVal ->
                        target = newVal
                        if (label.isBlank() || label == currentShortcut.label) {
                            val cleanName = newVal.trim().removePrefix("@")
                            if (cleanName.isNotEmpty() && !cleanName.startsWith("http")) {
                                label = cleanName.replaceFirstChar { it.uppercase() }
                            }
                        }
                    },
                    label = { Text("Nome ou @ do Canal (ex: sbt, cazetv)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nome exibido no botão (ex: SBT, CazéTV)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Fechar", color = Color(0xFFA1A1AA))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        val finalTarget = target.trim()
                        val finalLabel = label.trim().ifEmpty {
                            finalTarget.removePrefix("@").ifEmpty { "Atalho ${selectedSlot + 1}" }
                        }
                        if (finalTarget.isNotEmpty()) {
                            onSaveShortcut(
                                selectedSlot,
                                finalLabel,
                                actionType,
                                finalTarget,
                                customIconPath
                            )
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Salvar Botão ${selectedSlot + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
