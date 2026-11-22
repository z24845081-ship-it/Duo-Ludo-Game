package com.example.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DuoAudioManager
import com.example.audio.HapticManager
import com.example.data.AppSettings
import com.example.data.DuoLudoDatabase
import com.example.data.LocalStorageManager
import com.example.data.MatchHistoryRepository
import com.example.data.MatchRecord
import com.example.data.PlayerAggregateStats
import com.example.engine.DiceEngine
import com.example.engine.DiceRollResult
import com.example.engine.GameEngine
import com.example.engine.GameMode
import com.example.engine.GameState
import com.example.engine.MapManager
import com.example.engine.MatchConfig
import com.example.engine.MoveHistoryEntry
import com.example.engine.MovePlan
import com.example.engine.RoboAI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ToastTone {
    PRIMARY,
    HIT,
    GOLD,
    SOFT
}

data class GameToast(
    val id: Long,
    val text: String,
    val tone: ToastTone
)

data class UndoSnapshot(
    val state: GameState,
    val lastRoll: Pair<Int, DiceRollResult>?,
    val moveHistory: List<MoveHistoryEntry>
)

data class MatchUiState(
    val config: MatchConfig = MatchConfig(),
    val gameState: GameState? = null,
    val turnPhase: TurnPhase = TurnPhase.IDLE,
    val canRoll: Boolean = false,
    val legalTokens: List<Int> = emptyList(),
    val selectedTokenIndex: Int? = null,
    val previewPlan: MovePlan? = null,
    val currentRollValue: Int = 0,
    val playerHints: List<String> = listOf("", ""),
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0L,
    val lastRollInsight: Pair<Int, DiceRollResult>? = null,
    val showInsight: Boolean = false,
    val showMoveHistory: Boolean = false,
    val moveHistory: List<MoveHistoryEntry> = emptyList(),
    val canUndo: Boolean = false,
    val passDevicePlayerIndex: Int? = null,
    val toasts: List<GameToast> = emptyList(),
    val winnerIndex: Int? = null,
    val matchCompletedEventId: Long = 0L
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = LocalStorageManager(application)
    private val database = DuoLudoDatabase.getInstance(application)
    private val historyRepo = MatchHistoryRepository(database.matchHistoryDao())

    val audio = DuoAudioManager(application)
    val haptics = HapticManager(application)
    val anim = AnimationController()

    val settings: StateFlow<AppSettings> = storage.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    val matchHistory: StateFlow<List<MatchRecord>> = historyRepo.allMatches.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val aggregateStats: StateFlow<PlayerAggregateStats> = historyRepo.aggregateStats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerAggregateStats()
    )

    private val _matchState = MutableStateFlow(MatchUiState())
    val matchState: StateFlow<MatchUiState> = _matchState.asStateFlow()

    private var matchGeneration = 0L
    private var matchJob: Job? = null
    private var timerJob: Job? = null
    private var pendingRoll: DiceRollResult? = null
    private var undoStack = mutableListOf<UndoSnapshot>()
    private var toastIdGen = 1L
    private var moveLogIdGen = 1L
    private var resultEventGen = 1L
    private var activePortrait = false

    init {
        viewModelScope.launch {
            settings.collect { s ->
                audio.updateVolumes(
                    master = s.masterVolume,
                    music = s.musicVolume,
                    sfx = s.sfxVolume,
                    muted = s.isMuted
                )
                haptics.enabled = s.hapticFeedback
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            storage.updateSettings(transform)
        }
    }

    fun clearMatchHistory() {
        viewModelScope.launch {
            historyRepo.clearAll()
        }
    }

    fun deleteMatchRecord(id: Long) {
        viewModelScope.launch {
            historyRepo.deleteMatch(id)
        }
    }

    fun onBoardPortraitChanged(portrait: Boolean) {
        if (activePortrait == portrait) return
        activePortrait = portrait
        val st = _matchState.value.gameState ?: return
        if (st.portrait != portrait) {
            val updated = st.copy(
                map = MapManager.build(st.map.id, portrait),
                portrait = portrait
            )
            _matchState.update { cur ->
                val updatedPreview = if (cur.selectedTokenIndex != null && cur.currentRollValue > 0) {
                    GameEngine.previewMove(updated, updated.turn, cur.selectedTokenIndex, cur.currentRollValue)
                } else null
                cur.copy(gameState = updated, previewPlan = updatedPreview)
            }
        }
    }

    fun startNewMatch(config: MatchConfig) {
        matchGeneration++
        val gen = matchGeneration
        matchJob?.cancel()
        timerJob?.cancel()
        audio.stopMusic()
        pendingRoll = null
        undoStack.clear()

        val newState = GameEngine.create(config, portrait = activePortrait)
        // Fair coin flip for starter
        newState.turn = if (newState.rng.nextFloat() < 0.5f) 0 else 1
        anim.reset(newState.turn)

        _matchState.value = MatchUiState(
            config = config,
            gameState = newState,
            turnPhase = TurnPhase.IDLE,
            canRoll = false,
            playerHints = listOf("", ""),
            isPaused = false,
            elapsedMs = 0L,
            lastRollInsight = null,
            showInsight = false,
            showMoveHistory = false,
            moveHistory = emptyList(),
            canUndo = false,
            passDevicePlayerIndex = null,
            toasts = emptyList(),
            winnerIndex = null
        )

        startTimer(gen)
        matchJob = viewModelScope.launch {
            anim.pauseAwareDelay(450L)
            if (!isCurrentGen(gen)) return@launch
            showToast("${newState.players[newState.turn].name} starts", ToastTone.SOFT)
            beginTurn(gen)
        }
    }

    fun restartMatch() {
        val cfg = _matchState.value.config
        startNewMatch(cfg)
    }

    fun quitToMenu() {
        matchGeneration++
        matchJob?.cancel()
        timerJob?.cancel()
        anim.reset(0)
        audio.stopMusic()
        pendingRoll = null
        undoStack.clear()
        _matchState.update {
            it.copy(
                gameState = null,
                turnPhase = TurnPhase.IDLE,
                isPaused = false,
                winnerIndex = null,
                passDevicePlayerIndex = null
            )
        }
    }

    fun setPaused(paused: Boolean) {
        val cur = _matchState.value
        if (cur.gameState == null || cur.winnerIndex != null) return
        if (cur.isPaused == paused) return
        anim.setPaused(paused)
        _matchState.update { it.copy(isPaused = paused) }
    }

    fun onAppBackgrounded() {
        val cur = _matchState.value
        if (cur.gameState != null && cur.winnerIndex == null && !cur.isPaused) {
            setPaused(true)
        }
        audio.stopMusic()
    }

    fun toggleMute() {
        val curMuted = settings.value.isMuted
        updateSettings { it.copy(isMuted = !curMuted) }
        if (curMuted) {
            audio.playClick()
        }
    }

    fun toggleInsight() {
        audio.playClick()
        _matchState.update { it.copy(showInsight = !it.showInsight, showMoveHistory = false) }
    }

    fun toggleMoveHistory() {
        audio.playClick()
        _matchState.update { it.copy(showMoveHistory = !it.showMoveHistory, showInsight = false) }
    }

    fun dismissPassDevice() {
        val cur = _matchState.value
        if (cur.passDevicePlayerIndex == null || cur.isPaused) return
        audio.playClick()
        haptics.tap()
        val p = cur.gameState?.turn ?: 0
        _matchState.update {
            it.copy(
                passDevicePlayerIndex = null,
                turnPhase = TurnPhase.ROLL,
                canRoll = true,
                playerHints = buildHints(p, "Tap the dice")
            )
        }
    }

    fun confirmSelectedMove() {
        val cur = _matchState.value
        val ti = cur.selectedTokenIndex ?: return
        val roll = pendingRoll ?: return
        if (cur.isPaused || cur.turnPhase != TurnPhase.PICK_TOKEN) return
        executeChosenMove(ti, roll)
    }

    fun undoLastMove() {
        val cur = _matchState.value
        if (cur.isPaused || !cur.config.allowUndo || undoStack.isEmpty()) return
        if (cur.turnPhase == TurnPhase.ROLLING || cur.turnPhase == TurnPhase.MOVING || cur.winnerIndex != null) return

        audio.playClick()
        haptics.tap()

        // Cancel any pending bot/turn coroutine cleanly and restore snapshot
        matchGeneration++
        val gen = matchGeneration
        matchJob?.cancel()
        pendingRoll = null

        val snapshot = undoStack.removeAt(undoStack.lastIndex)
        val restored = snapshot.state.deepCopy().let { s ->
            if (s.portrait != activePortrait) {
                s.copy(map = MapManager.build(s.map.id, activePortrait), portrait = activePortrait)
            } else s
        }
        anim.reset(restored.turn)

        _matchState.update {
            it.copy(
                gameState = restored,
                turnPhase = TurnPhase.IDLE,
                canRoll = false,
                legalTokens = emptyList(),
                selectedTokenIndex = null,
                previewPlan = null,
                currentRollValue = 0,
                lastRollInsight = snapshot.lastRoll,
                moveHistory = snapshot.moveHistory,
                canUndo = undoStack.isNotEmpty(),
                passDevicePlayerIndex = null,
                playerHints = buildHints(restored.turn, "Undone")
            )
        }

        showToast("Move undone", ToastTone.SOFT)
        matchJob = viewModelScope.launch {
            beginTurn(gen, skipPassDevice = true)
        }
    }

    fun onBoardTap(hit: BoardTapHit) {
        val cur = _matchState.value
        val st = cur.gameState ?: return
        if (cur.isPaused || cur.winnerIndex != null) return

        when (hit) {
            BoardTapHit.Dice -> {
                if (cur.turnPhase != TurnPhase.ROLL || !cur.canRoll || st.players[st.turn].bot) return
                val gen = matchGeneration
                matchJob?.cancel()
                matchJob = viewModelScope.launch {
                    doRoll(gen)
                }
            }

            is BoardTapHit.Token -> {
                if (cur.turnPhase != TurnPhase.PICK_TOKEN) return
                val roll = pendingRoll ?: return
                if (!roll.legalTokens.contains(hit.tokenIndex)) return

                audio.playClick()
                haptics.tap()

                val confirmMode = settings.value.confirmTokenMove
                if (!confirmMode || cur.selectedTokenIndex == hit.tokenIndex) {
                    executeChosenMove(hit.tokenIndex, roll)
                } else {
                    val preview = GameEngine.previewMove(st, st.turn, hit.tokenIndex, roll.value)
                    _matchState.update {
                        it.copy(
                            selectedTokenIndex = hit.tokenIndex,
                            previewPlan = preview,
                            playerHints = buildHints(st.turn, "Tap again to move")
                        )
                    }
                }
            }

            is BoardTapHit.Destination -> {
                if (cur.turnPhase != TurnPhase.PICK_TOKEN) return
                val roll = pendingRoll ?: return
                if (!roll.legalTokens.contains(hit.tokenIndex)) return
                audio.playClick()
                haptics.tap()
                executeChosenMove(hit.tokenIndex, roll)
            }
        }
    }

    private fun executeChosenMove(tokenIndex: Int, roll: DiceRollResult) {
        val gen = matchGeneration
        pendingRoll = null
        matchJob?.cancel()
        matchJob = viewModelScope.launch {
            performMove(gen, tokenIndex, roll)
        }
    }

    private fun isCurrentGen(gen: Long): Boolean = gen == matchGeneration

    private fun startTimer(gen: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && isCurrentGen(gen)) {
                delay(250L)
                val cur = _matchState.value
                if (!cur.isPaused && cur.winnerIndex == null && cur.gameState != null) {
                    _matchState.update { it.copy(elapsedMs = it.elapsedMs + 250L) }
                }
            }
        }
    }

    private suspend fun beginTurn(gen: Long, skipPassDevice: Boolean = false) {
        anim.awaitIfPaused()
        if (!isCurrentGen(gen)) return

        val st = _matchState.value.gameState ?: return
        val p = st.turn
        val isBot = st.players[p].bot
        val cfg = _matchState.value.config

        // In Humo mode with Pass-Device enabled, show clean handoff interstitial between human turns
        if (!skipPassDevice && cfg.mode == GameMode.HUMO && cfg.passDeviceInHumo && !isBot && st.moves > 0) {
            _matchState.update {
                it.copy(
                    turnPhase = TurnPhase.PASS_DEVICE,
                    canRoll = false,
                    legalTokens = emptyList(),
                    selectedTokenIndex = null,
                    previewPlan = null,
                    passDevicePlayerIndex = p,
                    playerHints = buildHints(p, "Pass device")
                )
            }
            return
        }

        _matchState.update {
            it.copy(
                turnPhase = TurnPhase.ROLL,
                canRoll = !isBot,
                legalTokens = emptyList(),
                selectedTokenIndex = null,
                previewPlan = null,
                currentRollValue = 0,
                passDevicePlayerIndex = null,
                playerHints = buildHints(p, if (isBot) "Thinking..." else "Tap the dice")
            )
        }

        if (isBot) {
            val speed = settings.value.animationIntensity.speedMultiplier
            anim.pauseAwareDelay((650f * speed).toLong().coerceAtLeast(220L))
            if (!isCurrentGen(gen)) return
            doRoll(gen)
        }
    }

    private suspend fun doRoll(gen: Long) {
        val st = _matchState.value.gameState ?: return
        val p = st.turn
        val isBot = st.players[p].bot
        val curSettings = settings.value

        // Save undo snapshot before rolling only if human turn & undo enabled?
        // Actually saving snapshot right before a move (or before human roll) lets undo restore cleanly!
        // Let's capture pre-roll snapshot if human (or before AI moves) so undoing goes back to before the human's decision.
        val preRollSnapshot = if (!isBot && _matchState.value.config.allowUndo) {
            UndoSnapshot(
                state = st.deepCopy(),
                lastRoll = _matchState.value.lastRollInsight,
                moveHistory = _matchState.value.moveHistory
            )
        } else null

        _matchState.update {
            it.copy(
                turnPhase = TurnPhase.ROLLING,
                canRoll = false,
                selectedTokenIndex = null,
                previewPlan = null,
                playerHints = buildHints(p, "Rolling...")
            )
        }

        haptics.roll()
        val rollDurMs = (860f * curSettings.animationIntensity.speedMultiplier).toLong()
        if (!curSettings.reducedMotion) {
            audio.playDiceRoll(rollDurMs)
        }
        val roll = DiceEngine.rollDice(st, p)
        anim.animateRollDice(
            targetFace = roll.value,
            speedMultiplier = curSettings.animationIntensity.speedMultiplier,
            reducedMotion = curSettings.reducedMotion
        )
        if (!isCurrentGen(gen)) return

        if (roll.value == 6) audio.playSix()

        _matchState.update {
            it.copy(
                gameState = st.copy(),
                lastRollInsight = p to roll,
                currentRollValue = roll.value
            )
        }

        if (roll.legalTokens.isEmpty()) {
            audio.playNoMove()
            showToast("No move for ${roll.value}", ToastTone.SOFT)
            _matchState.update { it.copy(playerHints = buildHints(p, "No move")) }
            anim.pauseAwareDelay((780f * curSettings.animationIntensity.speedMultiplier).toLong().coerceAtLeast(250L))
            if (!isCurrentGen(gen)) return
            endTurn(gen, extraTurn = false)
            return
        }

        // If only one distinct board position is legal (e.g. all legal tokens are in base on a 1, or only 1 legal token)
        val distinctPositions = roll.legalTokens.map { st.players[p].tokens[it] }.toSet()
        if (distinctPositions.size == 1 || isBot) {
            if (preRollSnapshot != null) pushUndo(preRollSnapshot)
            _matchState.update {
                it.copy(
                    turnPhase = TurnPhase.MOVING,
                    legalTokens = emptyList(),
                    playerHints = buildHints(p, "")
                )
            }
            val delayMs = if (isBot) 520L else 240L
            anim.pauseAwareDelay((delayMs * curSettings.animationIntensity.speedMultiplier).toLong().coerceAtLeast(100L))
            if (!isCurrentGen(gen)) return
            val chosenTi = if (distinctPositions.size == 1) {
                roll.legalTokens.first()
            } else {
                RoboAI.chooseMove(st, p, roll.value, roll.legalTokens)
            }
            performMove(gen, chosenTi, roll)
            return
        }

        if (preRollSnapshot != null) pushUndo(preRollSnapshot)
        pendingRoll = roll
        _matchState.update {
            it.copy(
                turnPhase = TurnPhase.PICK_TOKEN,
                legalTokens = roll.legalTokens,
                selectedTokenIndex = null,
                previewPlan = null,
                playerHints = buildHints(p, "Pick a token (${roll.value})")
            )
        }
    }

    private suspend fun performMove(gen: Long, tokenIndex: Int, roll: DiceRollResult) {
        val st = _matchState.value.gameState ?: return
        val p = st.turn
        val curSettings = settings.value

        _matchState.update {
            it.copy(
                turnPhase = TurnPhase.MOVING,
                legalTokens = emptyList(),
                selectedTokenIndex = null,
                previewPlan = null,
                playerHints = buildHints(p, "")
            )
        }

        val plan = GameEngine.doMove(st, p, tokenIndex, roll.value)
        if (plan.entered) audio.playPop()

        anim.animateTokenMove(
            map = st.map,
            plan = plan,
            speedMultiplier = curSettings.animationIntensity.speedMultiplier,
            reducedMotion = curSettings.reducedMotion,
            onStep = { stepIdx -> audio.playStep(stepIdx) },
            onBoostStart = { audio.playBoost() },
            onCapture = { _ ->
                audio.playCapture()
                haptics.capture()
            },
            onHome = {
                audio.playHome()
                haptics.home()
            }
        )
        if (!isCurrentGen(gen)) return
        audio.playLand()

        val extraTurn = GameEngine.isExtraTurn(roll.value, plan)
        val entry = MoveHistoryEntry(
            id = moveLogIdGen++,
            moveNumber = st.moves,
            playerIndex = p,
            playerName = st.players[p].name,
            rollValue = roll.value,
            tokenIndex = tokenIndex,
            fromProg = plan.from,
            toProg = plan.to,
            entered = plan.entered,
            boosted = plan.boosted,
            capturedCount = plan.captures.size,
            reachedHome = plan.reachedHome,
            extraTurn = extraTurn
        )

        _matchState.update {
            it.copy(
                gameState = st.copy(),
                moveHistory = listOf(entry) + it.moveHistory,
                canUndo = undoStack.isNotEmpty() && it.config.allowUndo
            )
        }

        if (GameEngine.isWin(st, p)) {
            finishMatch(gen, p)
            return
        }

        when {
            plan.captured -> showToast("Captured!", ToastTone.HIT)
            plan.reachedHome -> showToast("Home!", ToastTone.GOLD)
            plan.boosted -> showToast("Boost +3", ToastTone.PRIMARY)
        }

        if (extraTurn) {
            st.players[p].stats = st.players[p].stats.copy(extra = st.players[p].stats.extra + 1)
            if (!plan.captured && !plan.reachedHome) {
                showToast("Roll again", ToastTone.SOFT)
            }
            audio.playExtra()
        }

        val pauseAfter = if (extraTurn) 560L else 300L
        anim.pauseAwareDelay((pauseAfter * curSettings.animationIntensity.speedMultiplier).toLong().coerceAtLeast(140L))
        if (!isCurrentGen(gen)) return

        endTurn(gen, extraTurn)
    }

    private suspend fun endTurn(gen: Long, extraTurn: Boolean) {
        val st = _matchState.value.gameState ?: return
        val curSettings = settings.value
        if (!extraTurn) {
            st.turn = 1 - st.turn
            _matchState.update {
                it.copy(
                    gameState = st.copy(),
                    turnPhase = TurnPhase.IDLE,
                    canRoll = false,
                    playerHints = listOf("", "")
                )
            }
            audio.playWhoosh()
            anim.animateMoveDiceTo(
                targetOwner = st.turn,
                instant = false,
                speedMultiplier = curSettings.animationIntensity.speedMultiplier,
                reducedMotion = curSettings.reducedMotion
            )
            if (!isCurrentGen(gen)) return
        }
        beginTurn(gen)
    }

    private suspend fun finishMatch(gen: Long, winner: Int) {
        timerJob?.cancel()
        val cur = _matchState.value
        val st = cur.gameState ?: return

        _matchState.update {
            it.copy(
                turnPhase = TurnPhase.OVER,
                canRoll = false,
                legalTokens = emptyList(),
                selectedTokenIndex = null,
                previewPlan = null,
                canUndo = false,
                playerHints = listOf("", ""),
                winnerIndex = winner
            )
        }

        audio.playWin()
        haptics.home()

        val record = MatchRecord(
            modeId = cur.config.mode.id,
            roboLevelId = cur.config.level.id,
            diceModeId = cur.config.diceMode.id,
            mapId = st.map.id.id,
            mapName = st.map.name,
            tokensCount = st.n,
            player1Name = st.players[0].name,
            player2Name = st.players[1].name,
            winnerIndex = winner,
            winnerName = st.players[winner].name,
            durationMs = cur.elapsedMs,
            totalMoves = st.moves,
            p1Captures = st.players[0].stats.captures,
            p2Captures = st.players[1].stats.captures,
            p1Sixes = st.players[0].stats.sixes,
            p2Sixes = st.players[1].stats.sixes,
            p1Rolls = st.players[0].stats.rolls,
            p2Rolls = st.players[1].stats.rolls
        )
        viewModelScope.launch {
            historyRepo.recordMatch(record)
        }

        delay(950L)
        if (!isCurrentGen(gen)) return
        _matchState.update { it.copy(matchCompletedEventId = resultEventGen++) }

        delay(900L)
        if (!isCurrentGen(gen)) return
        audio.startMusic()
    }

    private fun pushUndo(snapshot: UndoSnapshot) {
        undoStack.add(snapshot)
        if (undoStack.size > 20) undoStack.removeAt(0)
    }

    private fun buildHints(activePlayer: Int, activeHint: String): List<String> =
        if (activePlayer == 0) listOf(activeHint, "") else listOf("", activeHint)

    private fun showToast(text: String, tone: ToastTone) {
        val id = toastIdGen++
        _matchState.update { cur ->
            val trimmed = (cur.toasts + GameToast(id, text, tone)).takeLast(2)
            cur.copy(toasts = trimmed)
        }
        viewModelScope.launch {
            delay(1500L)
            _matchState.update { cur ->
                cur.copy(toasts = cur.toasts.filterNot { it.id == id })
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        matchJob?.cancel()
        timerJob?.cancel()
        audio.releaseFocus()
    }
}
