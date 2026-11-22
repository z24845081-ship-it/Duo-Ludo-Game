package com.example.engine

enum class GameMode(val id: String, val label: String, val subtitle: String) {
    HUMO("humo", "Humo", "Human vs human"),
    ROBO("robo", "Robo", "You vs the robot")
}

enum class RoboLevel(val id: String, val label: String, val description: String) {
    EASY("easy", "Chill", "Casual, mostly random moves"),
    NORMAL("normal", "Smart", "Strategic captures, safety & boosts"),
    HARD("hard", "Genius", "Predictive lookahead evaluation")
}

enum class DiceMode(val id: String, val label: String, val description: String) {
    CLASSIC(
        id = "classic",
        label = "Classic Dice",
        description = "Pure uniform 1/6 (16.7%) probability for every number. No catch-up or adaptive weighting."
    ),
    DRAMA(
        id = "drama",
        label = "Drama Dice (Adaptive)",
        description = "Bounded adaptive weights nudge odds symmetrically for both players to keep matches close."
    )
}

enum class MapId(val id: String) {
    ORBIT("orbit"),
    CITADEL("citadel"),
    PRISM("prism"),
    RHOMBUS("rhombus"),
    INFINITY("infinity");

    companion object {
        fun fromId(id: String): MapId = entries.firstOrNull { it.id == id } ?: ORBIT
    }
}

enum class TileShape {
    CIRCLE,
    SQUARE,
    TRIANGLE,
    DIAMOND
}

data class Vec2(val x: Float, val y: Float)

data class Bounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
)

data class MapMetadata(
    val id: MapId,
    val name: String,
    val blurb: String,
    val tileShape: TileShape,
    val ringTiles: Int,
    val pathLength: Int,
    val safeCount: Int,
    val boostCount: Int,
    val hasJunction: Boolean,
    val styleTag: String
)

data class MapGeometry(
    val id: MapId,
    val name: String,
    val blurb: String,
    val tileShape: TileShape,
    val portrait: Boolean,
    val n: Int,
    val gate: Int,
    val rl: Int,
    val len: Int,
    val home: Int,
    val lane: Int,
    val pts: List<Vec2>,
    val ang: List<Float>,
    val tileId: List<Int>,
    val junction: Set<Int>,
    val start: List<Int>,
    val entry: List<Int>,
    val lanes: List<List<Vec2>>,
    val laneDir: List<Float>,
    val safe: Set<Int>,
    val boost: Set<Int>,
    val spacing: Float,
    val tileR: Float,
    val dense: List<Vec2>,
    val bounds: Bounds
)

data class PlayerStats(
    val rolls: Int = 0,
    val sixes: Int = 0,
    val captures: Int = 0,
    val extra: Int = 0,
    val moves: Int = 0,
    val lost: Int = 0
)

data class PlayerState(
    val name: String,
    val bot: Boolean,
    val tokens: MutableList<Int>,
    val rolls: MutableList<Int> = mutableListOf(),
    var misses: Int = 0,
    var homeMisses: Int = 0,
    var stats: PlayerStats = PlayerStats()
) {
    fun deepCopy(): PlayerState = PlayerState(
        name = name,
        bot = bot,
        tokens = tokens.toMutableList(),
        rolls = rolls.toMutableList(),
        misses = misses,
        homeMisses = homeMisses,
        stats = stats
    )
}

/**
 * Deterministic xorshift/mulberry32 PRNG matching the original engine for reproducible tests.
 */
class Mulberry32Rng(seed: Long) {
    private var state: Int = seed.toInt()

    fun nextFloat(): Float {
        state = state + 0x6D2B79F5
        var t = (state xor (state ushr 15)) * (1 or state)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        val unsigned = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        return unsigned.toFloat() / 4294967296f
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return (nextFloat() * bound).toInt().coerceIn(0, bound - 1)
    }

    fun copyState(): Int = state
    fun restoreState(s: Int) {
        state = s
    }
}

data class GameState(
    val map: MapGeometry,
    val portrait: Boolean,
    val n: Int,
    val level: RoboLevel,
    val diceMode: DiceMode,
    var turn: Int,
    var moves: Int,
    val rng: Mulberry32Rng,
    val players: List<PlayerState>
) {
    fun deepCopy(): GameState = GameState(
        map = map,
        portrait = portrait,
        n = n,
        level = level,
        diceMode = diceMode,
        turn = turn,
        moves = moves,
        rng = Mulberry32Rng(0).apply { restoreState(rng.copyState()) },
        players = players.map { it.deepCopy() }
    )
}

data class CaptureEvent(
    val playerIndex: Int,
    val tokenIndex: Int,
    val fromProg: Int,
    val seqIndex: Int
)

data class MovePlan(
    val playerIndex: Int,
    val tokenIndex: Int,
    val from: Int,
    val to: Int,
    val seq: List<Int>,
    val captures: List<CaptureEvent>,
    val boosted: Boolean,
    val entered: Boolean,
    val reachedHome: Boolean,
    val captured: Boolean
)

data class DiceRollResult(
    val value: Int,
    val weights: List<Float>,
    val normalizedProbabilities: List<Float>,
    val reasons: List<String>,
    val legalTokens: List<Int>,
    val mode: DiceMode
)

data class MoveHistoryEntry(
    val id: Long,
    val moveNumber: Int,
    val playerIndex: Int,
    val playerName: String,
    val rollValue: Int,
    val tokenIndex: Int,
    val fromProg: Int,
    val toProg: Int,
    val entered: Boolean,
    val boosted: Boolean,
    val capturedCount: Int,
    val reachedHome: Boolean,
    val extraTurn: Boolean
)

data class MatchConfig(
    val mode: GameMode = GameMode.ROBO,
    val level: RoboLevel = RoboLevel.NORMAL,
    val diceMode: DiceMode = DiceMode.CLASSIC,
    val tokens: Int = 4,
    val map: MapId = MapId.ORBIT,
    val names: List<String> = listOf("Player 1", "Robo"),
    val passDeviceInHumo: Boolean = true,
    val allowUndo: Boolean = true
)
