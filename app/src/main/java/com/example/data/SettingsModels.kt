package com.example.data

import com.example.engine.DiceMode
import com.example.engine.GameMode
import com.example.engine.MapId
import com.example.engine.MatchConfig
import com.example.engine.RoboLevel

enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System Default"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark")
}

enum class ColorPreset(
    val id: String,
    val label: String,
    val p1MainHex: Long,
    val p1DeepHex: Long,
    val p1LightHex: Long,
    val p1SoftHex: Long,
    val p1InkHex: Long,
    val p2MainHex: Long,
    val p2DeepHex: Long,
    val p2LightHex: Long,
    val p2SoftHex: Long,
    val p2InkHex: Long
) {
    AZURE_ROSE(
        id = "azure_rose",
        label = "Azure & Rose",
        p1MainHex = 0xFF7AAEFF,
        p1DeepHex = 0xFF3E78DC,
        p1LightHex = 0xFFD2E3FF,
        p1SoftHex = 0xFFEAF2FF,
        p1InkHex = 0xFF1F4FA8,
        p2MainHex = 0xFFFF8FB2,
        p2DeepHex = 0xFFDE4D86,
        p2LightHex = 0xFFFFD3E2,
        p2SoftHex = 0xFFFFEBF2,
        p2InkHex = 0xFFA6285B
    ),
    COBALT_CORAL(
        id = "cobalt_coral",
        label = "Cobalt & Coral",
        p1MainHex = 0xFF5B95FF,
        p1DeepHex = 0xFF285EC7,
        p1LightHex = 0xFFC5DCFF,
        p1SoftHex = 0xFFE5F0FF,
        p1InkHex = 0xFF1A4191,
        p2MainHex = 0xFFFF827A,
        p2DeepHex = 0xFFD94A43,
        p2LightHex = 0xFFFFCFCB,
        p2SoftHex = 0xFFFFECEA,
        p2InkHex = 0xFF9C241E
    ),
    GLACIER_BERRY(
        id = "glacier_berry",
        label = "Glacier & Berry",
        p1MainHex = 0xFF5EC2F5,
        p1DeepHex = 0xFF2186BF,
        p1LightHex = 0xFFC8ECFF,
        p1SoftHex = 0xFFE6F7FF,
        p1InkHex = 0xFF125A85,
        p2MainHex = 0xFFEB7AC8,
        p2DeepHex = 0xFFC23B98,
        p2LightHex = 0xFFFAD0ED,
        p2SoftHex = 0xFFFEEAF8,
        p2InkHex = 0xFF8C1C69
    ),
    SAPPHIRE_BLOSSOM(
        id = "sapphire_blossom",
        label = "Sapphire & Blossom",
        p1MainHex = 0xFF6B8AFF,
        p1DeepHex = 0xFF3B57CF,
        p1LightHex = 0xFFCFD9FF,
        p1SoftHex = 0xFFEBEEFF,
        p1InkHex = 0xFF23399C,
        p2MainHex = 0xFFFF9CC9,
        p2DeepHex = 0xFFD95294,
        p2LightHex = 0xFFFFD9EB,
        p2SoftHex = 0xFFFFEFF7,
        p2InkHex = 0xFF9E2563
    );

    companion object {
        fun fromId(id: String): ColorPreset = entries.firstOrNull { it.id == id } ?: AZURE_ROSE
    }
}

enum class AnimationIntensity(val id: String, val label: String, val speedMultiplier: Float) {
    EXPRESS("express", "Fast & Snappy", 0.65f),
    NORMAL("normal", "Balanced", 1.0f),
    HIGH("high", "Dramatic", 1.25f);

    companion object {
        fun fromId(id: String): AnimationIntensity = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorPreset: ColorPreset = ColorPreset.AZURE_ROSE,
    val defaultMode: GameMode = GameMode.ROBO,
    val defaultLevel: RoboLevel = RoboLevel.NORMAL,
    val defaultDiceMode: DiceMode = DiceMode.CLASSIC,
    val defaultMap: MapId = MapId.ORBIT,
    val defaultTokens: Int = 4,
    val player1Name: String = "",
    val player2Name: String = "",
    val passDeviceInHumo: Boolean = true,
    val confirmTokenMove: Boolean = true,
    val allowUndo: Boolean = true,
    val masterVolume: Float = 0.9f,
    val musicVolume: Float = 0.5f,
    val sfxVolume: Float = 0.9f,
    val isMuted: Boolean = false,
    val hapticFeedback: Boolean = true,
    val animationIntensity: AnimationIntensity = AnimationIntensity.NORMAL,
    val reducedMotion: Boolean = false,
    val hasSeenTutorial: Boolean = false
) {
    fun toMatchConfig(): MatchConfig = MatchConfig(
        mode = defaultMode,
        level = defaultLevel,
        diceMode = defaultDiceMode,
        tokens = defaultTokens.coerceIn(1, 6),
        map = defaultMap,
        names = listOf(
            player1Name.ifBlank { "Player 1" }.take(14),
            if (defaultMode == GameMode.ROBO) "Robo" else player2Name.ifBlank { "Player 2" }.take(14)
        ),
        passDeviceInHumo = passDeviceInHumo,
        allowUndo = allowUndo
    )
}
