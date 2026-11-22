package com.example.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.engine.GameEngine
import com.example.engine.GameState
import com.example.engine.MapGeometry
import com.example.engine.MapId
import com.example.engine.MapManager
import com.example.engine.MovePlan
import com.example.engine.TileShape
import com.example.engine.Vec2
import com.example.ui.theme.DuoPalette
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LocalDuoPalette
import com.example.ui.theme.PlayerColorSet
import com.example.ui.theme.PlusJakartaFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private const val TAU = (2.0 * PI).toFloat()

enum class TurnPhase {
    IDLE,
    ROLL,
    ROLLING,
    PICK_TOKEN,
    MOVING,
    PASS_DEVICE,
    OVER
}

sealed interface BoardTapHit {
    data object Dice : BoardTapHit
    data class Token(val tokenIndex: Int) : BoardTapHit
    data class Destination(val tokenIndex: Int) : BoardTapHit
}

data class BoardLayoutMetrics(
    val width: Float,
    val height: Float,
    val isLandscapeSidebar: Boolean,
    val stripRects: List<Rect>,
    val dockCenters: List<Offset>,
    val dockSize: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val tileRadius: Float,
    val slotSpacing: Float,
    val slotPositions: List<List<Offset>>
) {
    fun toScreen(pos: Vec2): Offset = Offset(offsetX + pos.x * scale, offsetY + pos.y * scale)
}

fun computeBoardLayout(
    width: Float,
    height: Float,
    map: MapGeometry,
    tokenCount: Int
): BoardLayoutMetrics? {
    if (width < 60f || height < 60f) return null

    val isLandscapeSidebar = width > height * 1.28f
    val pad = (min(width, height) * 0.025f).coerceIn(10f, 22f)

    val stripRects: List<Rect>
    val dockCenters: List<Offset>
    val dockSize: Float
    val areaLeft: Float
    val areaTop: Float
    val areaWidth: Float
    val areaHeight: Float

    if (isLandscapeSidebar) {
        val sideW = (width * 0.21f).coerceIn(140f, 230f)
        stripRects = listOf(
            Rect(pad, pad, pad + sideW, height - pad),
            Rect(width - pad - sideW, pad, width - pad, height - pad)
        )
        dockSize = (min(sideW, height) * 0.34f).coerceIn(44f, 70f)
        dockCenters = listOf(
            Offset(stripRects[0].center.x, stripRects[0].bottom - 18f - dockSize / 2f),
            Offset(stripRects[1].center.x, stripRects[1].top + 18f + dockSize / 2f)
        )
        areaLeft = stripRects[0].right + pad
        areaTop = pad
        areaWidth = (stripRects[1].left - pad - areaLeft).coerceAtLeast(80f)
        areaHeight = (height - pad * 2).coerceAtLeast(80f)
    } else {
        val stripH = if (height < 520f) 76f else (height * 0.135f).coerceIn(82f, 118f)
        stripRects = listOf(
            Rect(pad, height - pad - stripH, width - pad, height - pad),
            Rect(pad, pad, width - pad, pad + stripH)
        )
        dockSize = (stripH * 0.66f).coerceIn(44f, 72f)
        dockCenters = listOf(
            Offset(stripRects[0].right - 16f - dockSize / 2f, stripRects[0].center.y),
            Offset(stripRects[1].left + 16f + dockSize / 2f, stripRects[1].center.y)
        )
        areaLeft = pad
        areaTop = stripRects[1].bottom + pad * 0.7f
        areaWidth = (width - pad * 2).coerceAtLeast(80f)
        areaHeight = (stripRects[0].top - pad * 0.7f - areaTop).coerceAtLeast(80f)
    }

    val bw = map.bounds.maxX - map.bounds.minX
    val bh = map.bounds.maxY - map.bounds.minY
    val s = min(areaWidth / bw, areaHeight / bh)
    val ox = areaLeft + areaWidth / 2f - ((map.bounds.minX + map.bounds.maxX) / 2f) * s
    val oy = areaTop + areaHeight / 2f - ((map.bounds.minY + map.bounds.maxY) / 2f) * s
    val tr = (map.tileR * s * 0.86f).coerceIn(6.5f, if (isLandscapeSidebar) 24f else stripRects[0].height * 0.24f)

    val slotSpacing: Float
    val slotPositions: List<List<Offset>>
    if (isLandscapeSidebar) {
        val cols = 3
        val cellGap = (stripRects[0].width - 28f) / cols
        slotSpacing = cellGap
        val p0Slots = List(tokenCount) { i ->
            val col = i % cols
            val row = i / cols
            Offset(
                stripRects[0].left + 14f + cellGap * (col + 0.5f),
                stripRects[0].top + stripRects[0].height * 0.42f + row * (tr * 2.5f)
            )
        }
        val p1Slots = List(tokenCount) { i ->
            val col = i % cols
            val row = i / cols
            Offset(
                stripRects[1].left + 14f + cellGap * (col + 0.5f),
                stripRects[1].bottom - stripRects[1].height * 0.42f - row * (tr * 2.5f)
            )
        }
        slotPositions = listOf(p0Slots, p1Slots)
    } else {
        val rowMax = width - pad * 2 - 36f - dockSize * 1.7f
        slotSpacing = min(tr * 2.55f, rowMax / tokenCount.coerceAtLeast(1))
        val p0Slots = List(tokenCount) { i ->
            Offset(
                stripRects[0].left + 20f + tr + i * slotSpacing,
                stripRects[0].top + stripRects[0].height * 0.68f
            )
        }
        val p1Slots = List(tokenCount) { i ->
            Offset(
                stripRects[1].right - 20f - tr - i * slotSpacing,
                stripRects[1].top + stripRects[1].height * 0.32f
            )
        }
        slotPositions = listOf(p0Slots, p1Slots)
    }

    return BoardLayoutMetrics(
        width = width,
        height = height,
        isLandscapeSidebar = isLandscapeSidebar,
        stripRects = stripRects,
        dockCenters = dockCenters,
        dockSize = dockSize,
        scale = s,
        offsetX = ox,
        offsetY = oy,
        tileRadius = tr,
        slotSpacing = slotSpacing,
        slotPositions = slotPositions
    )
}

