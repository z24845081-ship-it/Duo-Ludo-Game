package com.example.engine

import kotlin.math.min

object DiceEngine {

    fun previewWeights(state: GameState, playerIndex: Int): Pair<List<Float>, List<String>> {
        if (state.diceMode == DiceMode.CLASSIC) {
            return List(6) { 1f } to listOf("Classic Dice: uniform 1/6 (16.7%) probability per face.")
        }

        val m = state.map
        val me = state.players[playerIndex]
        val op = state.players[1 - playerIndex]
        val w = FloatArray(7) { 1f }
        val reasons = mutableListOf<String>()

        if (me.tokens.all { it < 0 } && me.misses > 0) {
            w[1] *= 1f + 0.22f * min(me.misses, 6)
            reasons.add("Helping you leave base")
        }

        var need = 0
        for (t in me.tokens) {
            if (t >= 0 && t < m.home && m.home - t <= 6) {
                val n = m.home - t
                if (need == 0 || n < need) need = n
            }
        }
        if (need > 0) {
            if (me.homeMisses >= 3) {
                w[need] *= 1f + 0.15f * min(me.homeMisses - 2, 4)
                reasons.add("Home stretch nudge")
            } else if (me.homeMisses <= 1) {
                w[need] *= 0.9f
            }
        }

        fun progressOf(pl: PlayerState): Float {
            var sum = 0f
            for (t in pl.tokens) {
                sum += if (t < 0) 0f else (t + 1).toFloat()
            }
            return sum / (state.n * m.len).toFloat()
        }

        val s = ((progressOf(op) - progressOf(me)) * 5f).coerceIn(-1f, 1f)
        for (k in 1..6) {
            w[k] *= if (k >= 4) 1f + 0.3f * s else 1f - 0.15f * s
        }
        if (s > 0.35f) reasons.add("Catch-up boost")
        else if (s < -0.35f) reasons.add("Leader balance")

        val r = me.rolls
        if (r.size >= 2 && r[r.size - 1] == r[r.size - 2]) {
            w[r[r.size - 1]] *= 0.5f
            reasons.add("Streak breaker")
        }

        val clamped = (1..6).map { w[it].coerceIn(0.5f, 1.7f) }
        return clamped to reasons
    }

    fun rollDice(state: GameState, playerIndex: Int): DiceRollResult {
        val m = state.map
        val me = state.players[playerIndex]

        val rawWeights: List<Float>
        val reasons: List<String>
        val value: Int

        if (state.diceMode == DiceMode.CLASSIC) {
            rawWeights = List(6) { 1f }
            reasons = listOf("Classic uniform roll — equal 16.7% chance for 1–6.")
            value = 1 + state.rng.nextInt(6)
        } else {
            val (baseWeights, baseReasons) = previewWeights(state, playerIndex)
            val jittered = FloatArray(6) { idx ->
                val v = baseWeights[idx] * (0.9f + 0.2f * state.rng.nextFloat())
                v.coerceIn(0.5f, 1.7f)
            }
            val total = jittered.sum()
            var x = state.rng.nextFloat() * total
            var chosen = 6
            for (k in 0 until 6) {
                x -= jittered[k]
                if (x <= 0f) {
                    chosen = k + 1
                    break
                }
            }
            rawWeights = jittered.toList()
            reasons = baseReasons
            value = chosen
        }

        val sumW = rawWeights.sum().coerceAtLeast(1e-6f)
        val normalized = rawWeights.map { (it / sumW) * 100f }

        me.rolls.add(value)
        if (me.rolls.size > 6) me.rolls.removeAt(0)
        me.stats = me.stats.copy(
            rolls = me.stats.rolls + 1,
            sixes = me.stats.sixes + if (value == 6) 1 else 0
        )

        val legal = GameEngine.legalMoves(state, playerIndex, value)
        me.misses = if (legal.isEmpty()) me.misses + 1 else 0

        var need = 0
        for (t in me.tokens) {
            if (t >= 0 && t < m.home && m.home - t <= 6) {
                val n = m.home - t
                if (need == 0 || n < need) need = n
            }
        }
        if (need > 0) {
            val canFinishNow = legal.any { i -> me.tokens[i] >= 0 && me.tokens[i] + value == m.home }
            if (canFinishNow) me.homeMisses = 0 else me.homeMisses++
        }

        return DiceRollResult(
            value = value,
            weights = rawWeights,
            normalizedProbabilities = normalized,
            reasons = reasons,
            legalTokens = legal,
            mode = state.diceMode
        )
    }
}
