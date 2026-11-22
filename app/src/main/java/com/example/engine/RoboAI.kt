package com.example.engine

object RoboAI {

    fun chooseMove(
        state: GameState,
        playerIndex: Int,
        rollValue: Int,
        legalTokens: List<Int>
    ): Int {
        require(legalTokens.isNotEmpty()) { "RoboAI called with empty legalTokens" }
        if (legalTokens.size == 1) return legalTokens.first()

        // Never mutate the real game state during evaluation/simulation
        val simSnapshot = state.deepCopy()

        return when (state.level) {
            RoboLevel.EASY -> chooseChill(simSnapshot, playerIndex, rollValue, legalTokens)
            RoboLevel.NORMAL -> chooseSmart(simSnapshot, playerIndex, rollValue, legalTokens)
            RoboLevel.HARD -> chooseGenius(simSnapshot, playerIndex, rollValue, legalTokens)
        }
    }

    /**
     * Chill: Casual opponent — mostly random legal moves with occasional obvious plays.
     */
    private fun chooseChill(
        state: GameState,
        playerIndex: Int,
        rollValue: Int,
        legalTokens: List<Int>
    ): Int {
        if (state.rng.nextFloat() < 0.80f) {
            return legalTokens[state.rng.nextInt(legalTokens.size)]
        }
        // 20% chance to prefer finishing or entering if available
        val m = state.map
        val finishOrEnter = legalTokens.firstOrNull { ti ->
            val t = state.players[playerIndex].tokens[ti]
            t < 0 || t + rollValue == m.home
        }
        return finishOrEnter ?: legalTokens[state.rng.nextInt(legalTokens.size)]
    }

    /**
     * Smart: Evaluates captures, danger avoidance, home progress, token protection, and boosts.
     */
    private fun chooseSmart(
        state: GameState,
        playerIndex: Int,
        rollValue: Int,
        legalTokens: List<Int>
    ): Int {
        val candidates = deduplicateByProgress(state, playerIndex, legalTokens)
        var best = candidates.first()
        var bestScore = Float.NEGATIVE_INFINITY
        for (ti in candidates) {
            val score = scoreMoveStrategic(state, playerIndex, ti, rollValue) +
                state.rng.nextFloat() * 4f
            if (score > bestScore) {
                bestScore = score
                best = ti
            }
        }
        return best
    }

    /**
     * Genius: Predictive 2-ply lookahead simulation on cloned GameState snapshots.
     * Considers final destination after boost, captures, safe tiles, stacking protection,
     * extra turns, and likely opponent responses across rolls 1..6.
     */
    private fun chooseGenius(
        state: GameState,
        playerIndex: Int,
        rollValue: Int,
        legalTokens: List<Int>
    ): Int {
        val candidates = deduplicateByProgress(state, playerIndex, legalTokens)
        if (candidates.size == 1) return candidates.first()

        var bestToken = candidates.first()
        var bestValue = Float.NEGATIVE_INFINITY
        val oppIndex = 1 - playerIndex

        for (ti in candidates) {
            val afterMyMove = state.deepCopy()
            val plan = GameEngine.doMove(afterMyMove, playerIndex, ti, rollValue)

            if (GameEngine.isWin(afterMyMove, playerIndex)) {
                return ti
            }

            val immediateEval = evaluateBoard(afterMyMove, playerIndex)
            val tacticalBonus = scoreMoveStrategic(state, playerIndex, ti, rollValue) * 0.45f
            val extraTurn = GameEngine.isExtraTurn(rollValue, plan)

            val value = if (extraTurn) {
                // Extra turn retains initiative: evaluate expected best follow-up lightly
                val followUp = expectedFollowUpValue(afterMyMove, playerIndex)
                immediateEval + tacticalBonus + 18f + followUp * 0.25f
            } else {
                // Simulate opponent's expected best response across rolls 1..6
                var oppWeightedEval = 0f
                for (oppRoll in 1..6) {
                    val oppLegal = GameEngine.legalMoves(afterMyMove, oppIndex, oppRoll)
                    if (oppLegal.isEmpty()) {
                        oppWeightedEval += immediateEval
                    } else {
                        val oppCandidates = deduplicateByProgress(afterMyMove, oppIndex, oppLegal)
                        var worstForMe = Float.POSITIVE_INFINITY
                        for (oppTi in oppCandidates) {
                            val afterOpp = afterMyMove.deepCopy()
                            GameEngine.doMove(afterOpp, oppIndex, oppTi, oppRoll)
                            val postOppEval = if (GameEngine.isWin(afterOpp, oppIndex)) {
                                -100_000f
                            } else {
                                evaluateBoard(afterOpp, playerIndex)
                            }
                            if (postOppEval < worstForMe) {
                                worstForMe = postOppEval
                            }
                        }
                        oppWeightedEval += worstForMe
                    }
                }
                val expectedPostOpp = oppWeightedEval / 6f
                expectedPostOpp * 0.7f + immediateEval * 0.3f + tacticalBonus * 0.35f
            }

            if (value > bestValue) {
                bestValue = value
                bestToken = ti
            }
        }
        return bestToken
    }

