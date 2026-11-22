package com.example.engine

object GameEngine {

    fun ringIdx(m: MapGeometry, playerIndex: Int, prog: Int): Int =
        (m.start[playerIndex] + prog) % m.n

    fun posOf(m: MapGeometry, playerIndex: Int, prog: Int): Vec2 =
        if (prog < m.rl) m.pts[ringIdx(m, playerIndex, prog)]
        else m.lanes[playerIndex][prog - m.rl]

    fun tileKey(m: MapGeometry, playerIndex: Int, prog: Int): String =
        if (prog < m.rl) "r${m.tileId[ringIdx(m, playerIndex, prog)]}"
        else "l${playerIndex}_${prog - m.rl}"

    fun create(
        config: MatchConfig,
        portrait: Boolean = false,
        seed: Long = (System.nanoTime() xor (Math.random() * 1e9).toLong())
    ): GameState {
        val n0 = config.names.getOrElse(0) { "Player 1" }.ifBlank { "Player 1" }.take(14)
        val n1 = if (config.mode == GameMode.ROBO) "Robo"
        else config.names.getOrElse(1) { "Player 2" }.ifBlank { "Player 2" }.take(14)

        val tokenCount = config.tokens.coerceIn(1, 6)
        val p0 = PlayerState(name = n0, bot = false, tokens = MutableList(tokenCount) { -1 })
        val p1 = PlayerState(name = n1, bot = config.mode == GameMode.ROBO, tokens = MutableList(tokenCount) { -1 })

        return GameState(
            map = MapManager.build(config.map, portrait),
            portrait = portrait,
            n = tokenCount,
            level = config.level,
            diceMode = config.diceMode,
            turn = 0,
            moves = 0,
            rng = Mulberry32Rng(seed),
            players = listOf(p0, p1)
        )
    }

    /**
     * Returns ALL legal token indices for [playerIndex] given roll [rollValue].
     * Unlike the legacy JS engine, this never drops stacked tokens at the same progress,
     * guaranteeing every highlighted token on the board is selectable and accepted.
     */
    fun legalMoves(state: GameState, playerIndex: Int, rollValue: Int): List<Int> {
        val m = state.map
        val out = mutableListOf<Int>()
        state.players[playerIndex].tokens.forEachIndexed { idx, t ->
            if (t == m.home) return@forEachIndexed
            if (t < 0) {
                if (rollValue != 1) return@forEachIndexed
            } else if (t + rollValue > m.home) {
                return@forEachIndexed
            }
            out.add(idx)
        }
        return out
    }

    /**
     * Computes step sequence & final destination without mutating [state],
     * used for interactive movement preview when a token is selected.
     */
    fun previewMove(state: GameState, playerIndex: Int, tokenIndex: Int, rollValue: Int): MovePlan? {
        if (!legalMoves(state, playerIndex, rollValue).contains(tokenIndex)) return null
        val m = state.map
        val from = state.players[playerIndex].tokens[tokenIndex]
        var q = if (from < 0) 0 else from + rollValue
        val seq = mutableListOf<Int>()
        if (from < 0) {
            seq.add(0)
        } else {
            for (k in (from + 1)..q) seq.add(k)
        }
        var boosted = false
        if (q < m.rl && m.boost.contains(ringIdx(m, playerIndex, q)) && q + 3 <= m.rl - 1) {
            boosted = true
            for (k in (q + 1)..(q + 3)) seq.add(k)
            q += 3
        }
        return MovePlan(
            playerIndex = playerIndex,
            tokenIndex = tokenIndex,
            from = from,
            to = q,
            seq = seq,
            captures = emptyList(),
            boosted = boosted,
            entered = from < 0,
            reachedHome = q == m.home,
            captured = false
        )
    }

    fun oppOnTile(state: GameState, playerIndex: Int, prog: Int): List<Int> {
        val m = state.map
        if (prog < 0 || prog >= m.rl) return emptyList()
        val o = 1 - playerIndex
        val tid = m.tileId[ringIdx(m, playerIndex, prog)]
        val res = mutableListOf<Int>()
        state.players[o].tokens.forEachIndexed { i, t ->
            if (t >= 0 && t < m.rl && m.tileId[ringIdx(m, o, t)] == tid) {
                res.add(i)
            }
        }
        return res
    }

    fun ownOnTileCount(state: GameState, playerIndex: Int, prog: Int): Int {
        val m = state.map
        if (prog < 0 || prog >= m.rl) return 0
        val tid = m.tileId[ringIdx(m, playerIndex, prog)]
        return state.players[playerIndex].tokens.count { t ->
            t >= 0 && t < m.rl && m.tileId[ringIdx(m, playerIndex, t)] == tid
        }
    }

    private fun resolveCapture(
        state: GameState,
        playerIndex: Int,
        q: Int,
        captures: MutableList<CaptureEvent>,
        seqIdx: Int
    ) {
        val m = state.map
        if (q >= m.rl) return
        if (m.safe.contains(ringIdx(m, playerIndex, q))) return
        val list = oppOnTile(state, playerIndex, q)
        if (list.size != 1) return
        val o = 1 - playerIndex
        val oppTi = list[0]
        val oppProg = state.players[o].tokens[oppTi]
        captures.add(CaptureEvent(playerIndex = o, tokenIndex = oppTi, fromProg = oppProg, seqIndex = seqIdx))
        state.players[o].tokens[oppTi] = -1
        state.players[playerIndex].stats = state.players[playerIndex].stats.copy(
            captures = state.players[playerIndex].stats.captures + 1
        )
        state.players[o].stats = state.players[o].stats.copy(
            lost = state.players[o].stats.lost + 1
        )
    }

    fun doMove(state: GameState, playerIndex: Int, tokenIndex: Int, rollValue: Int): MovePlan {
        require(legalMoves(state, playerIndex, rollValue).contains(tokenIndex)) {
            "Illegal move: player=$playerIndex token=$tokenIndex roll=$rollValue"
        }
        val m = state.map
        val pl = state.players[playerIndex]
        val from = pl.tokens[tokenIndex]
        var q = if (from < 0) 0 else from + rollValue
        val seq = mutableListOf<Int>()
        if (from < 0) {
            seq.add(0)
        } else {
            for (k in (from + 1)..q) seq.add(k)
        }
        pl.tokens[tokenIndex] = q

        val captures = mutableListOf<CaptureEvent>()
        resolveCapture(state, playerIndex, q, captures, seq.size - 1)

        var boosted = false
        if (q < m.rl && m.boost.contains(ringIdx(m, playerIndex, q)) && q + 3 <= m.rl - 1) {
            boosted = true
            for (k in (q + 1)..(q + 3)) seq.add(k)
            q += 3
            pl.tokens[tokenIndex] = q
            resolveCapture(state, playerIndex, q, captures, seq.size - 1)
        }
        if (q == m.home) pl.homeMisses = 0
        pl.stats = pl.stats.copy(moves = pl.stats.moves + 1)
        state.moves++

        return MovePlan(
            playerIndex = playerIndex,
            tokenIndex = tokenIndex,
            from = from,
            to = q,
            seq = seq,
            captures = captures,
            boosted = boosted,
            entered = from < 0,
            reachedHome = q == m.home,
            captured = captures.isNotEmpty()
        )
    }

    fun isWin(state: GameState, playerIndex: Int): Boolean =
        state.players[playerIndex].tokens.all { it == state.map.home }

    fun isExtraTurn(rollValue: Int, plan: MovePlan): Boolean =
        rollValue == 6 || plan.captured || plan.reachedHome
}
