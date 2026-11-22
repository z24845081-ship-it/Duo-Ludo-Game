package com.example.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.engine.GameEngine
import com.example.engine.GameState
import com.example.engine.MapGeometry
import com.example.engine.MovePlan
import com.example.engine.Vec2
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

data class AnimatedTokenOverride(
    val boardPos: Vec2,
    val fromBaseSlot: Boolean = false,
    val toBaseSlot: Boolean = false,
    val slotPlayer: Int = 0,
    val slotIndex: Int = 0,
    val hopFraction: Float = 0f,
    val progressFrac: Float = 0f,
    val scale: Float = 1f
)

data class ParticleFx(
    val id: Long,
    val isRing: Boolean,
    val boardPos: Vec2,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val age: Float = 0f,
    val maxAge: Float = 0.5f,
    val size: Float = 3f,
    val maxRadius: Float = 24f,
    val playerColorIdx: Int = -1, // -1 gold, -2 bolt, 0/1 player, -3 white
    val isWhite: Boolean = false
)

/**
 * Pause-aware, generation-safe animation controller.
 * All coordinates are stored in MapGeometry board units (or normalized lerp markers for base slots),
 * making animations resilient to screen rotation & resizing mid-flight!
 */
class AnimationController {
    var diceOwner by mutableIntStateOf(0)
    var diceFace by mutableIntStateOf(1)
    var diceRotRad by mutableFloatStateOf(0f)
    var diceHopNorm by mutableFloatStateOf(0f)
    var diceScaleX by mutableFloatStateOf(1f)
    var diceScaleY by mutableFloatStateOf(1f)
    var diceMoving by mutableStateOf(false)
    var diceMoveFraction by mutableFloatStateOf(0f)
    var diceFromOwner by mutableIntStateOf(0)
    var diceRolling by mutableStateOf(false)

    val tokenOverrides = mutableStateMapOf<Int, AnimatedTokenOverride>()
    var particles by mutableStateOf<List<ParticleFx>>(emptyList())
        private set

    private var fxIdCounter = 1L

    @Volatile
    private var pausedGate: CompletableDeferred<Unit>? = null

