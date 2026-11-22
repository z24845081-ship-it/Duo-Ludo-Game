package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DuoBackdrop
import com.example.ui.components.DuoCard
import com.example.ui.components.DuoLogoMark
import com.example.ui.theme.LocalDuoPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplashScreen(
    reducedMotion: Boolean,
    onComplete: () -> Unit
) {
    val palette = LocalDuoPalette.current
    var finished by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    fun finishOnce() {
        if (!finished) {
            finished = true
            onComplete()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = if (reducedMotion) 900 else 2200, easing = LinearEasing)
        )
        finishOnce()
    }

    val floatTransition = rememberInfiniteTransition(label = "splash_logo")
    val floatY by if (reducedMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        floatTransition.animateFloat(
            initialValue = 0f,
            targetValue = -6f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "float_y"
        )
    }
    val floatRot by if (reducedMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        floatTransition.animateFloat(
            initialValue = -2f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "float_rot"
        )
    }

    DuoBackdrop(reducedMotion = reducedMotion) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::finishOnce
                )
                .padding(20.dp)
                .testTag("splash_screen"),
            contentAlignment = Alignment.Center
        ) {
            DuoCard(
                modifier = Modifier.widthIn(max = 460.dp),
                cornerRadius = 32.dp,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DuoLogoMark(
                        size = 84.dp,
                        modifier = Modifier
                            .offset(y = floatY.dp)
                            .rotate(floatRot)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "WELCOME TO",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = palette.players[0].deep)) { append("DUO ") }
                            withStyle(SpanStyle(color = palette.players[1].deep)) { append("LUDO") }
                        },
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "A board built for exactly two. Five geometric maps, a clever Robo rival, and Classic or Drama dice.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.muted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Humo & Robo", "5 Maps", "Classic & Drama Dice").forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Transparent,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.9f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(palette.players[0].soft, palette.players[1].soft)
                                            )
                                        )
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.ink
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.segBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.value)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(palette.players[0].main, palette.players[1].main)
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap anywhere to continue",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.muted
                    )
                }
            }
        }
    }
}
