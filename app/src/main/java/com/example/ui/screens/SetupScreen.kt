package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.engine.DiceMode
import com.example.engine.GameMode
import com.example.engine.MapManager
import com.example.engine.MatchConfig
import com.example.engine.RoboLevel
import com.example.ui.components.DuoBackdrop
import com.example.ui.components.DuoBrandWordmark
import com.example.ui.components.DuoCard
import com.example.ui.components.DuoCircleIconButton
import com.example.ui.components.DuoGradientButton
import com.example.ui.game.MapPreviewCanvas
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDuoPalette

@Composable
fun SetupScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onPlayClick: (MatchConfig) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenTutorial: () -> Unit,
    onButtonClickSound: () -> Unit
) {
    val palette = LocalDuoPalette.current
    val maps = remember { MapManager.metadataList }
    var showRules by remember { mutableStateOf(false) }

    var mode by remember(settings.defaultMode) { mutableStateOf(settings.defaultMode) }
    var level by remember(settings.defaultLevel) { mutableStateOf(settings.defaultLevel) }
    var diceMode by remember(settings.defaultDiceMode) { mutableStateOf(settings.defaultDiceMode) }
    var tokens by remember(settings.defaultTokens) { mutableIntStateOf(settings.defaultTokens) }
    var mapIdx by remember(settings.defaultMap) {
        mutableIntStateOf(maps.indexOfFirst { it.id == settings.defaultMap }.coerceAtLeast(0))
    }
    var name0 by remember(settings.player1Name) { mutableStateOf(settings.player1Name) }
    var name1 by remember(settings.player2Name) { mutableStateOf(settings.player2Name) }

    val selectedMap = maps[mapIdx]

    DuoBackdrop(reducedMotion = settings.reducedMotion) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val screenWidth = maxWidth
            val isWideLayout = screenWidth > 680.dp && maxHeight > 440.dp

            DuoCard(
                modifier = Modifier
                    .widthIn(max = 980.dp)
                    .fillMaxWidth()
                    .heightIn(max = 780.dp),
                cornerRadius = 28.dp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DuoBrandWordmark(compact = screenWidth < 400.dp)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DuoCircleIconButton(
                                onClick = {
                                    onButtonClickSound()
                                    onOpenTutorial()
                                },
                                size = 40.dp,
                                modifier = Modifier.testTag("setup_tutorial_button")
                            ) {
                                Icon(Icons.Rounded.School, contentDescription = "Interactive Tutorial", modifier = Modifier.size(19.dp))
                            }
                            DuoCircleIconButton(
                                onClick = {
                                    onButtonClickSound()
                                    onOpenStats()
                                },
                                size = 40.dp,
                                modifier = Modifier.testTag("setup_stats_button")
                            ) {
                                Icon(Icons.Rounded.BarChart, contentDescription = "Stats & Match History", modifier = Modifier.size(19.dp))
                            }
                            DuoCircleIconButton(
                                onClick = {
                                    onButtonClickSound()
                                    showRules = true
                                },
                                size = 40.dp,
                                modifier = Modifier.testTag("setup_rules_button")
                            ) {
                                Icon(Icons.Rounded.Info, contentDescription = "How to play", modifier = Modifier.size(19.dp))
                            }
                            DuoCircleIconButton(
                                onClick = {
                                    onButtonClickSound()
                                    onOpenSettings()
                                },
                                size = 40.dp,
                                modifier = Modifier.testTag("setup_settings_button")
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings", modifier = Modifier.size(19.dp))
                            }
                        }
                    }

                    // Body: Side-by-side on wide screens, scrollable column on narrow/short screens
                    if (isWideLayout) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SetupOptionsColumn(
                                    mode = mode,
                                    onModeChange = {
                                        onButtonClickSound()
                                        mode = it
                                        onUpdateSettings { s -> s.copy(defaultMode = it) }
                                    },
                                    level = level,
                                    onLevelChange = {
                                        onButtonClickSound()
                                        level = it
                                        onUpdateSettings { s -> s.copy(defaultLevel = it) }
                                    },
                                    diceMode = diceMode,
                                    onDiceModeChange = {
                                        onButtonClickSound()
                                        diceMode = it
                                        onUpdateSettings { s -> s.copy(defaultDiceMode = it) }
                                    },
                                    tokens = tokens,
                                    onTokensChange = {
                                        onButtonClickSound()
                                        tokens = it
                                        onUpdateSettings { s -> s.copy(defaultTokens = it) }
                                    },
                                    name0 = name0,
                                    onName0Change = {
                                        name0 = it.take(14)
                                        onUpdateSettings { s -> s.copy(player1Name = name0.trim()) }
                                    },
                                    name1 = name1,
                                    onName1Change = {
                                        name1 = it.take(14)
                                        onUpdateSettings { s -> s.copy(player2Name = name1.trim()) }
                                    }
                                )
                            }

                            Column(modifier = Modifier.weight(1.22f)) {
                                MapSelectorPanel(
                                    maps = maps,
                                    mapIdx = mapIdx,
                                    previewHeight = 250.dp,
                                    onSelectMap = { idx ->
                                        onButtonClickSound()
                                        mapIdx = (idx + maps.size) % maps.size
                                        val mId = maps[mapIdx].id
                                        onUpdateSettings { s -> s.copy(defaultMap = mId) }
                                    }
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SetupOptionsColumn(
                                mode = mode,
                                onModeChange = {
                                    onButtonClickSound()
                                    mode = it
                                    onUpdateSettings { s -> s.copy(defaultMode = it) }
                                },
                                level = level,
                                onLevelChange = {
                                    onButtonClickSound()
                                    level = it
                                    onUpdateSettings { s -> s.copy(defaultLevel = it) }
                                },
                                diceMode = diceMode,
                                onDiceModeChange = {
                                    onButtonClickSound()
                                    diceMode = it
                                    onUpdateSettings { s -> s.copy(defaultDiceMode = it) }
                                },
                                tokens = tokens,
                                onTokensChange = {
                                    onButtonClickSound()
                                    tokens = it
                                    onUpdateSettings { s -> s.copy(defaultTokens = it) }
                                },
                                name0 = name0,
                                onName0Change = {
                                    name0 = it.take(14)
                                    onUpdateSettings { s -> s.copy(player1Name = name0.trim()) }
                                },
                                name1 = name1,
                                onName1Change = {
                                    name1 = it.take(14)
                                    onUpdateSettings { s -> s.copy(player2Name = name1.trim()) }
                                }
                            )

                            MapSelectorPanel(
                                maps = maps,
                                mapIdx = mapIdx,
                                previewHeight = 190.dp,
                                onSelectMap = { idx ->
                                    onButtonClickSound()
                                    mapIdx = (idx + maps.size) % maps.size
                                    val mId = maps[mapIdx].id
                                    onUpdateSettings { s -> s.copy(defaultMap = mId) }
                                }
                            )
                        }
                    }

                    // Play CTA
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DuoGradientButton(
                            onClick = {
                                onButtonClickSound()
                                val p1 = name0.trim().ifBlank { "Player 1" }.take(14)
                                val p2 = if (mode == GameMode.ROBO) "Robo" else name1.trim().ifBlank { "Player 2" }.take(14)
                                onPlayClick(
                                    MatchConfig(
                                        mode = mode,
                                        level = level,
                                        diceMode = diceMode,
                                        tokens = tokens,
                                        map = selectedMap.id,
                                        names = listOf(p1, p2),
                                        passDeviceInHumo = settings.passDeviceInHumo,
                                        allowUndo = settings.allowUndo
                                    )
                                )
                            },
                            modifier = Modifier
                                .widthIn(min = 210.dp)
                                .testTag("setup_play_button"),
                            contentPadding = PaddingValues(horizontal = 42.dp, vertical = 14.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Text("Play", style = MaterialTheme.typography.titleMedium)
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
private fun SetupOptionsColumn(
    mode: GameMode,
    onModeChange: (GameMode) -> Unit,
    level: RoboLevel,
    onLevelChange: (RoboLevel) -> Unit,
    diceMode: DiceMode,
    onDiceModeChange: (DiceMode) -> Unit,
    tokens: Int,
    onTokensChange: (Int) -> Unit,
    name0: String,
    onName0Change: (String) -> Unit,
    name1: String,
    onName1Change: (String) -> Unit
) {
    val palette = LocalDuoPalette.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Mode
        Column {
            FieldLabel("MODE")
            SegmentedPillRow(
                items = GameMode.entries,
                selected = mode,
                onSelect = onModeChange,
                tagPrefix = "mode"
            ) { item, selected ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) palette.ink else palette.muted
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = palette.muted
                    )
                }
            }
        }

        // Robo Level
        AnimatedVisibility(visible = mode == GameMode.ROBO) {
            Column {
                FieldLabel("ROBO LEVEL")
                SegmentedPillRow(
                    items = RoboLevel.entries,
                    selected = level,
                    onSelect = onLevelChange,
                    tagPrefix = "level"
                ) { item, selected ->
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) palette.ink else palette.muted
                    )
                }
            }
        }

        // Dice Mode
        Column {
            FieldLabel("DICE MODE")
            SegmentedPillRow(
                items = DiceMode.entries,
                selected = diceMode,
                onSelect = onDiceModeChange,
                tagPrefix = "dice_mode"
            ) { item, selected ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (item == DiceMode.CLASSIC) "Classic" else "Drama",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) palette.ink else palette.muted
                    )
                    Text(
                        text = if (item == DiceMode.CLASSIC) "Fair 1/6 odds" else "Adaptive odds",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = palette.muted
                    )
                }
            }
        }

        // Tokens Each
        Column {
            FieldLabel("TOKENS EACH")
            SegmentedPillRow(
                items = (1..6).toList(),
                selected = tokens,
                onSelect = onTokensChange,
                tagPrefix = "tokens"
            ) { item, selected ->
                Text(
                    text = "$item",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) palette.ink else palette.muted
                )
            }
        }

        // Player Names
        Column {
            FieldLabel("PLAYERS")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerNameField(
                    value = name0,
                    onValueChange = onName0Change,
                    placeholder = "Player 1",
                    dotColor = palette.players[0].main,
                    softColor = palette.players[0].soft,
                    enabled = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player1_name_input")
                )
                PlayerNameField(
                    value = if (mode == GameMode.ROBO) "" else name1,
                    onValueChange = onName1Change,
                    placeholder = if (mode == GameMode.ROBO) "Robo" else "Player 2",
                    dotColor = palette.players[1].main,
                    softColor = palette.players[1].soft,
                    enabled = mode != GameMode.ROBO,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player2_name_input")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapSelectorPanel(
    maps: List<com.example.engine.MapMetadata>,
    mapIdx: Int,
    previewHeight: androidx.compose.ui.unit.Dp,
    onSelectMap: (Int) -> Unit
) {
    val palette = LocalDuoPalette.current
    val currentMap = maps[mapIdx]

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FieldLabel("MAP")
            Text(
                text = "${mapIdx + 1} / ${maps.size} • ${currentMap.styleTag}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            palette.players[0].soft.copy(alpha = 0.7f),
                            palette.players[1].soft.copy(alpha = 0.7f)
                        )
                    )
                )
                .border(1.dp, palette.hairline, RoundedCornerShape(20.dp))
        ) {
            MapPreviewCanvas(
                mapId = currentMap.id,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
            DuoCircleIconButton(
                onClick = { onSelectMap(mapIdx - 1) },
                size = 36.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .testTag("map_prev_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous map")
            }
            DuoCircleIconButton(
                onClick = { onSelectMap(mapIdx + 1) },
                size = 36.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .testTag("map_next_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next map")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = currentMap.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink
                )
                Text(
                    text = "${currentMap.ringTiles} tiles • ${currentMap.safeCount} safe • ${currentMap.boostCount} boost" +
                        if (currentMap.hasJunction) " • Junction" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.players[0].deep,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = currentMap.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            maps.forEachIndexed { idx, m ->
                val isSelected = idx == mapIdx
                Surface(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelectMap(idx) }
                        .testTag("map_chip_${m.id.id}"),
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) Color.Transparent else if (palette.isDark) palette.tileFill else Color.White,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) palette.players[0].main else palette.hairline
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .then(
                                if (isSelected) Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(palette.players[0].soft, palette.players[1].soft)
                                    )
                                ) else Modifier
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = m.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 12.sp,
                            color = if (isSelected) palette.ink else palette.muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    val palette = LocalDuoPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted,
        modifier = Modifier.padding(bottom = 5.dp)
    )
}

@Composable
private fun <T> SegmentedPillRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    tagPrefix: String,
    itemContent: @Composable (T, Boolean) -> Unit
) {
    val palette = LocalDuoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.segBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { idx, item ->
            val isOn = item == selected
            val shape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .then(
                        if (isOn) {
                            Modifier
                                .shadow(4.dp, shape, ambientColor = palette.boardShadow, spotColor = palette.boardShadow)
                                .background(if (palette.isDark) palette.tileFill else Color.White, shape)
                        } else {
                            Modifier
                        }
                    )
                    .clip(shape)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .testTag("${tagPrefix}_$idx"),
                contentAlignment = Alignment.Center
            ) {
                itemContent(item, isOn)
            }
        }
    }
}

@Composable
private fun PlayerNameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    dotColor: Color,
    softColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalDuoPalette.current
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (palette.isDark) palette.tileFill else Color.White,
        border = BorderStroke(1.5.dp, palette.hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(softColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (enabled) palette.ink else palette.muted
                    ),
                    cursorBrush = SolidColor(dotColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
