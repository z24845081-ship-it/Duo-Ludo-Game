package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.DiceMode
import com.example.engine.GameMode
import com.example.engine.MapId
import com.example.engine.RoboLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "duoludo_settings")

class LocalStorageManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_PRESET = stringPreferencesKey("color_preset")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val DEFAULT_LEVEL = stringPreferencesKey("default_level")
        val DEFAULT_DICE_MODE = stringPreferencesKey("default_dice_mode")
        val DEFAULT_MAP = stringPreferencesKey("default_map")
        val DEFAULT_TOKENS = intPreferencesKey("default_tokens")
        val PLAYER_1_NAME = stringPreferencesKey("player_1_name")
        val PLAYER_2_NAME = stringPreferencesKey("player_2_name")
        val PASS_DEVICE_HUMO = booleanPreferencesKey("pass_device_humo")
        val CONFIRM_TOKEN_MOVE = booleanPreferencesKey("confirm_token_move")
        val ALLOW_UNDO = booleanPreferencesKey("allow_undo")
        val MASTER_VOL = floatPreferencesKey("master_volume")
        val MUSIC_VOL = floatPreferencesKey("music_volume")
        val SFX_VOL = floatPreferencesKey("sfx_volume")
        val IS_MUTED = booleanPreferencesKey("is_muted")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val ANIM_INTENSITY = stringPreferencesKey("animation_intensity")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val HAS_SEEN_TUTORIAL = booleanPreferencesKey("has_seen_tutorial")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.id == prefs[Keys.THEME_MODE] } ?: ThemeMode.SYSTEM,
            colorPreset = ColorPreset.fromId(prefs[Keys.COLOR_PRESET] ?: ColorPreset.AZURE_ROSE.id),
            defaultMode = GameMode.entries.firstOrNull { it.id == prefs[Keys.DEFAULT_MODE] } ?: GameMode.ROBO,
            defaultLevel = RoboLevel.entries.firstOrNull { it.id == prefs[Keys.DEFAULT_LEVEL] } ?: RoboLevel.NORMAL,
            defaultDiceMode = DiceMode.entries.firstOrNull { it.id == prefs[Keys.DEFAULT_DICE_MODE] } ?: DiceMode.CLASSIC,
            defaultMap = MapId.fromId(prefs[Keys.DEFAULT_MAP] ?: MapId.ORBIT.id),
            defaultTokens = (prefs[Keys.DEFAULT_TOKENS] ?: 4).coerceIn(1, 6),
            player1Name = prefs[Keys.PLAYER_1_NAME] ?: "",
            player2Name = prefs[Keys.PLAYER_2_NAME] ?: "",
            passDeviceInHumo = prefs[Keys.PASS_DEVICE_HUMO] ?: true,
            confirmTokenMove = prefs[Keys.CONFIRM_TOKEN_MOVE] ?: true,
            allowUndo = prefs[Keys.ALLOW_UNDO] ?: true,
            masterVolume = (prefs[Keys.MASTER_VOL] ?: 0.9f).coerceIn(0f, 1f),
            musicVolume = (prefs[Keys.MUSIC_VOL] ?: 0.5f).coerceIn(0f, 1f),
            sfxVolume = (prefs[Keys.SFX_VOL] ?: 0.9f).coerceIn(0f, 1f),
            isMuted = prefs[Keys.IS_MUTED] ?: false,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            animationIntensity = AnimationIntensity.fromId(prefs[Keys.ANIM_INTENSITY] ?: AnimationIntensity.NORMAL.id),
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            hasSeenTutorial = prefs[Keys.HAS_SEEN_TUTORIAL] ?: false
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.id == prefs[Keys.THEME_MODE] } ?: ThemeMode.SYSTEM,
                colorPreset = ColorPreset.fromId(prefs[Keys.COLOR_PRESET] ?: ColorPreset.AZURE_ROSE.id),
                defaultMode = GameMode.entries.firstOrNull { it.id == prefs[Keys.DEFAULT_MODE] } ?: GameMode.ROBO,
                defaultLevel = RoboLevel.entries.firstOrNull { it.id == prefs[Keys.DEFAULT_LEVEL] } ?: RoboLevel.NORMAL,
                defaultDiceMode = DiceMode.entries.firstOrNull { it.id == prefs[Keys.DEFAULT_DICE_MODE] } ?: DiceMode.CLASSIC,
                defaultMap = MapId.fromId(prefs[Keys.DEFAULT_MAP] ?: MapId.ORBIT.id),
                defaultTokens = (prefs[Keys.DEFAULT_TOKENS] ?: 4).coerceIn(1, 6),
                player1Name = prefs[Keys.PLAYER_1_NAME] ?: "",
                player2Name = prefs[Keys.PLAYER_2_NAME] ?: "",
                passDeviceInHumo = prefs[Keys.PASS_DEVICE_HUMO] ?: true,
                confirmTokenMove = prefs[Keys.CONFIRM_TOKEN_MOVE] ?: true,
                allowUndo = prefs[Keys.ALLOW_UNDO] ?: true,
                masterVolume = prefs[Keys.MASTER_VOL] ?: 0.9f,
                musicVolume = prefs[Keys.MUSIC_VOL] ?: 0.5f,
                sfxVolume = prefs[Keys.SFX_VOL] ?: 0.9f,
                isMuted = prefs[Keys.IS_MUTED] ?: false,
                hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
                animationIntensity = AnimationIntensity.fromId(prefs[Keys.ANIM_INTENSITY] ?: AnimationIntensity.NORMAL.id),
                reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
                hasSeenTutorial = prefs[Keys.HAS_SEEN_TUTORIAL] ?: false
            )
            val next = transform(current)
            prefs[Keys.THEME_MODE] = next.themeMode.id
            prefs[Keys.COLOR_PRESET] = next.colorPreset.id
            prefs[Keys.DEFAULT_MODE] = next.defaultMode.id
            prefs[Keys.DEFAULT_LEVEL] = next.defaultLevel.id
            prefs[Keys.DEFAULT_DICE_MODE] = next.defaultDiceMode.id
            prefs[Keys.DEFAULT_MAP] = next.defaultMap.id
            prefs[Keys.DEFAULT_TOKENS] = next.defaultTokens.coerceIn(1, 6)
            prefs[Keys.PLAYER_1_NAME] = next.player1Name.take(14)
            prefs[Keys.PLAYER_2_NAME] = next.player2Name.take(14)
            prefs[Keys.PASS_DEVICE_HUMO] = next.passDeviceInHumo
            prefs[Keys.CONFIRM_TOKEN_MOVE] = next.confirmTokenMove
            prefs[Keys.ALLOW_UNDO] = next.allowUndo
            prefs[Keys.MASTER_VOL] = next.masterVolume.coerceIn(0f, 1f)
            prefs[Keys.MUSIC_VOL] = next.musicVolume.coerceIn(0f, 1f)
            prefs[Keys.SFX_VOL] = next.sfxVolume.coerceIn(0f, 1f)
            prefs[Keys.IS_MUTED] = next.isMuted
            prefs[Keys.HAPTIC_FEEDBACK] = next.hapticFeedback
            prefs[Keys.ANIM_INTENSITY] = next.animationIntensity.id
            prefs[Keys.REDUCED_MOTION] = next.reducedMotion
            prefs[Keys.HAS_SEEN_TUTORIAL] = next.hasSeenTutorial
        }
    }
}
