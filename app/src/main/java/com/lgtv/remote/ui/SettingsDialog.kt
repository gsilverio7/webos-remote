package com.lgtv.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsDialog(
    initialIp: String,
    initialMac: String,
    initialBroadcast: String,
    onDismiss: () -> Unit,
    onDiscover: ((String) -> Unit) -> Unit,
    onSave: (ip: String, mac: String, broadcast: String) -> Unit
) {
    var ip by remember { mutableStateOf(initialIp) }
    var mac by remember { mutableStateOf(initialMac) }
    var broadcast by remember { mutableStateOf(initialBroadcast) }

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
                .background(Color(0xFF141519), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF2E3038), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "Configurar TV LG webOS",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Conexão direta via Wi-Fi (SSAP WebSocket + Wake-on-LAN)",
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text("IP da TV (ex: 192.168.0.4)") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = mac,
                onValueChange = { mac = it },
                label = { Text("MAC da TV (Wake-on-LAN)") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = broadcast,
                onValueChange = { broadcast = it },
                label = { Text("IP de Broadcast (ex: 192.168.0.255)") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = {
                    onDiscover { discoveredIp ->
                        ip = discoveredIp
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buscar TV na Rede (SSDP)", color = Color(0xFFE4E4E7))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = Color(0xFFA1A1AA))
                }
                Button(
                    onClick = {
                        onSave(ip, mac, broadcast)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
