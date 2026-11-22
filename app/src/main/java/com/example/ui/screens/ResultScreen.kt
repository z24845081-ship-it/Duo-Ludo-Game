package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.engine.DiceMode
import com.example.engine.GameMode
import com.example.ui.components.DuoBackdrop
import com.example.ui.components.DuoCard
import com.example.ui.components.DuoCircleIconButton
import com.example.ui.components.DuoGradientButton
import com.example.ui.components.DuoSecondaryButton
import com.example.ui.game.MatchUiState
import com.example.ui.game.formatMatchDuration
import com.example.ui.theme.LocalDuoPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    var xFrac: Float,
    var yPx: Float,
    var vx: Float,
    var vy: Float,
    val w: Float,
    val h: Float,
    var angle: Float,
    val va: Float,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(
    matchState: MatchUiState,
    settings: AppSettings,
    onRematch: () -> Unit,
    onNewSetup: () -> Unit,
    onToggleMusic: (Boolean) -> Unit
) {
    val palette = LocalDuoPalette.current
    val st = matchState.gameState ?: return
    val winner = matchState.winnerIndex ?: 0
    val wPlayer = st.players[winner]
    val lPlayer = st.players[1 - winner]
    val humanLost = matchState.config.mode == GameMode.ROBO && winner == 1
    var musicPlaying by remember { mutableStateOf(!settings.isMuted) }

    BackHandler {
        onNewSetup()
    }

    val confettiColors = remember(palette, winner) {
        listOf(
            palette.players[winner].main,
            palette.players[winner].deep,
            palette.players[1 - winner].main,
            palette.gold,
            palette.bolt,
            Color.White
        )
    }

    val confetti = remember {
        List(100) {
            ConfettiPiece(
                xFrac = Random.nextFloat(),
                yPx = -Random.nextFloat() * 700f,
                vx = (Random.nextFloat() - 0.5f) * 60f,
                vy = 90f + Random.nextFloat() * 140f,
                w = 8f + Random.nextFloat() * 8f,
                h = 5f + Random.nextFloat() * 6f,
                angle = Random.nextFloat() * 360f,
                va = (Random.nextFloat() - 0.5f) * 260f,
                color = confettiColors[Random.nextInt(confettiColors.size)]
            )
        }
    }
    var tickCounter by remember { mutableStateOf(0L) }

    LaunchedEffect(settings.reducedMotion) {
        if (settings.reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (last == 0L) 0.016f else ((nanos - last) / 1e9f).coerceIn(0f, 0.05f)
                last = nanos
                for (b in confetti) {
                    b.xFrac += ((b.vx + sin(b.angle * 0.03f) * 24f) * dt) / 1000f
                    b.yPx += b.vy * dt
                    b.angle += b.va * dt
                }
                tickCounter = nanos
            }
        }
    }

    DuoBackdrop(reducedMotion = settings.reducedMotion) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!settings.reducedMotion) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Read tickCounter to redraw each frame
                    tickCounter
                    for (b in confetti) {
                        if (b.yPx > size.height + 40f) continue
                        val cx = b.xFrac * size.width
                        val cy = b.yPx
                        rotate(degrees = b.angle, pivot = Offset(cx, cy)) {
                            scale(
                                scaleX = 1f,
                                scaleY = abs(cos(b.angle * 0.03f)) * 0.8f + 0.2f,
                                pivot = Offset(cx, cy)
                            ) {
                                drawRect(
                                    color = b.color,
                                    topLeft = Offset(cx - b.w / 2f, cy - b.h / 2f),
                                    size = Size(b.w, b.h)
                                )
                            }
                        }
                    }
                }
            }

            DuoCard(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth(),
                cornerRadius = 30.dp,
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .shadow(10.dp, CircleShape, ambientColor = palette.gold.copy(alpha = 0.5f))
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(palette.players[0].soft, palette.players[1].soft)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = "Winner Trophy",
                            tint = palette.gold,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "MATCH COMPLETE",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                        letterSpacing = 2.5.sp
                    )

                    Text(
                        text = if (humanLost) "Robo wins this one" else "${wPlayer.name} wins!",
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.players[winner].deep,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("result_winner_title")
                    )

                    Text(
                        text = if (humanLost) "${lPlayer.name}, the robot was quicker this time."
                        else "${lPlayer.name} fought hard. Ready for a rematch?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                        textAlign = TextAlign.Center
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ResultMetaPill("Time", formatMatchDuration(matchState.elapsedMs))
                        ResultMetaPill("Moves", "${st.moves}")
                        ResultMetaPill("Map", st.map.name)
                        ResultMetaPill("Dice", if (st.diceMode == DiceMode.CLASSIC) "Classic" else "Drama")
                    }

                    // Head-to-head player stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (p in 0..1) {
                            val pl = st.players[p]
                            val col = palette.players[p]
                            val homeCount = pl.tokens.count { it == st.map.home }
                            val isWin = p == winner
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                color = if (palette.isDark) palette.tileFill else Color.White,
                                border = BorderStroke(
                                    width = if (isWin) 2.dp else 1.dp,
                                    color = if (isWin) col.main else palette.hairline
                                ),
                                shadowElevation = if (isWin) 6.dp else 1.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(col.main)
                                        )
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = palette.ink,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = col.soft
                                        ) {
                                            Text(
                                                text = if (isWin) "WINNER" else "2ND",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = col.ink,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    StatRow("Home", "$homeCount/${st.n}")
                                    StatRow("Captures", "${pl.stats.captures}")
                                    StatRow("Tokens lost", "${pl.stats.lost}")
                                    StatRow("Sixes", "${pl.stats.sixes}")
                                    StatRow("Rolls", "${pl.stats.rolls}")
                                    StatRow("Bonus turns", "${pl.stats.extra}")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DuoGradientButton(
                            onClick = onRematch,
                            modifier = Modifier.testTag("result_rematch_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Rematch")
                        }
                        DuoSecondaryButton(
                            onClick = onNewSetup,
                            modifier = Modifier.testTag("result_new_setup_button")
                        ) {
                            Text("New setup")
                        }
                        DuoCircleIconButton(
                            onClick = {
                                val next = !musicPlaying
                                musicPlaying = next
                                onToggleMusic(next)
                            },
                            active = musicPlaying,
                            modifier = Modifier.testTag("result_music_button")
                        ) {
                            Icon(
                                imageVector = if (musicPlaying) Icons.Rounded.MusicNote else Icons.Rounded.MusicOff,
                                contentDescription = "Toggle result music",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultMetaPill(label: String, value: String) {
    val palette = LocalDuoPalette.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (palette.isDark) palette.tileFill else Color.White,
        border = BorderStroke(1.dp, palette.hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val palette = LocalDuoPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp, color = palette.muted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = palette.ink
        )
    }
}
