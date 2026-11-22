package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.ColorPreset

data class PlayerColorSet(
    val main: Color,
    val deep: Color,
    val light: Color,
    val soft: Color,
    val ink: Color
)

data class DuoPalette(
    val isDark: Boolean,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val surfaceBgTop: Color,
    val surfaceBgBottom: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val segBg: Color,
    val boardBgTop: Color,
    val boardBgBottom: Color,
    val boardShadow: Color,
    val roadBg: Color,
    val roadDash: Color,
    val tileFill: Color,
    val tileStroke: Color,
    val junctionFill: Color,
    val junctionStroke: Color,
    val gold: Color,
    val bolt: Color,
    val players: List<PlayerColorSet>
)

fun buildDuoPalette(preset: ColorPreset, isDark: Boolean): DuoPalette {
    val p1 = PlayerColorSet(
        main = Color(preset.p1MainHex),
        deep = Color(preset.p1DeepHex),
        light = Color(preset.p1LightHex),
        soft = if (isDark) Color(preset.p1DeepHex).copy(alpha = 0.24f) else Color(preset.p1SoftHex),
        ink = Color(preset.p1InkHex)
    )
    val p2 = PlayerColorSet(
        main = Color(preset.p2MainHex),
        deep = Color(preset.p2DeepHex),
        light = Color(preset.p2LightHex),
        soft = if (isDark) Color(preset.p2DeepHex).copy(alpha = 0.24f) else Color(preset.p2SoftHex),
        ink = Color(preset.p2InkHex)
    )

    return if (isDark) {
        DuoPalette(
            isDark = true,
            ink = Color(0xFFF0F4FF),
            muted = Color(0xFFA3B0D1),
            hairline = Color(0xFF2E3858),
            surfaceBgTop = Color(0xFF0F1526),
            surfaceBgBottom = Color(0xFF1A172B),
            cardBg = Color(0xE61B233A),
            cardBorder = Color(0xFF323E63),
            segBg = Color(0xFF141B30),
            boardBgTop = Color(0xFF1E2640),
            boardBgBottom = Color(0xFF151C32),
            boardShadow = Color(0x66060913),
            roadBg = Color(0xFF283354),
            roadDash = Color(0xFF3A4975),
            tileFill = Color(0xFF25304F),
            tileStroke = Color(0xFF445482),
            junctionFill = Color(0xFF312A54),
            junctionStroke = Color(0xFF9C88FF),
            gold = Color(0xFFF3BF59),
            bolt = Color(0xFFA08EFF),
            players = listOf(p1, p2)
        )
    } else {
        DuoPalette(
            isDark = false,
            ink = Color(0xFF262B42),
            muted = Color(0xFF6E7694),
            hairline = Color(0xFFE1E8F6),
            surfaceBgTop = Color(0xFFF8FAFF),
            surfaceBgBottom = Color(0xFFFDF8FB),
            cardBg = Color(0xEBFFFFFF),
            cardBorder = Color(0xFFFFFFFF),
            segBg = Color(0xFFEDF1FB),
            boardBgTop = Color(0xFFFFFFFF),
            boardBgBottom = Color(0xFFF3F6FE),
            boardShadow = Color(0x33465FAA),
            roadBg = Color(0xFFEDF2FF),
            roadDash = Color(0xFFDCE6FA),
            tileFill = Color(0xFFFFFFFF),
            tileStroke = Color(0xFFDAE3F5),
            junctionFill = Color(0xFFF2EDFF),
            junctionStroke = Color(0xFFBBA9FF),
            gold = Color(0xFFEDB54A),
            bolt = Color(0xFF8E7CF0),
            players = listOf(p1, p2)
        )
    }
}