    @Volatile
    var isPaused: Boolean = false
        private set

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) {
            if (pausedGate == null || pausedGate?.isCompleted == true) {
                pausedGate = CompletableDeferred()
            }
        } else {
            pausedGate?.complete(Unit)
            pausedGate = null
        }
    }

    suspend fun awaitIfPaused() {
        val gate = pausedGate
        if (isPaused && gate != null && !gate.isCompleted) {
            gate.await()
        }
    }

    fun reset(owner: Int) {
        setPaused(false)
        diceOwner = owner
        diceFromOwner = owner
        diceFace = 1
        diceRotRad = 0f
        diceHopNorm = 0f
        diceScaleX = 1f
        diceScaleY = 1f
        diceMoving = false
        diceMoveFraction = 0f
        diceRolling = false
        tokenOverrides.clear()
        particles = emptyList()
    }

    fun tickFx(dtSec: Float) {
        if (isPaused || particles.isEmpty()) return
        val updated = ArrayList<ParticleFx>(particles.size)
        for (p in particles) {
            val nextAge = p.age + dtSec
            if (nextAge < p.maxAge) {
                if (p.isRing) {
                    updated.add(p.copy(age = nextAge))
                } else {
                    val nx = p.boardPos.x + p.vx * dtSec
                    val ny = p.boardPos.y + p.vy * dtSec
                    val nvy = p.vy + 520f * dtSec
                    updated.add(p.copy(age = nextAge, boardPos = Vec2(nx, ny), vy = nvy))
                }
            }
        }
        particles = updated
    }

    fun burst(pos: Vec2, colorIdx: Int, count: Int, speed: Float) {
        val list = particles.toMutableList()
        for (i in 0 until count) {
            val a = Random.nextFloat() * (2f * PI.toFloat())
            val sp = speed * (0.35f + Random.nextFloat() * 0.8f)
            list.add(
                ParticleFx(
                    id = fxIdCounter++,
                    isRing = false,
                    boardPos = pos,
                    vx = cos(a) * sp,
                    vy = sin(a) * sp - speed * 0.25f,
                    age = 0f,
                    maxAge = 0.45f + Random.nextFloat() * 0.4f,
                    size = 2.5f + Random.nextFloat() * 3.5f,
                    playerColorIdx = colorIdx,
                    isWhite = colorIdx == -3
                )
            )
        }
        particles = list
    }

    fun ripple(pos: Vec2, colorIdx: Int, maxRadius: Float) {
        particles = particles + ParticleFx(
            id = fxIdCounter++,
            isRing = true,
            boardPos = pos,
            age = 0f,
            maxAge = 0.48f,
            maxRadius = maxRadius,
            playerColorIdx = colorIdx,
            isWhite = colorIdx == -3
        )
    }

    suspend fun pauseAwareDelay(ms: Long) {
        var remaining = ms
        val step = 16L
        while (remaining > 0) {
            awaitIfPaused()
            val chunk = minOf(step, remaining)
            delay(chunk)
            remaining -= chunk
        }
    }

    suspend fun animateMoveDiceTo(
        targetOwner: Int,
        instant: Boolean,
        speedMultiplier: Float,
        reducedMotion: Boolean
    ) {
        if (instant || reducedMotion) {
            diceFromOwner = targetOwner
            diceOwner = targetOwner
            diceMoving = false
            diceMoveFraction = 0f
            diceHopNorm = 0f
            return
        }
        diceFromOwner = diceOwner
        diceOwner = targetOwner
        diceMoving = true
        val dur = (520f * speedMultiplier).toLong().coerceAtLeast(180L)
        var elapsed = 0L
        val tick = 16L
        while (elapsed < dur) {
            awaitIfPaused()
            val k = (elapsed.toFloat() / dur).coerceIn(0f, 1f)
            val eased = if (k < 0.5f) 2f * k * k else 1f - (-2f * k + 2f).pow(2) / 2f
            diceMoveFraction = eased
            diceHopNorm = sin(PI.toFloat() * k) * 1.05f
            delay(tick)
            elapsed += tick
        }
        diceMoving = false
        diceMoveFraction = 0f
        diceHopNorm = 0f
        diceFromOwner = targetOwner
    }

    suspend fun animateRollDice(
        targetFace: Int,
        speedMultiplier: Float,
        reducedMotion: Boolean
    ) {
        if (reducedMotion) {
            diceFace = targetFace
            diceRotRad = 0f
            diceHopNorm = 0f
            diceScaleX = 1f
            diceScaleY = 1f
            diceRolling = false
            return
        }

        diceRolling = true
        val dur = (860f * speedMultiplier).toLong().coerceAtLeast(260L)
        val startRot = diceRotRad
        val dir = if (Random.nextBoolean()) 1f else -1f
        val spin = dir * (2.4f + Random.nextFloat() * 1.4f) * (2f * PI.toFloat())
        var elapsed = 0L
        var lastFaceSwitch = 0L
        val tick = 16L

        while (elapsed < dur) {
            awaitIfPaused()
            val k = (elapsed.toFloat() / dur).coerceIn(0f, 1f)
            val eased = 1f - (1f - k).pow(3)
            diceRotRad = startRot + spin * eased
            diceHopNorm = kotlin.math.abs(sin(k * PI.toFloat() * 2.5f)) * (1f - k) * 0.95f
            diceScaleX = 1f + 0.10f * sin(k * PI.toFloat() * 6f) * (1f - k)
            diceScaleY = 1f - 0.10f * sin(k * PI.toFloat() * 6f) * (1f - k)
            if (elapsed - lastFaceSwitch > 45 + (k * 120).toLong()) {
                diceFace = 1 + Random.nextInt(6)
                lastFaceSwitch = elapsed
            }
            delay(tick)
            elapsed += tick
        }

        diceFace = targetFace
        diceRotRad = round(diceRotRad / (PI.toFloat() / 2f)) * (PI.toFloat() / 2f)
        diceHopNorm = 0f
        diceScaleX = 1.12f
        diceScaleY = 0.88f
        pauseAwareDelay(65L)
        diceScaleX = 0.97f
        diceScaleY = 1.03f
        pauseAwareDelay(65L)
        diceScaleX = 1f
        diceScaleY = 1f
        diceRolling = false
    }

    suspend fun animateTokenMove(
        map: MapGeometry,
        plan: MovePlan,
        speedMultiplier: Float,
        reducedMotion: Boolean,
        onStep: (Int) -> Unit,
        onBoostStart: () -> Unit,
        onCapture: (Int) -> Unit,
        onHome: () -> Unit
    ) {
        val key = plan.playerIndex * 10 + plan.tokenIndex
        if (reducedMotion) {
            if (plan.boosted) onBoostStart()
            if (plan.captures.isNotEmpty()) onCapture(plan.captures.first().playerIndex)
            if (plan.reachedHome) onHome()
            pauseAwareDelay(140L)
            return
        }

        val startPos = if (plan.from < 0) {
            GameEngine.posOf(map, plan.playerIndex, 0)
        } else {
            GameEngine.posOf(map, plan.playerIndex, plan.from)
        }

        tokenOverrides[key] = AnimatedTokenOverride(
            boardPos = startPos,
            fromBaseSlot = plan.from < 0,
            slotPlayer = plan.playerIndex,
            slotIndex = plan.tokenIndex,
            progressFrac = 0f
        )

        var prevPos = startPos
        var stepCounter = 0

        for (i in plan.seq.indices) {
            val isBoostStep = plan.boosted && i >= plan.seq.size - 3
            if (isBoostStep && i == plan.seq.size - 3) {
                onBoostStart()
                burst(prevPos, colorIdx = -2, count = 14, speed = 150f)
            }

            val nextProg = plan.seq[i]
            val targetPos = GameEngine.posOf(map, plan.playerIndex, nextProg)
            val isEnteringFromSlot = plan.from < 0 && i == 0
            val dur = ((if (isBoostStep) 100f else if (isEnteringFromSlot) 230f else 155f) * speedMultiplier)
                .toLong().coerceAtLeast(60L)

            var elapsed = 0L
            val tick = 16L
            while (elapsed < dur) {
                awaitIfPaused()
                val k = (elapsed.toFloat() / dur).coerceIn(0f, 1f)
                val eased = if (k < 0.5f) 2f * k * k else 1f - (-2f * k + 2f).pow(2) / 2f
                val curr = if (isEnteringFromSlot) {
                    targetPos
                } else {
                    Vec2(
                        prevPos.x + (targetPos.x - prevPos.x) * eased,
                        prevPos.y + (targetPos.y - prevPos.y) * eased
                    )
                }
                tokenOverrides[key] = AnimatedTokenOverride(
                    boardPos = curr,
                    fromBaseSlot = isEnteringFromSlot,
                    slotPlayer = plan.playerIndex,
                    slotIndex = plan.tokenIndex,
                    hopFraction = sin(PI.toFloat() * k) * (if (isBoostStep) 0.6f else 1.0f),
                    progressFrac = eased
                )
                delay(tick)
                elapsed += tick
            }

            onStep(stepCounter++)
            ripple(targetPos, colorIdx = plan.playerIndex, maxRadius = map.tileR * 1.7f)
            prevPos = targetPos

            val capsAtStep = plan.captures.filter { it.seqIndex == i }
            if (capsAtStep.isNotEmpty()) {
                onCapture(capsAtStep.first().playerIndex)
                burst(targetPos, colorIdx = capsAtStep.first().playerIndex, count = 24, speed = 220f)
                ripple(targetPos, colorIdx = -3, maxRadius = map.tileR * 3.0f)

                coroutineScope {
                    capsAtStep.map { cap ->
                        launch {
                            val capKey = cap.playerIndex * 10 + cap.tokenIndex
                            val capStart = GameEngine.posOf(map, cap.playerIndex, cap.fromProg)
                            val capDur = (540f * speedMultiplier).toLong().coerceAtLeast(180L)
                            var ce = 0L
                            while (ce < capDur) {
                                awaitIfPaused()
                                val ck = (ce.toFloat() / capDur).coerceIn(0f, 1f)
                                val ceased = if (ck < 0.5f) 2f * ck * ck else 1f - (-2f * ck + 2f).pow(2) / 2f
                                tokenOverrides[capKey] = AnimatedTokenOverride(
                                    boardPos = capStart,
                                    toBaseSlot = true,
                                    slotPlayer = cap.playerIndex,
                                    slotIndex = cap.tokenIndex,
                                    hopFraction = sin(PI.toFloat() * ck) * 2.6f,
                                    progressFrac = ceased
                                )
                                delay(tick)
                                ce += tick
                            }
                            tokenOverrides.remove(capKey)
                        }
                    }.forEach { it.join() }
                }
            }
        }

        if (plan.reachedHome) {
            onHome()
            val homePos = GameEngine.posOf(map, plan.playerIndex, plan.to)
            burst(homePos, colorIdx = -1, count = 28, speed = 220f)
            burst(homePos, colorIdx = plan.playerIndex, count = 18, speed = 160f)
        }
        tokenOverrides.remove(key)
    }
}
