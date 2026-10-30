package com.lgtv.remote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Botão físico 3D em cinza escuro fosco com chanfro superior iluminado,
 * cavidade rebaixada no chassi e feedback tátil (vibração curta de tecla física).
 */
@Composable
fun PhysicalKeyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    surfaceTopColor: Color = Color(0xFF32343B),
    surfaceBottomColor: Color = Color(0xFF1E2025),
    pressedTopColor: Color = Color(0xFF1B1C20),
    pressedBottomColor: Color = Color(0xFF151619),
    bevelHighlightColor: Color = Color(0xFF50535E),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offsetY by animateDpAsState(
        targetValue = if (isPressed) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 50),
        label = "keyPressOffset"
    )

    // Cavidade escura no chassi do controle onde a tecla se encaixa
    Box(
        modifier = modifier
            .background(Color(0xFF040406), shape)
            .border(1.dp, Color(0xFF17181C), shape)
            .padding(bottom = 3.dp, top = 1.dp, start = 1.dp, end = 1.dp)
    ) {
        // Corpo 3D da tecla física
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = offsetY)
                .shadow(
                    elevation = if (isPressed) 1.dp else 5.dp,
                    shape = shape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(pressedTopColor, pressedBottomColor)
                        } else {
                            listOf(surfaceTopColor, surfaceBottomColor)
                        }
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(Color(0xFF282A30), Color(0xFF121316))
                        } else {
                            listOf(bevelHighlightColor, Color(0xFF101114))
                        }
                    ),
                    shape = shape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                    }
                )
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Botão físico circular VERMELHO unificado de Ligar / Desligar TV.
 */
@Composable
fun PhysicalRedPowerButton(
    isTvOnline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offsetY by animateDpAsState(
        targetValue = if (isPressed) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 50),
        label = "powerOffset"
    )

    // Cavidade circular com anel metálico escuro
    Box(
        modifier = modifier
            .size(size + 8.dp)
            .background(Color(0xFF040406), CircleShape)
            .border(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    colors = if (isTvOnline) {
                        listOf(Color(0xFFEF4444).copy(alpha = 0.55f), Color(0xFF1F2026))
                    } else {
                        listOf(Color(0xFF2B2D35), Color(0xFF121317))
                    }
                ),
                shape = CircleShape
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = offsetY)
                .shadow(
                    elevation = if (isPressed) 2.dp else 8.dp,
                    shape = CircleShape,
                    ambientColor = Color(0xFFDC2626),
                    spotColor = Color.Black
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(Color(0xFFB91C1C), Color(0xFF7F1D1D))
                        } else {
                            listOf(Color(0xFFEF4444), Color(0xFFB91C1C), Color(0xFF881313))
                        }
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = if (isPressed) {
                            listOf(Color(0xFFDC2626), Color(0xFF450A0A))
                        } else {
                            listOf(Color(0xFFFCA5A5), Color(0xFF7F1D1D))
                        }
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Ligar / Desligar TV",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * D-Pad Físico Circular (estilo controle físico LG Magic Remote) com anel direcional 3D
 * e botão OK central esculpido.
 */
@Composable
fun PhysicalCircularDPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Dp = 196.dp
) {
    val view = LocalView.current

    val upSource = remember { MutableInteractionSource() }
    val downSource = remember { MutableInteractionSource() }
    val leftSource = remember { MutableInteractionSource() }
    val rightSource = remember { MutableInteractionSource() }
    val okSource = remember { MutableInteractionSource() }

    val upPressed by upSource.collectIsPressedAsState()
    val downPressed by downSource.collectIsPressedAsState()
    val leftPressed by leftSource.collectIsPressedAsState()
    val rightPressed by rightSource.collectIsPressedAsState()
    val okPressed by okSource.collectIsPressedAsState()

    // Cavidade externa no corpo do controle
    Box(
        modifier = modifier
            .size(wheelSize + 10.dp)
            .background(Color(0xFF040406), CircleShape)
            .border(1.5.dp, Color(0xFF1E2026), CircleShape)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        // Anel direcional externo 3D
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF33353D),
                            Color(0xFF23252B),
                            Color(0xFF18191E)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF555864), Color(0xFF121316))
                    ),
                    shape = CircleShape
                )
        ) {
            // Sulcos diagonais de separação das 4 teclas do anel e relevo tátil
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Destaque sutil no quadrante pressionado
                if (upPressed) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 225f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
                if (rightPressed) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 315f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
                if (downPressed) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 45f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }
                if (leftPressed) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 135f,
                        sweepAngle = 90f,
                        useCenter = true
                    )
                }

                // Linhas diagonais de junção física do anel D-Pad (45°, 135°, 225°, 315°)
                val diag = r * 0.7071f
                val grooveColor = Color(0xFF0E0F12)
                drawLine(
                    color = grooveColor,
                    start = Offset(center.x - diag, center.y - diag),
                    end = Offset(center.x + diag, center.y + diag),
                    strokeWidth = 3f
                )
                drawLine(
                    color = grooveColor,
                    start = Offset(center.x + diag, center.y - diag),
                    end = Offset(center.x - diag, center.y + diag),
                    strokeWidth = 3f
                )

                // Anel interno sutil
                drawCircle(
                    color = Color(0xFF3E414B),
                    radius = r * 0.74f,
                    center = center,
                    style = Stroke(width = 1.2f)
                )
            }

            // Setor CIMA
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 76.dp, height = 56.dp)
                    .clickable(
                        interactionSource = upSource,
                        indication = null,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onUp()
                        }
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Cima",
                    tint = if (upPressed) Color.White else Color(0xFFD4D4D8),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(28.dp)
                )
            }

            // Setor BAIXO
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = 76.dp, height = 56.dp)
                    .clickable(
                        interactionSource = downSource,
                        indication = null,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onDown()
                        }
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Baixo",
                    tint = if (downPressed) Color.White else Color(0xFFD4D4D8),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .size(28.dp)
                )
            }

            // Setor ESQUERDA
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 56.dp, height = 76.dp)
                    .clickable(
                        interactionSource = leftSource,
                        indication = null,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onLeft()
                        }
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Esquerda",
                    tint = if (leftPressed) Color.White else Color(0xFFD4D4D8),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(28.dp)
                )
            }

            // Setor DIREITA
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 56.dp, height = 76.dp)
                    .clickable(
                        interactionSource = rightSource,
                        indication = null,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onRight()
                        }
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Direita",
                    tint = if (rightPressed) Color.White else Color(0xFFD4D4D8),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp)
                )
            }

            // Botão Central Físico "OK"
            val okOffset by animateDpAsState(
                targetValue = if (okPressed) 2.dp else 0.dp,
                animationSpec = tween(durationMillis = 50),
                label = "okOffset"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(82.dp)
                    .background(Color(0xFF0A0A0D), CircleShape)
                    .border(2.dp, Color(0xFF14151A), CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(y = okOffset)
                        .shadow(if (okPressed) 1.dp else 6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (okPressed) {
                                    listOf(Color(0xFF23252C), Color(0xFF17181C))
                                } else {
                                    listOf(Color(0xFF3B3E47), Color(0xFF25272E), Color(0xFF1B1C22))
                                }
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            brush = Brush.verticalGradient(
                                colors = if (okPressed) {
                                    listOf(Color(0xFF2E3038), Color(0xFF141519))
                                } else {
                                    listOf(Color(0xFF606472), Color(0xFF15161A))
                                }
                            ),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = okSource,
                            indication = null,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onOk()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "OK",
                            color = Color(0xFFF4F4F5),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
        }
    }
}
