package com.lgtv.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

@Composable
fun TvKeyboardDialog(
    onSendText: (text: String, replace: Boolean, pressEnter: Boolean) -> Unit,
    onBackspace: () -> Unit,
    onEnterKey: () -> Unit,
    onSearchYouTube: (query: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var submitEnterAfterText by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(120)
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
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
                .padding(20.dp)
        ) {
            Text(
                text = "Teclado para a TV",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Digite no celular para preencher o campo de busca/texto aberto na TV ou buscar direto no YouTube.",
                color = Color(0xFFA1A1AA),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Digite o texto ou busca...") },
                singleLine = true,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotEmpty()) {
                            onSendText(text, false, submitEnterAfterText)
                            text = ""
                        } else {
                            onEnterKey()
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Checkbox(
                    checked = submitEnterAfterText,
                    onCheckedChange = { submitEnterAfterText = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFEF4444),
                        uncheckedColor = Color(0xFF71717A)
                    )
                )
                Text(
                    text = "Confirmar (Enter) na TV após enviar texto",
                    color = Color(0xFFD4D4D8),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botões de controle direto do campo da TV: Apagar (Backspace), Enter na TV, Enviar Texto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBackspace,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Apagar na TV",
                        tint = Color(0xFFE4E4E7),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apagar", color = Color(0xFFE4E4E7), fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onEnterKey,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardReturn,
                        contentDescription = "Enter na TV",
                        tint = Color(0xFFE4E4E7),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enter TV", color = Color(0xFFE4E4E7), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (text.isNotEmpty()) {
                        onSendText(text, false, submitEnterAfterText)
                        text = ""
                    }
                },
                enabled = text.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enviar para o Campo da TV", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSearchYouTube(text.trim())
                        onDismiss()
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Buscar e Reproduzir no YouTube",
                    color = Color(0xFFE4E4E7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Fechar", color = Color(0xFFA1A1AA))
                }
            }
        }
    }
}