    private fun expectedFollowUpValue(state: GameState, playerIndex: Int): Float {
        var total = 0f
        for (r in 1..6) {
            val legal = GameEngine.legalMoves(state, playerIndex, r)
            if (legal.isEmpty()) continue
            var best = Float.NEGATIVE_INFINITY
            for (ti in deduplicateByProgress(state, playerIndex, legal)) {
                val sc = scoreMoveStrategic(state, playerIndex, ti, r)
                if (sc > best) best = sc
            }
            if (best > Float.NEGATIVE_INFINITY) total += best
        }
        return total / 6f
    }

    private fun deduplicateByProgress(
        state: GameState,
        playerIndex: Int,
        legalTokens: List<Int>
    ): List<Int> {
        val seen = HashSet<Int>()
        val out = mutableListOf<Int>()
        for (ti in legalTokens) {
            val prog = state.players[playerIndex].tokens[ti]
            if (seen.add(prog)) out.add(ti)
        }
        return out
    }

    fun dangerAt(state: GameState, playerIndex: Int, prog: Int): Int {
        val m = state.map
        if (prog < 0 || prog >= m.rl) return 0
        val r = GameEngine.ringIdx(m, playerIndex, prog)
        if (m.safe.contains(r)) return 0
        // If we already have 2+ tokens here, we are protected!
        if (GameEngine.ownOnTileCount(state, playerIndex, prog) >= 2) return 0

        val tid = m.tileId[r]
        val o = 1 - playerIndex
        var d = 0
        for (t in state.players[o].tokens) {
            if (t < 0 || t >= m.rl) continue
            val ro = GameEngine.ringIdx(m, o, t)
            for (k in 1..6) {
                if (t + k <= m.rl - 1 && m.tileId[(ro + k) % m.n] == tid) {
                    d++
                    break
                }
            }
        }
        return d
    }

