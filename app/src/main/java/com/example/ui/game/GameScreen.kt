package com.example.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.engine.DiceEngine
import com.example.engine.DiceMode
import com.example.engine.DiceRollResult
import com.example.engine.GameState
import com.example.engine.MoveHistoryEntry
import com.example.ui.components.DuoBackdrop
import com.example.ui.components.DuoBrandWordmark
import com.example.ui.components.DuoCard
import com.example.ui.components.DuoCircleIconButton
import com.example.ui.components.DuoGradientButton
import com.example.ui.components.DuoSecondaryButton
import com.example.ui.screens.RulesDialog
import com.example.ui.theme.LocalDuoPalette
import kotlin.math.roundToInt

fun formatMatchDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

@Composable
fun GameScreen(
    matchState: MatchUiState,
    settings: AppSettings,
    anim: AnimationController,
    onPortraitDetected: (Boolean) -> Unit,
    onBoardTap: (BoardTapHit) -> Unit,
    onConfirmSelectedMove: () -> Unit,
    onDismissPassDevice: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onQuitToMenu: () -> Unit,
    onToggleInsight: () -> Unit,
    onToggleMoveHistory: () -> Unit,
    onUndo: () -> Unit,
    onToggleMute: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalDuoPalette.current
    val st = matchState.gameState ?: return
    var showRules by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showRules -> showRules = false
            matchState.showInsight -> onToggleInsight()
            matchState.showMoveHistory -> onToggleMoveHistory()
            matchState.isPaused -> onResume()
            else -> onPause()
        }
    }

    DuoBackdrop(reducedMotion = settings.reducedMotion) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top HUD Bar
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val showBrandText = maxWidth > 540.dp
                val showMapPill = maxWidth > 410.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (showBrandText) {
                        DuoBrandWordmark(compact = true)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudPill {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = "Match timer",
                                modifier = Modifier.size(15.dp),
                                tint = palette.muted
                            )
                            Text(
                                text = formatMatchDuration(matchState.elapsedMs),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink,
                                modifier = Modifier.testTag("game_timer")
                            )
                        }
                        HudPill {
                            Text(
                                text = "${st.moves}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink,
                                modifier = Modifier.testTag("game_moves")
                            )
                            Text(
                                text = "moves",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.muted
                            )
                        }
                        if (showMapPill) {
                            HudPill {
                                Text(
                                    text = "${st.map.name} • ${if (st.diceMode == DiceMode.CLASSIC) "Classic" else "Drama"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (matchState.config.allowUndo) {
                            DuoCircleIconButton(
                                onClick = onUndo,
                                active = matchState.canUndo,
                                size = 38.dp,
                                modifier = Modifier.testTag("undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = "Undo last move",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DuoCircleIconButton(
                            onClick = onToggleMoveHistory,
                            highlighted = matchState.showMoveHistory,
                            size = 38.dp,
                            modifier = Modifier.testTag("history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Move history",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DuoCircleIconButton(
                            onClick = onToggleInsight,
                            highlighted = matchState.showInsight,
                            size = 38.dp,
                            modifier = Modifier.testTag("insight_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BarChart,
                                contentDescription = "Dice insight",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DuoCircleIconButton(
                            onClick = onToggleMute,
                            active = !settings.isMuted,
                            size = 38.dp,
                            modifier = Modifier.testTag("sound_button")
                        ) {
                            Icon(
                                imageVector = if (settings.isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                contentDescription = if (settings.isMuted) "Unmute audio" else "Mute audio",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DuoCircleIconButton(
                            onClick = onPause,
                            size = 38.dp,
                            modifier = Modifier.testTag("pause_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Pause,
                                contentDescription = "Pause match",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Stage Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
            ) {
                DuoBoardCanvas(
                    gameState = st,
                    anim = anim,
                    turnPhase = matchState.turnPhase,
                    canRoll = matchState.canRoll,
                    legalTokens = matchState.legalTokens,
                    selectedTokenIndex = matchState.selectedTokenIndex,
                    previewPlan = matchState.previewPlan,
                    rollValue = matchState.currentRollValue,
                    hints = matchState.playerHints,
                    reducedMotion = settings.reducedMotion,
                    onPortraitDetected = onPortraitDetected,
                    onTapHit = onBoardTap,
                    modifier = Modifier.testTag("game_board_canvas")
                )

                // Selected token confirmation pill
                val preview = matchState.previewPlan
                androidx.compose.animation.AnimatedVisibility(
                    visible = matchState.turnPhase == TurnPhase.PICK_TOKEN && preview != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 86.dp)
                ) {
                    if (preview != null) {
                        val actionLabel = when {
                            preview.reachedHome -> "Move to Home"
                            preview.boosted -> "Move + Boost (+3)"
                            preview.entered -> "Enter Board"
                            else -> "Confirm Move (${matchState.currentRollValue})"
                        }
                        DuoGradientButton(
                            onClick = onConfirmSelectedMove,
                            modifier = Modifier.testTag("confirm_move_button"),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            if (preview.boosted) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            } else if (preview.reachedHome) {
                                Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // Toasts
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (toast in matchState.toasts) {
                        ToastBanner(toast)
                    }
                }

                // Dice Insight Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = matchState.showInsight,
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut() + scaleOut(targetScale = 0.94f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(10.dp)
                ) {
                    DiceInsightCard(
                        gameState = st,
                        lastRollInsight = matchState.lastRollInsight,
                        onClose = onToggleInsight
                    )
                }

                // Move History Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = matchState.showMoveHistory,
                    enter = fadeIn() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut() + scaleOut(targetScale = 0.94f),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(10.dp)
                ) {
                    MoveHistoryCard(
                        entries = matchState.moveHistory,
                        onClose = onToggleMoveHistory
                    )
                }

                // Humo Pass-Device Overlay
                if (matchState.passDevicePlayerIndex != null && !matchState.isPaused) {
                    val pIdx = matchState.passDevicePlayerIndex
                    val pl = st.players[pIdx]
                    val pCol = palette.players[pIdx]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xB8181D30))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        contentAlignment = Alignment.Center
                    ) {
                        DuoCard(
                            modifier = Modifier
                                .padding(20.dp)
                                .widthIn(max = 360.dp),
                            contentPadding = PaddingValues(24.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = pCol.soft,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.SwapHoriz,
                                            contentDescription = null,
                                            tint = pCol.deep,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Pass Device",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.muted
                                )
                                Text(
                                    text = "${pl.name}'s Turn",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = pCol.deep,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Hand the device to ${pl.name} before rolling.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.muted,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                DuoGradientButton(
                                    onClick = onDismissPassDevice,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pass_device_ready_button")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Text("I'm Ready")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pause Modal
        if (matchState.isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x661E243B))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                DuoCard(
                    modifier = Modifier
                        .padding(20.dp)
                        .widthIn(max = 360.dp),
                    contentPadding = PaddingValues(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.ink
                        )
                        Text(
                            text = "The clock and animations are frozen. Take your time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        DuoGradientButton(
                            onClick = onResume,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("resume_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text("Resume")
                        }
                        DuoSecondaryButton(
                            onClick = onRestart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("restart_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Restart Match")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DuoSecondaryButton(
                                onClick = { showRules = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Rules")
                            }
                            DuoSecondaryButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pause_settings_button")
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Settings")
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onQuitToMenu)
                                .testTag("quit_menu_button"),
                            color = Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Main Menu",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.muted
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showRules) {
            RulesDialog(onDismiss = { showRules = false })
        }
    }
}

@Composable
private fun HudPill(content: @Composable () -> Unit) {
    val palette = LocalDuoPalette.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (palette.isDark) palette.tileFill else Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, palette.hairline),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ToastBanner(toast: GameToast) {
    val palette = LocalDuoPalette.current
    val bgBrush = when (toast.tone) {
        ToastTone.PRIMARY -> Brush.horizontalGradient(listOf(palette.players[0].main, palette.bolt))
        ToastTone.HIT -> Brush.horizontalGradient(listOf(palette.players[1].deep, Color(0xFFFF8F6B)))
        ToastTone.GOLD -> Brush.horizontalGradient(listOf(palette.gold, Color(0xFFFF9F7A)))
        ToastTone.SOFT -> Brush.horizontalGradient(
            listOf(
                if (palette.isDark) palette.tileFill else Color.White,
                if (palette.isDark) palette.tileFill else Color.White
            )
        )
    }
    val textColor = if (toast.tone == ToastTone.SOFT) palette.ink else Color.White
    Surface(
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 10.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(bgBrush, RoundedCornerShape(999.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = toast.text,
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
        }
    }
}

@Composable
private fun DiceInsightCard(
    gameState: GameState,
    lastRollInsight: Pair<Int, DiceRollResult>?,
    onClose: () -> Unit
) {
    val palette = LocalDuoPalette.current
    val activeTurn = gameState.turn
    val (previewWeights, previewReasons) = remember(gameState, lastRollInsight) {
        if (lastRollInsight != null) {
            lastRollInsight.second.normalizedProbabilities to lastRollInsight.second.reasons
        } else {
            val (w, r) = DiceEngine.previewWeights(gameState, activeTurn)
            val sum = w.sum().coerceAtLeast(1e-6f)
            w.map { (it / sum) * 100f } to r
        }
    }

    DuoCard(
        modifier = Modifier
            .widthIn(min = 240.dp, max = 290.dp)
            .testTag("dice_insight_panel"),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (gameState.diceMode == DiceMode.CLASSIC) "Classic Dice (Fair 1/6)" else "Drama Dice (Adaptive)",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.ink
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Dice Insight",
                    tint = palette.muted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClose)
                )
            }

            if (lastRollInsight != null) {
                val (pIdx, roll) = lastRollInsight
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(palette.players[pIdx].main)
                    )
                    Text(
                        text = "${gameState.players[pIdx].name} rolled ${roll.value}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                }
            } else {
                Text(
                    text = "Current odds for ${gameState.players[activeTurn].name}:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
            }

            // Normalized Probability Bars (1..6)
            val maxProb = (previewWeights.maxOrNull() ?: 20f).coerceAtLeast(16.7f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                previewWeights.forEachIndexed { idx, pct ->
                    val face = idx + 1
                    val isHit = lastRollInsight?.second?.value == face
                    val barFrac = (pct / maxProb).coerceIn(0.15f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = "${pct.roundToInt()}%",
                            fontSize = 9.sp,
                            fontWeight = if (isHit) FontWeight.Bold else FontWeight.Medium,
                            color = if (isHit) palette.players[lastRollInsight!!.first].deep else palette.muted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((40f * barFrac).dp)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(
                                    if (isHit) Brush.verticalGradient(
                                        listOf(palette.players[0].main, palette.players[1].main)
                                    ) else Brush.verticalGradient(
                                        listOf(palette.hairline, palette.hairline)
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$face",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                    }
                }
            }

            Text(
                text = if (previewReasons.isEmpty()) "Plain roll, no adjustment applied."
                else previewReasons.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.5.sp,
                color = palette.ink
            )
            Text(
                text = if (gameState.diceMode == DiceMode.CLASSIC) {
                    "Uniform RNG with exact 1/6 (16.7%) probability per face. Switchable in Settings."
                } else {
                    "Adaptive mode: odds shift symmetrically within bounded weights [0.5–1.7] for both players."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 11.sp,
                color = palette.muted
            )
        }
    }
}

@Composable
private fun MoveHistoryCard(
    entries: List<MoveHistoryEntry>,
    onClose: () -> Unit
) {
    val palette = LocalDuoPalette.current
    DuoCard(
        modifier = Modifier
            .widthIn(min = 240.dp, max = 300.dp)
            .fillMaxHeight(0.68f)
            .testTag("move_history_panel"),
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Move History (${entries.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.ink
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Move History",
                    tint = palette.muted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onClose)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No moves yet. Roll the dice!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(entries, key = { it.id }) { e ->
                        val pCol = palette.players[e.playerIndex]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pCol.soft.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, palette.hairline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(pCol.main)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${e.moveNumber} ${e.playerName} • Rolled ${e.rollValue}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = palette.ink
                                    )
                                    val detail = buildString {
                                        if (e.entered) append("Entered start")
                                        else append("Step ${e.fromProg} → ${e.toProg}")
                                        if (e.boosted) append(" • Boost +3")
                                        if (e.capturedCount > 0) append(" • Capture!")
                                        if (e.reachedHome) append(" • Home!")
                                        if (e.extraTurn) append(" • Bonus turn")
                                    }
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 11.sp,
                                        color = palette.muted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
