package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalDuoPalette

@Composable
fun DuoBackdrop(
    reducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalDuoPalette.current
    val transition = rememberInfiniteTransition(label = "backdrop_orbs")
    val drift by if (reducedMotion) {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb_drift"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.surfaceBgTop, palette.surfaceBgBottom)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val orb1Center = Offset(
                x = w * (0.08f + drift * 0.08f),
                y = h * (0.78f - drift * 0.06f)
            )
            val orb2Center = Offset(
                x = w * (0.94f - drift * 0.07f),
                y = h * (0.12f + drift * 0.05f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.players[0].main.copy(alpha = if (palette.isDark) 0.16f else 0.25f),
                        Color.Transparent
                    ),
                    center = orb1Center,
                    radius = w.coerceAtLeast(h) * 0.55f
                ),
                radius = w.coerceAtLeast(h) * 0.55f,
                center = orb1Center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.players[1].main.copy(alpha = if (palette.isDark) 0.16f else 0.24f),
                        Color.Transparent
                    ),
                    center = orb2Center,
                    radius = w.coerceAtLeast(h) * 0.50f
                ),
                radius = w.coerceAtLeast(h) * 0.50f,
                center = orb2Center
            )
        }
        content()
    }
}

@Composable
fun DuoLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val palette = LocalDuoPalette.current
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = w * (14f / 48f)
        val c1 = Offset(w * (18f / 48f), h * 0.5f)
        val c2 = Offset(w * (30f / 48f), h * 0.5f)

        val leftCircle = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(c1.x - r, c1.y - r, c1.x + r, c1.y + r))
        }
        val rightCircle = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(c2.x - r, c2.y - r, c2.x + r, c2.y + r))
        }
        val lens = Path.combine(PathOperation.Intersect, leftCircle, rightCircle)

        drawCircle(color = palette.players[0].main, radius = r, center = c1)
        drawCircle(color = palette.players[1].main.copy(alpha = 0.92f), radius = r, center = c2)
        drawPath(path = lens, color = Color.White.copy(alpha = 0.92f))
        drawCircle(
            color = palette.players[0].deep,
            radius = w * (2.6f / 48f),
            center = Offset(w * 0.5f, h * 0.5f)
        )
    }
}

@Composable
fun DuoBrandWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val palette = LocalDuoPalette.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        DuoLogoMark(size = if (compact) 26.dp else 32.dp)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = palette.ink)) { append("DUO ") }
                withStyle(SpanStyle(color = palette.players[1].deep)) { append("LUDO") }
            },
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun DuoCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalDuoPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .shadow(
                elevation = if (palette.isDark) 12.dp else 16.dp,
                shape = shape,
                ambientColor = palette.boardShadow,
                spotColor = palette.boardShadow
            ),
        shape = shape,
        color = palette.cardBg,
        border = BorderStroke(1.dp, palette.cardBorder.copy(alpha = 0.85f))
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun DuoGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalDuoPalette.current
    val shape = RoundedCornerShape(999.dp)
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            palette.players[0].main,
            Color(0xFF8F8EF2),
            palette.players[1].deep
        )
    )
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = shape,
                ambientColor = palette.players[0].deep.copy(alpha = 0.45f),
                spotColor = palette.players[1].deep.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(
                brush = gradient,
                alpha = if (enabled) 1f else 0.45f
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
fun DuoSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val palette = LocalDuoPalette.current
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = modifier.heightIn(min = 48.dp),
        shape = shape,
        color = if (palette.isDark) palette.tileFill else Color.White,
        border = BorderStroke(1.dp, palette.hairline),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides palette.ink) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun DuoCircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    highlighted: Boolean = false,
    size: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    val palette = LocalDuoPalette.current
    val bgColor = when {
        highlighted -> palette.players[0].soft
        palette.isDark -> palette.tileFill
        else -> Color.White.copy(alpha = 0.9f)
    }
    val borderColor = when {
        highlighted -> palette.players[0].main
        else -> palette.hairline
    }
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(4.dp, CircleShape, ambientColor = palette.boardShadow, spotColor = palette.boardShadow)
                .clip(CircleShape)
                .background(bgColor)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(
                LocalContentColor provides if (active) palette.ink else palette.muted.copy(alpha = 0.55f)
            ) {
                content()
            }
        }
    }
}
