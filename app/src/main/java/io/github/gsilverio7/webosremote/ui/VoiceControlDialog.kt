package io.github.gsilverio7.webosremote.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat

@Composable
fun VoiceControlDialog(
    onExecuteVoiceCommand: (spokenText: String) -> Unit,
    onDictateTextToTv: (spokenText: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var partialOrFinalText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Preparando microfone...") }
    var autoExecuteCommand by remember { mutableStateOf(true) }

    val speechRecognizer = remember {
        runCatching {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else null
        }.getOrNull()
    }

    // Fallback para intent de voz do sistema caso SpeechRecognizer direto falhe no aparelho
    val systemRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                partialOrFinalText = spoken
                statusMessage = "Reconhecido: \"$spoken\""
                if (autoExecuteCommand) {
                    onExecuteVoiceCommand(spoken)
                    onDismiss()
                }
            } else {
                statusMessage = "Nenhuma fala detectada. Toque no microfone para tentar novamente."
            }
        } else {
            statusMessage = "Toque no microfone para falar um comando."
        }
    }

    fun startVoiceCapture() {
        partialOrFinalText = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale o comando para a TV LG...")
        }

        if (speechRecognizer != null) {
            runCatching {
                isListening = true
                statusMessage = "Ouvindo pelo microfone do celular..."
                speechRecognizer.startListening(intent)
            }.onFailure {
                isListening = false
                runCatching { systemRecognizerLauncher.launch(intent) }
            }
        } else {
            runCatching {
                isListening = true
                systemRecognizerLauncher.launch(intent)
            }.onFailure {
                isListening = false
                statusMessage = "Serviço de reconhecimento de voz indisponível neste aparelho."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            statusMessage = "Permissão de microfone necessária para o controle por voz."
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                statusMessage = "Ouvindo... Fale agora!"
            }

            override fun onBeginningOfSpeech() {
                isListening = true
                statusMessage = "Captando sua voz..."
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListening = false
                statusMessage = "Processando comando..."
            }

            override fun onError(error: Int) {
                isListening = false
                if (partialOrFinalText.isNotBlank() && autoExecuteCommand) {
                    val spoken = partialOrFinalText.trim()
                    onExecuteVoiceCommand(spoken)
                    onDismiss()
                    return
                }
                statusMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não entendi. Toque no microfone e fale novamente."
                    else -> "Toque no microfone para falar com a TV."
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotEmpty()) {
                    partialOrFinalText = spoken
                    statusMessage = "Comando: \"$spoken\""
                    if (autoExecuteCommand) {
                        onExecuteVoiceCommand(spoken)
                        onDismiss()
                    }
                } else {
                    statusMessage = "Toque no microfone para falar novamente."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    partialOrFinalText = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        speechRecognizer?.setRecognitionListener(listener)

        onDispose {
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            startVoiceCapture()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.18f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141519), RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF2E3038), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Controle por Voz & IA",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusMessage,
                color = if (isListening) Color(0xFFFCA5A5) else Color(0xFFA1A1AA),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Botão central pulsante de Microfone
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(if (isListening) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(
                        if (isListening) Color(0xFFDC2626) else Color(0xFF27272A)
                    )
                    .border(
                        width = 2.dp,
                        color = if (isListening) Color(0xFFFCA5A5) else Color(0xFF52525B),
                        shape = CircleShape
                    )
                    .clickable {
                        if (isListening) {
                            runCatching { speechRecognizer?.stopListening() }
                            isListening = false
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                startVoiceCapture()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Microfone",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Caixa mostrando o texto transcrito em tempo real
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0B0E), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF23252C), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (partialOrFinalText.isNotBlank()) {
                        "\"$partialOrFinalText\""
                    } else {
                        "Exemplos:\n• \"Abrir YouTube\" ou \"Abrir Netflix\"\n• \"Live do SBT\" ou \"CazéTV ao vivo\"\n• \"Volume 15\", \"Aumentar volume\", \"Mudo\"\n• \"Pesquisar gols da rodada no YouTube\"\n• \"Desligar TV\" ou \"Ligar TV\""
                    },
                    color = if (partialOrFinalText.isNotBlank()) Color.White else Color(0xFF71717A),
                    fontSize = if (partialOrFinalText.isNotBlank()) 14.sp else 11.sp,
                    fontWeight = if (partialOrFinalText.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                    textAlign = if (partialOrFinalText.isNotBlank()) TextAlign.Center else TextAlign.Start
                )
            }

            if (partialOrFinalText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onExecuteVoiceCommand(partialOrFinalText.trim())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Executar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            onDictateTextToTv(partialOrFinalText.trim())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = Color(0xFFE4E4E7),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Digitar na TV", color = Color(0xFFE4E4E7), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

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
