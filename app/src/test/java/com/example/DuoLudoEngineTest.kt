package com.example

import com.example.engine.DiceEngine
import com.example.engine.DiceMode
import com.example.engine.GameEngine
import com.example.engine.GameMode
import com.example.engine.MapId
import com.example.engine.MapManager
import com.example.engine.MatchConfig
import com.example.engine.RoboAI
import com.example.engine.RoboLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class DuoLudoEngineTest {

    @Test
    fun `classic dice has uniform 1 over 6 distribution and equal weights`() {
        val state = GameEngine.create(
            MatchConfig(diceMode = DiceMode.CLASSIC, tokens = 4),
            seed = 12345L
        )
        // Put player 1 behind so drama mode would normally boost, classic must ignore it
        state.players[1].tokens[0] = 25
        state.players[0].misses = 5

        val counts = IntArray(6)
        val nRolls = 6000
        for (i in 0 until nRolls) {
            val res = DiceEngine.rollDice(state, 0)
            assertEquals(DiceMode.CLASSIC, res.mode)
            assertEquals(List(6) { 1f }, res.weights)
            res.normalizedProbabilities.forEach { pct ->
                assertEquals(100f / 6f, pct, 0.01f)
            }
            counts[res.value - 1]++
        }

        // Each face should be within reasonable statistical tolerance of 1000 (+/- 12%)
        for (c in counts) {
            assertTrue("Face count $c out of uniform range", c in 880..1120)
        }
    }

    @Test
    fun `drama dice adapts bounded probabilities symmetrically and normalizes to 100 percent`() {
        val state = GameEngine.create(
            MatchConfig(diceMode = DiceMode.DRAMA, tokens = 4),
            seed = 777L
        )
        // Player 0 stuck in base with 4 misses, Player 1 far ahead -> should boost 1 and high rolls
        state.players[0].misses = 4
        state.players[1].tokens[0] = 32
        state.players[1].tokens[1] = 30

        val res = DiceEngine.rollDice(state, 0)
        assertEquals(DiceMode.DRAMA, res.mode)
        assertTrue(res.reasons.isNotEmpty())
        assertTrue(res.weights.all { it in 0.5f..1.7f })
        assertEquals(100f, res.normalizedProbabilities.sum(), 0.05f)
        assertTrue("Expected face 1 weight boosted when stuck in base", res.weights[0] > 1.0f)
    }

    @Test
    fun `entering tokens requires rolling a 1`() {
        val state = GameEngine.create(MatchConfig(tokens = 3), seed = 1L)
        for (roll in 2..6) {
            assertTrue(GameEngine.legalMoves(state, 0, roll).isEmpty())
        }
        val legalOnOne = GameEngine.legalMoves(state, 0, 1)
        assertEquals(listOf(0, 1, 2), legalOnOne)

        val plan = GameEngine.doMove(state, 0, 1, 1)
        assertTrue(plan.entered)
        assertEquals(0, state.players[0].tokens[1])
    }

    @Test
    fun `legalMoves includes all stacked tokens and every highlighted token is accepted`() {
        val state = GameEngine.create(MatchConfig(tokens = 4, map = MapId.ORBIT), seed = 2L)
        // Two tokens stacked on tile prog=5, two in base (-1)
        state.players[0].tokens[0] = 5
        state.players[0].tokens[1] = 5
        state.players[0].tokens[2] = -1
        state.players[0].tokens[3] = -1

        val legalFor4 = GameEngine.legalMoves(state, 0, 4)
        assertEquals(listOf(0, 1), legalFor4)

        // Selecting token 1 (the second stacked token) must succeed cleanly!
        val plan = GameEngine.doMove(state, 0, 1, 4)
        assertEquals(9, plan.to)
        assertEquals(9, state.players[0].tokens[1])
    }

    @Test
    fun `capturing lone opponent on non-safe tile returns opponent to base`() {
        val state = GameEngine.create(MatchConfig(tokens = 2, map = MapId.ORBIT), seed = 3L)
        // Player 0 at prog=1, Player 1 at ring index 4 -> for P1 (start=20), ring index 4 is prog=24
        state.players[0].tokens[0] = 1
        state.players[1].tokens[0] = 24
        assertFalse(state.map.safe.contains(GameEngine.ringIdx(state.map, 0, 4)))

        val plan = GameEngine.doMove(state, 0, 0, 3)
        assertTrue(plan.captured)
        assertEquals(1, plan.captures.size)
        assertEquals(-1, state.players[1].tokens[0])
        assertEquals(1, state.players[0].stats.captures)
        assertEquals(1, state.players[1].stats.lost)
    }

    @Test
    fun `safe tile protects lone opponent from capture`() {
        val state = GameEngine.create(MatchConfig(tokens = 2, map = MapId.ORBIT), seed = 4L)
        // Orbit n=40: safe tiles include 0, 20, 8, 12, 28, 32
        val safeRingIdx = 8
        state.players[0].tokens[0] = 5
        state.players[1].tokens[0] = (safeRingIdx - state.map.start[1] + state.map.n) % state.map.n

        val plan = GameEngine.doMove(state, 0, 0, 3)
        assertFalse(plan.captured)
        assertNotEquals(-1, state.players[1].tokens[0])
    }

    @Test
    fun `multiple opponent tokens on same tile protect each other`() {
        val state = GameEngine.create(MatchConfig(tokens = 2, map = MapId.ORBIT), seed = 5L)
        state.players[0].tokens[0] = 1
        // Place BOTH Player 1 tokens at ring index 4 (non-safe)
        state.players[1].tokens[0] = 24
        state.players[1].tokens[1] = 24

        val plan = GameEngine.doMove(state, 0, 0, 3)
        assertFalse(plan.captured)
        assertEquals(24, state.players[1].tokens[0])
        assertEquals(24, state.players[1].tokens[1])
    }

    @Test
    fun `boost tile advances token 3 extra steps and resolves capture at destination`() {
        val state = GameEngine.create(MatchConfig(tokens = 2, map = MapId.ORBIT), seed = 6L)
        // Orbit boost at index 3 (round(40*0.075)=3). Moving from 1 by 2 lands on 3 -> boosts to 6!
        state.players[0].tokens[0] = 1
        // Put lone opponent at ring index 6 -> P1 prog = (6 - 20 + 40) = 26
        state.players[1].tokens[0] = 26

        val plan = GameEngine.doMove(state, 0, 0, 2)
        assertTrue(plan.boosted)
        assertEquals(6, plan.to)
        assertEquals(listOf(2, 3, 4, 5, 6), plan.seq)
        assertTrue(plan.captured)
        assertEquals(-1, state.players[1].tokens[0])
    }

    @Test
    fun `home lane entry, exact finishing, extra turns, and winning condition`() {
        val state = GameEngine.create(MatchConfig(tokens = 1, map = MapId.ORBIT), seed = 7L)
        val m = state.map
        // Place token 2 steps before home
        state.players[0].tokens[0] = m.home - 2

        // Overshoot rolls (3..6) must be illegal
        for (r in 3..6) {
            assertTrue("Roll $r should not overshoot home", GameEngine.legalMoves(state, 0, r).isEmpty())
        }
        // Preview & exact finish on 2
        val preview = GameEngine.previewMove(state, 0, 0, 2)
        assertTrue(preview != null && preview.reachedHome)
        assertNull(GameEngine.previewMove(state, 0, 0, 4))

        val plan = GameEngine.doMove(state, 0, 0, 2)
        assertTrue(plan.reachedHome)
        assertTrue(GameEngine.isExtraTurn(2, plan))
        assertTrue(GameEngine.isWin(state, 0))
    }

    @Test
    fun `infinity junction shares tileId across crossroads and allows capture`() {
        val m = MapManager.build(MapId.INFINITY, portrait = false)
        assertEquals(setOf(10, 30), m.junction)
        assertEquals(m.tileId[10], m.tileId[30])

        val state = GameEngine.create(MatchConfig(tokens = 2, map = MapId.INFINITY), seed = 8L)
        // Infinity start[0]=5, start[1]=25.
        // For P0, prog=5 -> ringIdx=10 (junction).
        // For P1, prog=5 -> ringIdx=30 (also junction, shares tileId[30] == 10!).
        state.players[0].tokens[0] = 3
        state.players[1].tokens[0] = 5

        val plan = GameEngine.doMove(state, 0, 0, 2)
        assertEquals(5, plan.to)
        assertTrue("Capture must trigger across Infinity junction", plan.captured)
        assertEquals(-1, state.players[1].tokens[0])
    }

    @Test
    fun `all 5 maps pass geometric invariants and complete simulated games without getting stuck`() {
        val levels = RoboLevel.entries
        val diceModes = DiceMode.entries

        for (mapMeta in MapManager.metadataList) {
            for (portrait in listOf(false, true)) {
                val m = MapManager.build(mapMeta.id, portrait)
                var minGap = Float.POSITIVE_INFINITY
                for (i in 0 until m.n) {
                    for (j in (i + 1) until m.n) {
                        if (m.tileId[i] == m.tileId[j]) continue
                        minGap = minOf(minGap, hypot(m.pts[i].x - m.pts[j].x, m.pts[i].y - m.pts[j].y))
                    }
                }
                assertTrue("${mapMeta.id} tiles too close", minGap > m.tileR * 1.9f)

                for (b in m.boost) {
                    assertFalse("${mapMeta.id} boost on safe", m.safe.contains(b))
                    assertFalse("${mapMeta.id} boost on junction", m.junction.contains(b))
                    assertFalse("${mapMeta.id} boost on gate", m.entry.contains(b))
                }
                assertTrue(m.entry[0] != m.start[0] && m.entry[1] != m.start[1])
                val h0 = m.lanes[0][m.lane - 1]
                val h1 = m.lanes[1][m.lane - 1]
                assertTrue(hypot(h0.x - h1.x, h0.y - h1.y) > m.tileR * 3f)
            }

            // Simulate 45 full matches per map across levels & dice modes
            for (g in 0 until 45) {
                val cfg = MatchConfig(
                    mode = GameMode.ROBO,
                    level = levels[g % levels.size],
                    diceMode = diceModes[g % diceModes.size],
                    tokens = 1 + (g % 4),
                    map = mapMeta.id
                )
                val st = GameEngine.create(cfg, portrait = g % 2 == 0, seed = (g * 31 + 9).toLong())
                var finished = false
                for (step in 0 until 20_000) {
                    val p = st.turn
                    val r = DiceEngine.rollDice(st, p)
                    var extra = false
                    if (r.legalTokens.isNotEmpty()) {
                        val snapshotBefore = st.deepCopy()
                        val ti = RoboAI.chooseMove(st, p, r.value, r.legalTokens)
                        assertTrue("RoboAI picked illegal move", r.legalTokens.contains(ti))
                        // Verify RoboAI did NOT mutate real game state
                        assertEquals(snapshotBefore.players[0].tokens, st.players[0].tokens)
                        assertEquals(snapshotBefore.players[1].tokens, st.players[1].tokens)
                        assertEquals(snapshotBefore.moves, st.moves)

                        val plan = GameEngine.doMove(st, p, ti, r.value)
                        assertTrue(st.players[p].tokens.all { it <= st.map.home })
                        if (GameEngine.isWin(st, p)) {
                            finished = true
                            break
                        }
                        extra = GameEngine.isExtraTurn(r.value, plan)
                    }
                    if (!extra) st.turn = 1 - st.turn
                }
                assertTrue("Simulated game on ${mapMeta.id} did not finish", finished)
            }
        }
    }

    @Test
    fun `genius AI prefers winning or safe capture over suicidal move`() {
        val state = GameEngine.create(
            MatchConfig(mode = GameMode.ROBO, level = RoboLevel.HARD, tokens = 2, map = MapId.ORBIT),
            seed = 99L
        )
        // Token 0 can finish at home with roll=2! Token 1 is just entering/moving on ring.
        state.players[1].tokens[0] = state.map.home - 2
        state.players[1].tokens[1] = 2

        val legal = GameEngine.legalMoves(state, 1, 2)
        val chosen = RoboAI.chooseMove(state, 1, 2, legal)
        assertEquals(0, chosen)
    }

    @Test
    fun `genius AI never mutates real state even when evaluating captures and extra turns`() {
        val state = GameEngine.create(
            MatchConfig(mode = GameMode.ROBO, level = RoboLevel.HARD, tokens = 4, map = MapId.INFINITY),
            seed = 42L
        )
        state.players[1].tokens[0] = 3
        state.players[1].tokens[1] = 10
        state.players[0].tokens[0] = 5
        state.players[0].tokens[1] = 14

        val tokensP0Before = state.players[0].tokens.toList()
        val tokensP1Before = state.players[1].tokens.toList()
        val movesBefore = state.moves
        val turnBefore = state.turn

        val legal = GameEngine.legalMoves(state, 1, 2)
        val chosen = RoboAI.chooseMove(state, 1, 2, legal)
        assertTrue(legal.contains(chosen))
        assertEquals(tokensP0Before, state.players[0].tokens)
        assertEquals(tokensP1Before, state.players[1].tokens)
        assertEquals(movesBefore, state.moves)
        assertEquals(turnBefore, state.turn)
    }
}
