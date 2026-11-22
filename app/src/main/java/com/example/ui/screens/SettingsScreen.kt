package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AnimationIntensity
import com.example.data.AppSettings
import com.example.data.ColorPreset
import com.example.data.ThemeMode
import com.example.engine.DiceMode
import com.example.engine.MapManager
import com.example.engine.RoboLevel
import com.example.ui.components.DuoBackdrop
import com.example.ui.components.DuoCard
import com.example.ui.components.DuoCircleIconButton
import com.example.ui.theme.LocalDuoPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onButtonClickSound: () -> Unit
) {
    val palette = LocalDuoPalette.current

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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DuoCircleIconButton(
                    onClick = {
                        onButtonClickSound()
                        onBack()
                    },
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.ink
                    )
                    Text(
                        text = "Appearance, gameplay rules, audio & motion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Appearance
                SettingsSectionCard(title = "Appearance") {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            SettingChoiceChip(
                                label = mode.label,
                                selected = settings.themeMode == mode,
                                onClick = {
                                    onButtonClickSound()
                                    onUpdateSettings { it.copy(themeMode = mode) }
                                },
                                modifier = Modifier.testTag("theme_chip_${mode.id}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Blue & Pink Palette",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ColorPreset.entries.forEach { preset ->
                            val isSelected = settings.colorPreset == preset
                            Surface(
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onButtonClickSound()
                                        onUpdateSettings { it.copy(colorPreset = preset) }
                                    }
                                    .testTag("preset_chip_${preset.id}"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) palette.players[0].soft else if (palette.isDark) palette.tileFill else Color.White,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) palette.players[0].main else palette.hairline
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(preset.p1MainHex))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(preset.p2MainHex))
                                        )
                                    }
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = palette.ink
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Gameplay Settings
                SettingsSectionCard(title = "Gameplay & Rules") {
                    Text(
                        text = "Dice Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.ink
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiceMode.entries.forEach { dMode ->
                            val selected = settings.defaultDiceMode == dMode
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        onButtonClickSound()
                                        onUpdateSettings { it.copy(defaultDiceMode = dMode) }
                                    }
                                    .testTag("settings_dice_${dMode.id}"),
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) palette.players[0].soft.copy(alpha = 0.7f) else if (palette.isDark) palette.tileFill else Color.White,
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) palette.players[0].main else palette.hairline
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = dMode.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = palette.ink
                                    )
                                    Text(
                                        text = dMode.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontSize = 12.sp,
                                        color = palette.muted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Default Robo Difficulty",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoboLevel.entries.forEach { lvl ->
                            SettingChoiceChip(
                                label = "${lvl.label} — ${lvl.description}",
                                selected = settings.defaultLevel == lvl,
                                onClick = {
                                    onButtonClickSound()
                                    onUpdateSettings { it.copy(defaultLevel = lvl) }
                                },
                                modifier = Modifier.testTag("settings_level_${lvl.id}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Preferred Map",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MapManager.metadataList.forEach { m ->
                            SettingChoiceChip(
                                label = m.name,
                                selected = settings.defaultMap == m.id,
                                onClick = {
                                    onButtonClickSound()
                                    onUpdateSettings { it.copy(defaultMap = m.id) }
                                },
                                modifier = Modifier.testTag("settings_map_${m.id.id}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Default Tokens Each",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.ink
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..6).forEach { count ->
                            SettingChoiceChip(
                                label = "$count",
                                selected = settings.defaultTokens == count,
                                onClick = {
                                    onButtonClickSound()
                                    onUpdateSettings { it.copy(defaultTokens = count) }
                                },
                                modifier = Modifier.testTag("settings_tokens_$count")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    SettingSwitchRow(
                        title = "Preview & Confirm Token Move",
                        subtitle = "First tap previews destination path; second tap confirms move",
                        checked = settings.confirmTokenMove,
                        onCheckedChange = { v ->
                            onButtonClickSound()
                            onUpdateSettings { it.copy(confirmTokenMove = v) }
                        },
                        testTag = "setting_confirm_move"
                    )
                    SettingSwitchRow(
                        title = "Pass-Device Screen in Humo Mode",
                        subtitle = "Show a turn handoff prompt between human players",
                        checked = settings.passDeviceInHumo,
                        onCheckedChange = { v ->
                            onButtonClickSound()
                            onUpdateSettings { it.copy(passDeviceInHumo = v) }
                        },
                        testTag = "setting_pass_device"
                    )
                    SettingSwitchRow(
                        title = "Allow Undo in Casual Matches",
                        subtitle = "Restore full game state & stats to before your last move",
                        checked = settings.allowUndo,
                        onCheckedChange = { v ->
                            onButtonClickSound()
                            onUpdateSettings { it.copy(allowUndo = v) }
                        },
                        testTag = "setting_allow_undo"
                    )
                }

                // 3. Audio Settings
                SettingsSectionCard(title = "Audio") {
                    SettingSwitchRow(
                        title = "Mute All Audio",
                        subtitle = "Silence both sound effects and victory music",
                        checked = settings.isMuted,
                        onCheckedChange = { v ->
                            onUpdateSettings { it.copy(isMuted = v) }
                        },
                        testTag = "setting_mute"
                    )
                    VolumeSliderRow(
                        label = "Master Volume",
                        value = settings.masterVolume,
                        enabled = !settings.isMuted,
                        onValueChange = { v -> onUpdateSettings { it.copy(masterVolume = v) } }
                    )
                    VolumeSliderRow(
                        label = "Sound Effects",
                        value = settings.sfxVolume,
                        enabled = !settings.isMuted,
                        onValueChange = { v -> onUpdateSettings { it.copy(sfxVolume = v) } }
                    )
                    VolumeSliderRow(
                        label = "Background Music",
                        value = settings.musicVolume,
                        enabled = !settings.isMuted,
                        onValueChange = { v -> onUpdateSettings { it.copy(musicVolume = v) } }
                    )
                }

                // 4. Haptics & Motion
                SettingsSectionCard(title = "Feedback & Motion") {
                    SettingSwitchRow(
                        title = "Haptic Feedback",
                        subtitle = "Tactile vibration on dice rolls, captures, and finishing home",
                        checked = settings.hapticFeedback,
                        onCheckedChange = { v ->
                            onButtonClickSound()
                            onUpdateSettings { it.copy(hapticFeedback = v) }
                        },
                        testTag = "setting_haptics"
                    )
                    Text(
                        text = "Animation Intensity",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.ink
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimationIntensity.entries.forEach { ai ->
                            SettingChoiceChip(
                                label = ai.label,
                                selected = settings.animationIntensity == ai,
                                onClick = {
                                    onButtonClickSound()
                                    onUpdateSettings { it.copy(animationIntensity = ai) }
                                },
                                modifier = Modifier.testTag("anim_chip_${ai.id}")
                            )
                        }
                    }
                    SettingSwitchRow(
                        title = "Reduced Motion",
                        subtitle = "Minimize floating orbs, confetti, and token hop arcs",
                        checked = settings.reducedMotion,
                        onCheckedChange = { v ->
                            onButtonClickSound()
                            onUpdateSettings { it.copy(reducedMotion = v) }
                        },
                        testTag = "setting_reduced_motion"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val palette = LocalDuoPalette.current
    DuoCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = palette.players[0].deep
            )
            content()
        }
    }
}

@Composable
private fun SettingChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDuoPalette.current
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) palette.players[0].soft else if (palette.isDark) palette.tileFill else Color.White,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) palette.players[0].main else palette.hairline
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) palette.ink else palette.muted
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val palette = LocalDuoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                color = palette.muted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.players[0].deep,
                uncheckedThumbColor = palette.muted,
                uncheckedTrackColor = palette.segBg
            )
        )
    }
}

@Composable
private fun VolumeSliderRow(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    val palette = LocalDuoPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) palette.ink else palette.muted
            )
            Text(
                text = "${(value * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.players[0].deep
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = palette.players[0].deep,
                activeTrackColor = palette.players[0].main,
                inactiveTrackColor = palette.segBg
            )
        )
    }
}