@Composable
fun DuoBoardCanvas(
    gameState: GameState,
    anim: AnimationController,
    turnPhase: TurnPhase,
    canRoll: Boolean,
    legalTokens: List<Int>,
    selectedTokenIndex: Int?,
    previewPlan: MovePlan?,
    rollValue: Int,
    hints: List<String>,
    reducedMotion: Boolean,
    onPortraitDetected: (Boolean) -> Unit,
    onTapHit: (BoardTapHit) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDuoPalette.current
    val textMeasurer = rememberTextMeasurer()

    var frameTimeMs by remember { mutableLongStateOf(0L) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastFrameNanos == 0L) 0.016f else ((nanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.05f)
                lastFrameNanos = nanos
                frameTimeMs = nanos / 1_000_000L
                anim.tickFx(dt)
            }
        }
    }

    // Track rest positions for hit testing
    var currentMetrics by remember { androidx.compose.runtime.mutableStateOf<BoardLayoutMetrics?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(gameState, turnPhase, canRoll, legalTokens, selectedTokenIndex) {
                detectTapGestures { tapOffset ->
                    val metrics = currentMetrics ?: return@detectTapGestures
                    val hit = hitTestBoard(
                        tap = tapOffset,
                        metrics = metrics,
                        state = gameState,
                        anim = anim,
                        phase = turnPhase,
                        canRoll = canRoll,
                        legalTokens = legalTokens,
                        selectedTokenIndex = selectedTokenIndex,
                        rollValue = rollValue
                    )
                    if (hit != null) onTapHit(hit)
                }
            }
    ) {
        val portrait = size.height > size.width
        if (gameState.portrait != portrait) {
            onPortraitDetected(portrait)
        }

        val metrics = computeBoardLayout(
            width = size.width,
            height = size.height,
            map = gameState.map,
            tokenCount = gameState.n
        ) ?: return@Canvas
        currentMetrics = metrics

        // 1. Player strips
        drawPlayerStrips(
            metrics = metrics,
            state = gameState,
            turnPhase = turnPhase,
            canRoll = canRoll,
            hints = hints,
            nowMs = frameTimeMs,
            reducedMotion = reducedMotion,
            palette = palette,
            textMeasurer = textMeasurer
        )

        // 2. Static game board
        drawStaticBoard(
            map = gameState.map,
            scale = metrics.scale,
            ox = metrics.offsetX,
            oy = metrics.offsetY,
            palette = palette,
            mini = false
        )

        // 3. Legal destination markers & selected token path preview
        if (turnPhase == TurnPhase.PICK_TOKEN && legalTokens.isNotEmpty()) {
            drawLegalAndPreviewOverlays(
                metrics = metrics,
                state = gameState,
                legalTokens = legalTokens,
                selectedTokenIndex = selectedTokenIndex,
                previewPlan = previewPlan,
                rollValue = rollValue,
                nowMs = frameTimeMs,
                reducedMotion = reducedMotion,
                palette = palette
            )
        }

        // 4. Tokens (rest + animated)
        val restMap = computeRestTokenScreenPositions(metrics, gameState, anim)
        val drawList = mutableListOf<TokenRenderItem>()
        val legalSet = if (turnPhase == TurnPhase.PICK_TOKEN) legalTokens.toSet() else emptySet()

        gameState.players.forEachIndexed { pIdx, pl ->
            pl.tokens.forEachIndexed { ti, _ ->
                val key = pIdx * 10 + ti
                val override = anim.tokenOverrides[key]
                if (override != null) {
                    val baseScreen = metrics.toScreen(override.boardPos)
                    val slotPos = metrics.slotPositions[override.slotPlayer][override.slotIndex]
                    val screenPos = when {
                        override.fromBaseSlot -> Offset(
                            slotPos.x + (baseScreen.x - slotPos.x) * override.progressFrac,
                            slotPos.y + (baseScreen.y - slotPos.y) * override.progressFrac
                        )
                        override.toBaseSlot -> Offset(
                            baseScreen.x + (slotPos.x - baseScreen.x) * override.progressFrac,
                            baseScreen.y + (slotPos.y - baseScreen.y) * override.progressFrac
                        )
                        else -> baseScreen
                    }
                    drawList.add(
                        TokenRenderItem(
                            playerIndex = pIdx,
                            tokenIndex = ti,
                            screenPos = screenPos,
                            scale = override.scale,
                            hopPx = override.hopFraction * metrics.tileRadius * 0.95f,
                            glow = false,
                            selected = false,
                            onTop = true
                        )
                    )
                } else {
                    val rest = restMap[key]
                    if (rest != null) {
                        val isLegal = pIdx == gameState.turn && legalSet.contains(ti)
                        val isSelected = pIdx == gameState.turn && selectedTokenIndex == ti
                        drawList.add(
                            TokenRenderItem(
                                playerIndex = pIdx,
                                tokenIndex = ti,
                                screenPos = rest.pos,
                                scale = if (isSelected) rest.scale * 1.14f else rest.scale,
                                hopPx = if (isSelected) metrics.tileRadius * 0.22f else 0f,
                                glow = isLegal,
                                selected = isSelected,
                                onTop = isSelected
                            )
                        )
                    }
                }
            }
        }

        drawList.sortWith(compareBy<TokenRenderItem> { if (it.onTop) 1 else 0 }.thenBy { it.screenPos.y })
        for (item in drawList) {
            drawToken(
                center = item.screenPos,
                radius = metrics.tileRadius,
                colorSet = palette.players[item.playerIndex],
                scale = item.scale,
                hopPx = item.hopPx,
                glow = item.glow,
                selected = item.selected,
                nowMs = frameTimeMs,
                reducedMotion = reducedMotion
            )
        }

        // 5. Particles & Ripples
        drawParticles(anim.particles, metrics, palette)

        // 6. Animated Dice
        drawDice(metrics, anim, canRoll, frameTimeMs, reducedMotion, palette)
    }
}

