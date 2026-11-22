package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.MatchRecord
import com.example.data.PlayerAggregateStats
import com.example.ui.components.DuoBackdrop
import com.example.ui.components.DuoCard
import com.example.ui.components.DuoCircleIconButton
import com.example.ui.components.DuoSecondaryButton
import com.example.ui.game.formatMatchDuration
import com.example.ui.theme.LocalDuoPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    stats: PlayerAggregateStats,
    matches: List<MatchRecord>,
    settings: AppSettings,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    onButtonClickSound: () -> Unit
) {
    val palette = LocalDuoPalette.current
    val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    BackHandler { onBack() }

    DuoBackdrop(reducedMotion = settings.reducedMotion) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DuoCircleIconButton(
                        onClick = {
                            onButtonClickSound()
                            onBack()
                        },
                        modifier = Modifier.testTag("stats_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "Player Stats & History",
                            style = MaterialTheme.typography.headlineMedium,
                            color = palette.ink
                        )
                        Text(
                            text = "Offline match ledger stored locally",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted
                        )
                    }
                }
                if (matches.isNotEmpty()) {
                    DuoSecondaryButton(
                        onClick = {
                            onButtonClickSound()
                            onClearHistory()
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("stats_clear_button")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Clear", fontSize = 13.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                item {
                    DuoCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp,
                        contentPadding = PaddingValues(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "PLAYER 1 LIFETIME SUMMARY",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.players[0].deep
                            )
                            val winRate = if (stats.totalMatches > 0) {
                                "${(stats.p1Wins * 100) / stats.totalMatches}%"
                            } else "—"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatTile("Matches", "${stats.totalMatches}", Modifier.weight(1f))
                                StatTile("Wins / Losses", "${stats.p1Wins} / ${stats.p1Losses}", Modifier.weight(1f))
                                StatTile("Win Rate", winRate, Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatTile("Captures", "${stats.totalCaptures}", Modifier.weight(1f))
                                StatTile(
                                    "Avg Time",
                                    if (stats.totalMatches > 0) formatMatchDuration(stats.averageDurationMs) else "—",
                                    Modifier.weight(1f)
                                )
                                StatTile("Streak / Best", "${stats.currentWinStreak} / ${stats.bestWinStreak}", Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatTile(
                                    "vs Robo Wins",
                                    "${stats.roboWins}/${stats.roboMatches}",
                                    Modifier.weight(1f)
                                )
                                StatTile("Favorite Map", stats.favoriteMap, Modifier.weight(1f))
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "RECENT MATCHES (${matches.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                if (matches.isEmpty()) {
                    item {
                        DuoCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 22.dp,
                            contentPadding = PaddingValues(28.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEvents,
                                    contentDescription = null,
                                    tint = palette.muted.copy(alpha = 0.6f),
                                    modifier = Modifier.size(38.dp)
                                )
                                Text(
                                    text = "No completed matches yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.ink
                                )
                                Text(
                                    text = "Finish a Humo or Robo match to record stats and streaks here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.muted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(matches, key = { it.id }) { m ->
                        val winCol = palette.players[m.winnerIndex.coerceIn(0, 1)]
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = palette.cardBg,
                            border = BorderStroke(1.dp, palette.hairline),
                            shadowElevation = 2.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(winCol.main)
                                        )
                                        Text(
                                            text = "${m.winnerName} won",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = winCol.deep
                                        )
                                    }
                                    Text(
                                        text = dateFormatter.format(Date(m.timestampMs)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.muted
                                    )
                                }
                                Text(
                                    text = "${m.player1Name} vs ${m.player2Name} • ${m.mapName} (${m.tokensCount} tokens) • ${
                                        if (m.diceModeId == "classic") "Classic" else "Drama"
                                    }",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.ink
                                )
                                Text(
                                    text = "Duration ${formatMatchDuration(m.durationMs)} • ${m.totalMoves} moves • Captures ${m.p1Captures}-${m.p2Captures}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp,
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

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalDuoPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (palette.isDark) palette.tileFill else Color.White,
        border = BorderStroke(1.dp, palette.hairline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}