    fun scoreMoveStrategic(
        state: GameState,
        playerIndex: Int,
        tokenIndex: Int,
        rollValue: Int
    ): Float {
        val m = state.map
        val t = state.players[playerIndex].tokens[tokenIndex]
        val initialQ = if (t < 0) 0 else t + rollValue
        val willBoost = initialQ < m.rl && m.boost.contains(GameEngine.ringIdx(m, playerIndex, initialQ)) && initialQ + 3 <= m.rl - 1
        val finalQ = if (willBoost) initialQ + 3 else initialQ

        var s = 0f

        // Finishing and home lane advancement
        if (finalQ == m.home) s += 120f
        else if (finalQ >= m.rl) s += 32f + (finalQ - m.rl) * 3.5f

        // Leaving base
        if (t < 0) s += 38f

        // Evaluate initial landing capture if boosted
        if (initialQ < m.rl) {
            val rInit = GameEngine.ringIdx(m, playerIndex, initialQ)
            if (!m.safe.contains(rInit)) {
                val cInit = GameEngine.oppOnTile(state, playerIndex, initialQ)
                if (cInit.size == 1) {
                    s += 62f + state.players[1 - playerIndex].tokens[cInit[0]] * 0.7f
                }
            }
        }

        // Evaluate final destination
        if (finalQ < m.rl) {
            val rFinal = GameEngine.ringIdx(m, playerIndex, finalQ)
            if (m.safe.contains(rFinal)) {
                s += 24f
            } else {
                val cFinal = GameEngine.oppOnTile(state, playerIndex, finalQ)
                if (cFinal.size == 1) {
                    s += 62f + state.players[1 - playerIndex].tokens[cFinal[0]] * 0.7f
                }
                // Check if landing creates a protective pair of own tokens
                if (GameEngine.ownOnTileCount(state, playerIndex, finalQ) >= 1) {
                    s += 20f
                }
            }
            if (willBoost) s += 18f
        }

        // Breaking an existing protective pair when leaving danger
        val wasProtectedPair = t >= 0 && t < m.rl && GameEngine.ownOnTileCount(state, playerIndex, t) == 2
        if (wasProtectedPair) s -= 10f

        // Progress value
        s += finalQ * 0.4f

        // Danger delta (weighted by progress so advanced tokens care more about safety)
        val dangerBefore = if (t >= 0) dangerAt(state, playerIndex, t) else 0
        val dangerAfter = dangerAtAfterMove(state, playerIndex, tokenIndex, finalQ)
        val progressWeight = 1f + (finalQ.coerceAtLeast(0).toFloat() / m.len.toFloat())
        s += (dangerBefore - dangerAfter) * 18f * progressWeight

        return s
    }

    private fun dangerAtAfterMove(
        state: GameState,
        playerIndex: Int,
        movingTi: Int,
        targetProg: Int
    ): Int {
        val m = state.map
        if (targetProg < 0 || targetProg >= m.rl) return 0
        val r = GameEngine.ringIdx(m, playerIndex, targetProg)
        if (m.safe.contains(r)) return 0
        val tid = m.tileId[r]
        // Count remaining own tokens already on targetProg
        var ownCount = 1
        state.players[playerIndex].tokens.forEachIndexed { idx, t ->
            if (idx != movingTi && t >= 0 && t < m.rl && m.tileId[GameEngine.ringIdx(m, playerIndex, t)] == tid) {
                ownCount++
            }
        }
        if (ownCount >= 2) return 0

        val o = 1 - playerIndex
        var d = 0
        for (t in state.players[o].tokens) {
            if (t < 0 || t >= m.rl) continue
            val ro = GameEngine.ringIdx(m, o, t)
            for (k in 1..6) {
                if (t + k <= m.rl - 1 && m.tileId[(ro + k) % m.n] == tid) {
                    d++
                    break
                }
            }
        }
        return d
    }

    private fun evaluateBoard(state: GameState, playerIndex: Int): Float {
        val m = state.map
        val me = state.players[playerIndex]
        val opp = state.players[1 - playerIndex]

        var score = 0f
        for (t in me.tokens) {
            if (t == m.home) score += 220f
            else if (t >= m.rl) score += 85f + (t - m.rl) * 8f
            else if (t >= 0) {
                score += 22f + t * 1.4f
                val r = GameEngine.ringIdx(m, playerIndex, t)
                if (m.safe.contains(r) || GameEngine.ownOnTileCount(state, playerIndex, t) >= 2) {
                    score += 16f
                } else {
                    score -= dangerAt(state, playerIndex, t) * (12f + t * 0.25f)
                }
            }
        }

        for (t in opp.tokens) {
            if (t == m.home) score -= 220f
            else if (t >= m.rl) score -= 85f + (t - m.rl) * 8f
            else if (t >= 0) {
                score -= 22f + t * 1.4f
                val r = GameEngine.ringIdx(m, 1 - playerIndex, t)
                if (m.safe.contains(r) || GameEngine.ownOnTileCount(state, 1 - playerIndex, t) >= 2) {
                    score -= 14f
                }
            }
        }

        return score
    }
}