private data class RestScreenToken(val pos: Offset, val scale: Float)

private data class TokenRenderItem(
    val playerIndex: Int,
    val tokenIndex: Int,
    val screenPos: Offset,
    val scale: Float,
    val hopPx: Float,
    val glow: Boolean,
    val selected: Boolean,
    val onTop: Boolean
)

private fun computeRestTokenScreenPositions(
    metrics: BoardLayoutMetrics,
    state: GameState,
    anim: AnimationController
): Map<Int, RestScreenToken> {
    val groups = linkedMapOf<String, MutableList<Triple<Int, Int, Int>>>()
    val res = hashMapOf<Int, RestScreenToken>()

    state.players.forEachIndexed { pIdx, pl ->
        pl.tokens.forEachIndexed { ti, prog ->
            val key = pIdx * 10 + ti
            if (anim.tokenOverrides.containsKey(key)) return@forEachIndexed
            if (prog < 0) {
                res[key] = RestScreenToken(metrics.slotPositions[pIdx][ti], 1f)
            } else {
                val tk = GameEngine.tileKey(state.map, pIdx, prog)
                groups.getOrPut(tk) { mutableListOf() }.add(Triple(pIdx, ti, prog))
            }
        }
    }

    val tr = metrics.tileRadius
    for ((_, arr) in groups) {
        val count = arr.size
        val baseScreen = metrics.toScreen(GameEngine.posOf(state.map, arr[0].first, arr[0].third))
        arr.forEachIndexed { idx, (pIdx, ti, _) ->
            var ox = 0f
            var oy = 0f
            var sc = 1f
            if (count == 2) {
                ox = (if (idx == 0) -1f else 1f) * tr * 0.55f
                sc = 0.84f
            } else if (count > 2) {
                val a = (idx.toFloat() / count) * TAU + 0.5f
                val rad = tr * (0.45f + count * 0.1f)
                ox = cos(a) * rad
                oy = sin(a) * rad
                sc = 0.70f
            }
            res[pIdx * 10 + ti] = RestScreenToken(Offset(baseScreen.x + ox, baseScreen.y + oy), sc)
        }
    }
    return res
}

private fun hitTestBoard(
    tap: Offset,
    metrics: BoardLayoutMetrics,
    state: GameState,
    anim: AnimationController,
    phase: TurnPhase,
    canRoll: Boolean,
    legalTokens: List<Int>,
    selectedTokenIndex: Int?,
    rollValue: Int
): BoardTapHit? {
    val diceCenter = if (anim.diceMoving) {
        val from = metrics.dockCenters[anim.diceFromOwner]
        val to = metrics.dockCenters[anim.diceOwner]
        Offset(
            from.x + (to.x - from.x) * anim.diceMoveFraction,
            from.y + (to.y - from.y) * anim.diceMoveFraction
        )
    } else {
        metrics.dockCenters[anim.diceOwner]
    }

    if (canRoll && hypot(tap.x - diceCenter.x, tap.y - diceCenter.y) <= metrics.dockSize * 0.92f) {
        return BoardTapHit.Dice
    }

    if (phase == TurnPhase.PICK_TOKEN && legalTokens.isNotEmpty()) {
        val rest = computeRestTokenScreenPositions(metrics, state, anim)
        val turn = state.turn
        val tr = metrics.tileRadius

        // 1. Check direct tap on any legal token (prioritize closest, and prefer selectedTokenIndex if tied)
        var bestTi: Int? = null
        var bestDist = Float.POSITIVE_INFINITY
        for (ti in legalTokens) {
            val r = rest[turn * 10 + ti] ?: continue
            val d = hypot(tap.x - r.pos.x, tap.y - r.pos.y)
            if (d <= tr * 1.95f) {
                val adjustedDist = if (ti == selectedTokenIndex) d - 2f else d
                if (adjustedDist < bestDist) {
                    bestDist = adjustedDist
                    bestTi = ti
                }
            }
        }
        if (bestTi != null) {
            return BoardTapHit.Token(bestTi)
        }

        // 2. If a token is selected, tapping its destination preview ring confirms that move!
        if (selectedTokenIndex != null && legalTokens.contains(selectedTokenIndex)) {
            val plan = GameEngine.previewMove(state, turn, selectedTokenIndex, rollValue)
            if (plan != null) {
                val dest = metrics.toScreen(GameEngine.posOf(state.map, turn, plan.to))
                if (hypot(tap.x - dest.x, tap.y - dest.y) <= tr * 1.95f) {
                    return BoardTapHit.Destination(selectedTokenIndex)
                }
            }
        }

        // 3. Tapping any highlighted destination ring selects/moves the corresponding legal token
        for (ti in legalTokens) {
            val tk = state.players[turn].tokens[ti]
            val targetProg = if (tk < 0) 0 else tk + rollValue
            val dest = metrics.toScreen(GameEngine.posOf(state.map, turn, targetProg))
            if (hypot(tap.x - dest.x, tap.y - dest.y) <= tr * 1.75f) {
                return BoardTapHit.Destination(ti)
            }
        }
    }

    return null
}

private fun DrawScope.drawPlayerStrips(
    metrics: BoardLayoutMetrics,
    state: GameState,
    turnPhase: TurnPhase,
    canRoll: Boolean,
    hints: List<String>,
    nowMs: Long,
    reducedMotion: Boolean,
    palette: DuoPalette,
    textMeasurer: TextMeasurer
) {
    val tr = metrics.tileRadius
    val ds = metrics.dockSize * 1.24f

    for (p in 0..1) {
        val rect = metrics.stripRects[p]
        val pl = state.players[p]
        val col = palette.players[p]
        val active = state.turn == p && turnPhase != TurnPhase.OVER && turnPhase != TurnPhase.IDLE
        val corner = if (metrics.isLandscapeSidebar) 24f else rect.height * 0.30f

        // Soft glow / shadow behind strip
        drawRoundRect(
            color = if (active) col.main.copy(alpha = 0.28f) else palette.boardShadow.copy(alpha = 0.25f),
            topLeft = Offset(rect.left - if (active) 3f else 0f, rect.top + 3f),
            size = Size(rect.width + if (active) 6f else 0f, rect.height + if (active) 4f else 0f),
            cornerRadius = CornerRadius(corner + 3f, corner + 3f)
        )

        drawRoundRect(
            color = if (active) palette.cardBg else palette.cardBg.copy(alpha = 0.72f),
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = if (active) col.main else palette.hairline,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = if (active) 2.5f else 1.2f)
        )

        // Token home slots
        for (i in 0 until state.n) {
            val slotCenter = metrics.slotPositions[p][i]
            drawCircle(
                color = col.main.copy(alpha = 0.12f),
                radius = tr * 0.96f,
                center = slotCenter
            )
            drawCircle(
                color = col.main.copy(alpha = 0.38f),
                radius = tr * 0.96f,
                center = slotCenter,
                style = Stroke(
                    width = 1.4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
            )
        }

        // Dice dock
        val dock = metrics.dockCenters[p]
        val dockTL = Offset(dock.x - ds / 2f, dock.y - ds / 2f)
        drawRoundRect(
            color = col.main.copy(alpha = if (active) 0.18f else 0.08f),
            topLeft = dockTL,
            size = Size(ds, ds),
            cornerRadius = CornerRadius(ds * 0.26f, ds * 0.26f)
        )
        drawRoundRect(
            color = col.main.copy(alpha = if (active) 0.65f else 0.28f),
            topLeft = dockTL,
            size = Size(ds, ds),
            cornerRadius = CornerRadius(ds * 0.26f, ds * 0.26f),
            style = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
            )
        )
        if (active && canRoll) {
            val pu = if (reducedMotion) 0.6f else (sin(nowMs / 240.0).toFloat() + 1f) / 2f
            drawRoundRect(
                color = col.main.copy(alpha = 0.35f + pu * 0.5f),
                topLeft = Offset(dockTL.x - 4f - pu * 3f, dockTL.y - 4f - pu * 3f),
                size = Size(ds + 8f + pu * 6f, ds + 8f + pu * 6f),
                cornerRadius = CornerRadius(ds * 0.30f, ds * 0.30f),
                style = Stroke(width = 3f)
            )
        }

        // Strip label text
        val homeCount = pl.tokens.count { it == state.map.home }
        val displayName = pl.name + if (pl.bot) " • ROBO" else ""
        val hintText = if (active && hints[p].isNotBlank()) hints[p]
        else "Home $homeCount/${state.n}  •  Caps ${pl.stats.captures}"

        val nameFontSp = if (metrics.isLandscapeSidebar) 13.sp else 14.sp
        val hintFontSp = if (metrics.isLandscapeSidebar) 11.sp else 11.5.sp

        val nameLayout = textMeasurer.measure(
            text = displayName,
            style = TextStyle(
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = nameFontSp,
                color = palette.ink
            )
        )
        val hintLayout = textMeasurer.measure(
            text = hintText,
            style = TextStyle(
                fontFamily = InterFontFamily,
                fontWeight = if (active && hints[p].isNotBlank()) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = hintFontSp,
                color = if (active && hints[p].isNotBlank()) col.deep else palette.muted
            )
        )

        val dotR = 5.5f
        if (metrics.isLandscapeSidebar) {
            val nameY = if (p == 0) rect.top + 16f else rect.bottom - 52f
            val hintY = nameY + nameLayout.size.height + 4f
            drawCircle(
                color = col.main,
                radius = dotR,
                center = Offset(rect.left + 16f + dotR, nameY + nameLayout.size.height / 2f)
            )
            drawText(
                textLayoutResult = nameLayout,
                topLeft = Offset(rect.left + 16f + dotR * 2f + 8f, nameY)
            )
            drawText(
                textLayoutResult = hintLayout,
                topLeft = Offset(rect.left + 16f, hintY)
            )
        } else {
            val nameY = if (p == 0) rect.top + rect.height * 0.25f - nameLayout.size.height / 2f
            else rect.top + rect.height * 0.72f - nameLayout.size.height / 2f

            if (p == 0) {
                var x = rect.left + 18f
                drawCircle(color = col.main, radius = dotR, center = Offset(x + dotR, nameY + nameLayout.size.height / 2f))
                x += dotR * 2f + 8f
                drawText(textLayoutResult = nameLayout, topLeft = Offset(x, nameY))
                x += nameLayout.size.width + 12f
                val maxAvailable = rect.right - 20f - ds - 12f - x
                if (hintLayout.size.width <= maxAvailable) {
                    drawText(
                        textLayoutResult = hintLayout,
                        topLeft = Offset(x, nameY + (nameLayout.size.height - hintLayout.size.height) / 2f)
                    )
                }
            } else {
                var x = rect.right - 18f
                drawCircle(color = col.main, radius = dotR, center = Offset(x - dotR, nameY + nameLayout.size.height / 2f))
                x -= dotR * 2f + 8f
                drawText(textLayoutResult = nameLayout, topLeft = Offset(x - nameLayout.size.width, nameY))
                x -= nameLayout.size.width + 12f
                val minAllowed = rect.left + 20f + ds + 12f
                if (x - hintLayout.size.width >= minAllowed) {
                    drawText(
                        textLayoutResult = hintLayout,
                        topLeft = Offset(
                            x - hintLayout.size.width,
                            nameY + (nameLayout.size.height - hintLayout.size.height) / 2f
                        )
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLegalAndPreviewOverlays(
    metrics: BoardLayoutMetrics,
    state: GameState,
    legalTokens: List<Int>,
    selectedTokenIndex: Int?,
    previewPlan: MovePlan?,
    rollValue: Int,
    nowMs: Long,
    reducedMotion: Boolean,
    palette: DuoPalette
) {
    val turn = state.turn
    val col = palette.players[turn]
    val tr = metrics.tileRadius
    val pu = if (reducedMotion) 0.5f else (sin(nowMs / 220.0).toFloat() + 1f) / 2f

    // Highlight legal destinations
    val drawnDestinations = HashSet<Int>()
    for (ti in legalTokens) {
        val tk = state.players[turn].tokens[ti]
        val destProg = if (tk < 0) 0 else tk + rollValue
        if (!drawnDestinations.add(destProg)) continue
        val destPos = metrics.toScreen(GameEngine.posOf(state.map, turn, destProg))
        drawCircle(
            color = col.main.copy(alpha = 0.16f),
            radius = tr * (1.26f + pu * 0.12f),
            center = destPos
        )
        drawCircle(
            color = col.deep.copy(alpha = 0.6f + pu * 0.3f),
            radius = tr * (1.26f + pu * 0.12f),
            center = destPos,
            style = Stroke(
                width = 2.6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
            )
        )
    }

    // Draw step-by-step path preview for the selected token
    if (previewPlan != null) {
        val pathPoints = mutableListOf<Offset>()
        if (previewPlan.from >= 0) {
            pathPoints.add(metrics.toScreen(GameEngine.posOf(state.map, turn, previewPlan.from)))
        }
        for (prog in previewPlan.seq) {
            pathPoints.add(metrics.toScreen(GameEngine.posOf(state.map, turn, prog)))
        }

        if (pathPoints.size >= 2) {
            val previewPath = Path().apply {
                moveTo(pathPoints.first().x, pathPoints.first().y)
                for (i in 1 until pathPoints.size) {
                    lineTo(pathPoints[i].x, pathPoints[i].y)
                }
            }
            drawPath(
                path = previewPath,
                color = if (previewPlan.boosted) palette.bolt else col.deep,
                style = Stroke(
                    width = (tr * 0.24f).coerceAtLeast(3.2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), -(nowMs / 35f))
                )
            )
        }

        val finalPos = metrics.toScreen(GameEngine.posOf(state.map, turn, previewPlan.to))
        val targetColor = if (previewPlan.boosted) palette.bolt else col.deep
        drawCircle(
            color = targetColor.copy(alpha = 0.26f),
            radius = tr * 1.48f,
            center = finalPos
        )
        drawCircle(
            color = targetColor,
            radius = tr * 1.48f,
            center = finalPos,
            style = Stroke(width = 3.2f)
        )
    }
}

fun DrawScope.drawStaticBoard(
    map: MapGeometry,
    scale: Float,
    ox: Float,
    oy: Float,
    palette: DuoPalette,
    mini: Boolean
) {
    fun x(v: Float) = ox + v * scale
    fun y(v: Float) = oy + v * scale

    val r = map.tileR * scale * 0.86f
    val b = map.bounds
    val px = x(b.minX)
    val py = y(b.minY)
    val pw = (b.maxX - b.minX) * scale
    val ph = (b.maxY - b.minY) * scale
    val corner = min(pw, ph) * 0.055f

    // Board drop shadow
    drawRoundRect(
        color = palette.boardShadow,
        topLeft = Offset(px, py + r * 0.45f),
        size = Size(pw, ph),
        cornerRadius = CornerRadius(corner, corner)
    )

    // Board card surface
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.boardBgTop, palette.boardBgBottom),
            startY = py,
            endY = py + ph
        ),
        topLeft = Offset(px, py),
        size = Size(pw, ph),
        cornerRadius = CornerRadius(corner, corner)
    )
    drawRoundRect(
        color = palette.hairline,
        topLeft = Offset(px, py),
        size = Size(pw, ph),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.4f)
    )
    val inset = r * 0.6f
    val innerCorner = (corner - inset * 0.6f).coerceAtLeast(2f)
    drawRoundRect(
        color = palette.players[0].main.copy(alpha = 0.24f),
        topLeft = Offset(px + inset, py + inset),
        size = Size((pw - inset * 2f).coerceAtLeast(4f), (ph - inset * 2f).coerceAtLeast(4f)),
        cornerRadius = CornerRadius(innerCorner, innerCorner),
        style = Stroke(width = 1.2f)
    )

    // Road outline
    val roadPath = Path()
    var firstRoad = true
    for (i in map.dense.indices step 4) {
        val q = map.dense[i]
        if (firstRoad) {
            roadPath.moveTo(x(q.x), y(q.y))
            firstRoad = false
        } else {
            roadPath.lineTo(x(q.x), y(q.y))
        }
    }
    roadPath.close()

    drawPath(
        path = roadPath,
        color = palette.roadBg,
        style = Stroke(width = r * 2.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = roadPath,
        color = palette.roadDash,
        style = Stroke(
            width = (r * 0.09f).coerceAtLeast(1f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.5f, r * 0.75f), 0f)
        )
    )

    // Home lanes
    for (p in 0..1) {
        val e = map.pts[map.entry[p]]
        val lanePath = Path().apply {
            moveTo(x(e.x), y(e.y))
            for (q in map.lanes[p]) lineTo(x(q.x), y(q.y))
        }
        drawPath(
            path = lanePath,
            color = palette.players[p].light.copy(alpha = if (palette.isDark) 0.4f else 1f),
            style = Stroke(width = r * 2.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = lanePath,
            color = palette.players[p].soft,
            style = Stroke(width = r * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // Ring tiles
    val secondJunction = if (map.junction.size >= 2) map.junction.elementAt(1) else -1
    for (i in 0 until map.n) {
        if (i == secondJunction) continue
        val tx = x(map.pts[i].x)
        val ty = y(map.pts[i].y)
        val sp = map.start.indexOf(i)
        val gp = map.entry.indexOf(i)
        val isJ = map.junction.contains(i)
        val shape = if (isJ) TileShape.DIAMOND else map.tileShape

        // Tile shadow
        val shadowPath = buildTilePath(shape, tx, ty + r * 0.16f, r, map.ang[i])
        drawPath(path = shadowPath, color = palette.boardShadow.copy(alpha = 0.45f))

        // Tile body
        val tilePath = buildTilePath(shape, tx, ty, r, map.ang[i])
        val fillColor = when {
            sp >= 0 -> palette.players[sp].soft
            isJ -> palette.junctionFill
            else -> palette.tileFill
        }
        val strokeColor = when {
            sp >= 0 -> palette.players[sp].main
            gp >= 0 -> palette.players[gp].main
            isJ -> palette.junctionStroke
            else -> palette.tileStroke
        }
        drawPath(path = tilePath, color = fillColor)
        drawPath(
            path = tilePath,
            color = strokeColor,
            style = Stroke(
                width = (r * if (sp >= 0 || gp >= 0) 0.14f else 0.10f).coerceAtLeast(1.2f),
                join = StrokeJoin.Round
            )
        )

        if (mini) continue
        val gs = r * 0.52f
        when {
            sp >= 0 -> drawStarGlyph(Offset(tx, ty), gs * 1.05f, palette.players[sp].deep)
            map.safe.contains(i) -> drawStarGlyph(Offset(tx, ty), gs, palette.gold)
            map.boost.contains(i) -> drawBoltGlyph(Offset(tx, ty), gs * 1.05f, palette.bolt)
            gp >= 0 -> drawChevronGlyph(
                Offset(tx, ty),
                map.laneDir[gp],
                r * 0.9f,
                palette.players[gp].deep,
                (r * 0.14f).coerceAtLeast(1.6f)
            )
            isJ -> {
                val crossW = (r * 0.11f).coerceAtLeast(1.3f)
                drawLine(palette.junctionStroke, Offset(tx - gs * 0.7f, ty), Offset(tx + gs * 0.7f, ty), strokeWidth = crossW)
                drawLine(palette.junctionStroke, Offset(tx, ty - gs * 0.7f), Offset(tx, ty + gs * 0.7f), strokeWidth = crossW)
            }
        }
    }

    // Lane tiles + Home circles
    for (p in 0..1) {
        map.lanes[p].forEachIndexed { idx, q ->
            val lx = x(q.x)
            val ly = y(q.y)
            val isLast = idx == map.lane - 1
            if (isLast) {
                drawCircle(color = palette.boardShadow.copy(alpha = 0.45f), radius = r * 1.38f, center = Offset(lx, ly + r * 0.16f))
                drawCircle(color = palette.tileFill, radius = r * 1.38f, center = Offset(lx, ly))
                drawCircle(
                    color = palette.players[p].main,
                    radius = r * 1.38f,
                    center = Offset(lx, ly),
                    style = Stroke(width = (r * 0.16f).coerceAtLeast(2f))
                )
                drawCircle(color = palette.players[p].soft, radius = r * 0.98f, center = Offset(lx, ly))
                if (!mini) {
                    drawHouseGlyph(Offset(lx, ly - r * 0.05f), r * 0.52f, palette.players[p].deep)
                }
            } else {
                val sh = buildTilePath(map.tileShape, lx, ly + r * 0.12f, r * 0.8f, map.laneDir[p])
                drawPath(path = sh, color = palette.boardShadow.copy(alpha = 0.35f))
                val tp = buildTilePath(map.tileShape, lx, ly, r * 0.8f, map.laneDir[p])
                drawPath(path = tp, color = palette.tileFill)
                drawPath(
                    path = tp,
                    color = palette.players[p].main,
                    style = Stroke(width = (r * 0.11f).coerceAtLeast(1.2f), join = StrokeJoin.Round)
                )
                if (!mini) {
                    drawChevronGlyph(
                        Offset(lx, ly),
                        map.laneDir[p],
                        r * 0.7f,
                        palette.players[p].main,
                        (r * 0.12f).coerceAtLeast(1.4f)
                    )
                }
            }
        }
    }
}

private fun buildTilePath(
    shape: TileShape,
    x: Float,
    y: Float,
    r: Float,
    angRad: Float
): Path {
    val path = Path()
    when (shape) {
        TileShape.CIRCLE -> {
            path.addOval(Rect(x - r, y - r, x + r, y + r))
        }
        TileShape.SQUARE -> {
            val half = r * 0.95f
            val corner = r * 0.16f
            val rr = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = -half,
                        top = -half,
                        right = half,
                        bottom = half,
                        cornerRadius = CornerRadius(corner, corner)
                    )
                )
            }
            val matrix = androidx.compose.ui.graphics.Matrix()
            matrix.translate(x, y)
            matrix.rotateZ(angRad * 180f / PI.toFloat())
            rr.transform(matrix)
            path.addPath(rr)
        }
        TileShape.DIAMOND -> {
            val k = r * 1.22f
            path.moveTo(x, y - k)
            path.lineTo(x + k, y)
            path.lineTo(x, y + k)
            path.lineTo(x - k, y)
            path.close()
        }
        TileShape.TRIANGLE -> {
            val k = r * 1.25f
            for (i in 0 until 3) {
                val a = angRad + (i * TAU) / 3f
                val px = x + cos(a) * k
                val py = y + sin(a) * k
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        }
    }
    return path
}

private fun DrawScope.drawStarGlyph(center: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2f + (i * PI.toFloat()) / 5f
        val k = if (i % 2 == 1) r * 0.45f else r
        val px = center.x + cos(a) * k
        val py = center.y + sin(a) * k
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path = path, color = color)
}

private fun DrawScope.drawBoltGlyph(center: Offset, r: Float, color: Color) {
    val pts = listOf(
        0.2f to -1f,
        -0.6f to 0.15f,
        -0.05f to 0.15f,
        -0.25f to 1f,
        0.6f to -0.25f,
        0.05f to -0.25f
    )
    val path = Path()
    pts.forEachIndexed { idx, (px, py) ->
        val vx = center.x + px * r
        val vy = center.y + py * r
        if (idx == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
    }
    path.close()
    drawPath(path = path, color = color)
}

private fun DrawScope.drawHouseGlyph(center: Offset, r: Float, color: Color) {
    val pts = listOf(
        0f to -1f,
        1f to -0.12f,
        0.68f to -0.12f,
        0.68f to 0.88f,
        -0.68f to 0.88f,
        -0.68f to -0.12f,
        -1f to -0.12f
    )
    val path = Path()
    pts.forEachIndexed { idx, (px, py) ->
        val vx = center.x + px * r
        val vy = center.y + py * r
        if (idx == 0) path.moveTo(vx, vy) else path.lineTo(vx, vy)
    }
    path.close()
    drawPath(path = path, color = color)
    drawRect(
        color = Color.White,
        topLeft = Offset(center.x - r * 0.2f, center.y + r * 0.2f),
        size = Size(r * 0.4f, r * 0.68f)
    )
}

private fun DrawScope.drawChevronGlyph(
    center: Offset,
    angRad: Float,
    k: Float,
    color: Color,
    strokeWidth: Float
) {
    rotate(degrees = angRad * 180f / PI.toFloat(), pivot = center) {
        val path = Path().apply {
            moveTo(center.x - k * 0.35f, center.y - k * 0.55f)
            lineTo(center.x + k * 0.35f, center.y)
            lineTo(center.x - k * 0.35f, center.y + k * 0.55f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

fun DrawScope.drawToken(
    center: Offset,
    radius: Float,
    colorSet: PlayerColorSet,
    scale: Float = 1f,
    hopPx: Float = 0f,
    glow: Boolean = false,
    selected: Boolean = false,
    nowMs: Long = 0L,
    reducedMotion: Boolean = false
) {
    val r = radius * scale
    val shadowAlpha = (0.22f - hopPx * 0.004f).coerceIn(0.06f, 0.22f)
    drawOval(
        color = Color(0xFF262B42).copy(alpha = shadowAlpha),
        topLeft = Offset(center.x - r * 0.9f, center.y + r * 0.28f),
        size = Size(r * 1.8f, r * 0.68f)
    )

    val cy = center.y - hopPx - r * 0.12f

    if (selected) {
        val pu = if (reducedMotion) 0.7f else (sin(nowMs / 150.0).toFloat() + 1f) / 2f
        drawCircle(
            color = colorSet.deep.copy(alpha = 0.28f),
            radius = r + 7f + pu * 3f,
            center = Offset(center.x, cy)
        )
        drawCircle(
            color = Color.White,
            radius = r + 5f,
            center = Offset(center.x, cy),
            style = Stroke(width = 3.5f)
        )
        drawCircle(
            color = colorSet.deep,
            radius = r + 7f + pu * 2.5f,
            center = Offset(center.x, cy),
            style = Stroke(width = 2.8f)
        )
    } else if (glow) {
        val pu = if (reducedMotion) 0.5f else (sin(nowMs / 170.0).toFloat() + 1f) / 2f
        drawCircle(
            color = colorSet.main.copy(alpha = 0.55f + pu * 0.35f),
            radius = r + 4f + pu * 3f,
            center = Offset(center.x, cy),
            style = Stroke(width = 2.6f)
        )
    }

    // 3D base
    drawOval(
        color = colorSet.deep,
        topLeft = Offset(center.x - r * 0.92f, cy + r * 0.1f),
        size = Size(r * 1.84f, r * 0.8f)
    )
    drawOval(
        color = lerp(colorSet.deep, Color.White, 0.25f),
        topLeft = Offset(center.x - r * 0.92f, cy + r * 0.06f),
        size = Size(r * 1.84f, r * 0.72f)
    )

    // Body radial sphere
    val bodyCenter = Offset(center.x, cy)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.White,
                0.28f to colorSet.light,
                0.62f to colorSet.main,
                1f to colorSet.deep
            ),
            center = Offset(center.x - r * 0.3f, cy - r * 0.35f),
            radius = r * 1.15f
        ),
        radius = r * 0.82f,
        center = bodyCenter
    )
    drawCircle(
        color = colorSet.deep.copy(alpha = 0.55f),
        radius = r * 0.82f,
        center = bodyCenter,
        style = Stroke(width = (r * 0.07f).coerceAtLeast(1f))
    )

    // Specular highlight
    rotate(degrees = -34f, pivot = Offset(center.x - r * 0.28f, cy - r * 0.42f)) {
        drawOval(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(center.x - r * 0.50f, cy - r * 0.54f),
            size = Size(r * 0.44f, r * 0.24f)
        )
    }
}

private fun DrawScope.drawParticles(
    particles: List<ParticleFx>,
    metrics: BoardLayoutMetrics,
    palette: DuoPalette
) {
    for (f in particles) {
        val k = (f.age / f.maxAge).coerceIn(0f, 1f)
        val p = metrics.toScreen(f.boardPos)
        val c = when {
            f.isWhite -> Color.White
            f.playerColorIdx == -1 -> palette.gold
            f.playerColorIdx == -2 -> palette.bolt
            f.playerColorIdx in 0..1 -> palette.players[f.playerColorIdx].main
            else -> palette.gold
        }
        if (f.isRing) {
            drawCircle(
                color = c.copy(alpha = (1f - k) * 0.65f),
                radius = f.maxRadius * metrics.scale * (0.3f + k * 0.9f),
                center = p,
                style = Stroke(width = 2.2f * (1f - k) + 0.6f)
            )
        } else {
            drawCircle(
                color = c.copy(alpha = 1f - k),
                radius = (f.size * (1f - k * 0.5f)).coerceAtLeast(1f),
                center = p
            )
        }
    }
}

private val DICE_PIPS = mapOf(
    1 to listOf(4),
    2 to listOf(0, 8),
    3 to listOf(0, 4, 8),
    4 to listOf(0, 2, 6, 8),
    5 to listOf(0, 2, 4, 6, 8),
    6 to listOf(0, 2, 3, 5, 6, 8)
)

private fun DrawScope.drawDice(
    metrics: BoardLayoutMetrics,
    anim: AnimationController,
    canRoll: Boolean,
    nowMs: Long,
    reducedMotion: Boolean,
    palette: DuoPalette
) {
    val ds = metrics.dockSize
    val ready = canRoll && !anim.diceRolling && !anim.diceMoving
    val bob = if (ready && !reducedMotion) sin(nowMs / 280.0).toFloat() * ds * 0.05f else 0f

    val baseCenter = if (anim.diceMoving) {
        val from = metrics.dockCenters[anim.diceFromOwner]
        val to = metrics.dockCenters[anim.diceOwner]
        Offset(
            from.x + (to.x - from.x) * anim.diceMoveFraction,
            from.y + (to.y - from.y) * anim.diceMoveFraction
        )
    } else {
        metrics.dockCenters[anim.diceOwner]
    }

    val x = baseCenter.x
    val y = baseCenter.y + bob
    val hopPx = anim.diceHopNorm * ds * 0.95f

    val shadowScale = 1f - (hopPx / (ds * 4f)).coerceIn(0f, 0.5f)
    val shadowAlpha = (0.20f - hopPx / (ds * 9f)).coerceIn(0.05f, 0.20f)
    drawOval(
        color = Color(0xFF262B42).copy(alpha = shadowAlpha),
        topLeft = Offset(x - ds * 0.46f * shadowScale, y + ds * 0.39f),
        size = Size(ds * 0.92f * shadowScale, ds * 0.26f * shadowScale)
    )

    val diceCenter = Offset(x, y - hopPx)
    translate(left = diceCenter.x, top = diceCenter.y) {
        rotate(degrees = anim.diceRotRad * 180f / PI.toFloat(), pivot = Offset.Zero) {
            scale(scaleX = anim.diceScaleX, scaleY = anim.diceScaleY, pivot = Offset.Zero) {
                val h = ds / 2f
                val corner = ds * 0.24f

                drawRoundRect(
                    color = palette.boardShadow.copy(alpha = 0.35f),
                    topLeft = Offset(-h, -h + ds * 0.08f),
                    size = Size(ds, ds),
                    cornerRadius = CornerRadius(corner, corner)
                )

                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White, Color(0xFFE7EDFA)),
                        start = Offset(-h, -h),
                        end = Offset(h, h)
                    ),
                    topLeft = Offset(-h, -h),
                    size = Size(ds, ds),
                    cornerRadius = CornerRadius(corner, corner)
                )
                drawRoundRect(
                    color = Color(0xFFD3DCF1),
                    topLeft = Offset(-h, -h),
                    size = Size(ds, ds),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 1.4f)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.95f),
                    topLeft = Offset(-h + ds * 0.05f, -h + ds * 0.05f),
                    size = Size(ds * 0.9f, ds * 0.9f),
                    cornerRadius = CornerRadius(ds * 0.20f, ds * 0.20f),
                    style = Stroke(width = ds * 0.03f)
                )

                val pipOffset = ds * 0.25f
                val pips = DICE_PIPS[anim.diceFace] ?: DICE_PIPS[1]!!
                for (i in pips) {
                    val px = ((i % 3) - 1) * pipOffset
                    val py = ((i / 3) - 1) * pipOffset
                    drawCircle(
                        color = if (anim.diceFace == 1) palette.players[anim.diceOwner].deep else Color(0xFF262B42),
                        radius = ds * 0.080f,
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}

@Composable
fun MapPreviewCanvas(
    mapId: MapId,
    modifier: Modifier = Modifier,
    mini: Boolean = false
) {
    val palette = LocalDuoPalette.current
    Canvas(modifier = modifier) {
        if (size.width < 16f || size.height < 16f) return@Canvas
        val portrait = size.height > size.width * 1.05f
        val map = MapManager.build(mapId, portrait)
        val bw = map.bounds.maxX - map.bounds.minX
        val bh = map.bounds.maxY - map.bounds.minY
        val s = min((size.width * 0.90f) / bw, (size.height * 0.90f) / bh)
        val ox = size.width / 2f - ((map.bounds.minX + map.bounds.maxX) / 2f) * s
        val oy = size.height / 2f - ((map.bounds.minY + map.bounds.maxY) / 2f) * s

        drawStaticBoard(
            map = map,
            scale = s,
            ox = ox,
            oy = oy,
            palette = palette,
            mini = mini || size.width < 160f
        )

        val tr = map.tileR * s * 0.86f
        for (p in 0..1) {
            val q = map.pts[map.start[p]]
            drawToken(
                center = Offset(ox + q.x * s, oy + q.y * s),
                radius = tr,
                colorSet = palette.players[p]
            )
        }
    }
}
