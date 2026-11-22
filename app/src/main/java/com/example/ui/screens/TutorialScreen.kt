package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AppSettings
import com.example.engine.DiceMode
import com.example.engine.GameEngine
import com.example.engine.GameMode
import com.example.engine.GameState
import com.example.engine.MapId
import com.example.engine.MatchConfig
import com.example.ui.components.DuoBackdrop
import com.example.ui.components.DuoCard
import com.example.ui.components.DuoCircleIconButton
import com.example.ui.components.DuoGradientButton
import com.example.ui.components.DuoSecondaryButton
import com.example.ui.game.AnimationController
import com.example.ui.game.BoardTapHit
import com.example.ui.game.DuoBoardCanvas
import com.example.ui.game.TurnPhase
import com.example.ui.theme.LocalDuoPalette
import kotlinx.coroutines.launch

private data class TutorialLesson(
    val title: String,
    val instruction: String,
    val fixedRoll: Int,
    val setup: (Boolean) -> GameState,
    val expectedHint: String,
    val successText: String
)

@Composable
fun TutorialScreen(
    settings: AppSettings,
    onFinish: () -> Unit,
    onButtonClickSound: () -> Unit,
    onStepSound: (Int) -> Unit,
    onCaptureSound: () -> Unit,
    onBoostSound: () -> Unit,
    onHomeSound: () -> Unit
) {
    val palette = LocalDuoPalette.current
    val scope = rememberCoroutineScope()
    val anim = remember { AnimationController() }

    val lessons = remember {
        listOf(
            TutorialLesson(
                title = "1. Enter with a 1",
                instruction = "Tokens begin in your base slots. Tap the glowing dice to roll a 1, then tap your glowing blue token to enter the board!",
                fixedRoll = 1,
                setup = { portrait ->
                    GameEngine.create(
                        MatchConfig(mode = GameMode.HUMO, tokens = 2, map = MapId.ORBIT, names = listOf("You", "Rival")),
                        portrait = portrait,
                        seed = 11L
                    ).apply { turn = 0 }
                },
                expectedHint = "Tap the dice, then tap a token",
                successText = "Nice! A roll of 1 deploys a token onto your start star."
            ),
            TutorialLesson(
                title = "2. Capture & Safe Stars",
                instruction = "You rolled a 3! Land directly on the lone Pink token ahead to send it back to base and earn a bonus turn.",
                fixedRoll = 3,
                setup = { portrait ->
                    GameEngine.create(
                        MatchConfig(mode = GameMode.HUMO, tokens = 2, map = MapId.ORBIT, names = listOf("You", "Rival")),
                        portrait = portrait,
                        seed = 22L
                    ).apply {
                        turn = 0
                        players[0].tokens[0] = 1
                        // Player 1 token at ring index 4 -> for Player 0 that's prog 4, so player 1 prog = (4 - 20 + 40) = 24
                        players[1].tokens[0] = 24
                    }
                },
                expectedHint = "Tap the dice, then capture Pink!",
                successText = "Captured! Remember: gold Star tiles and stacked pairs (2+ tokens) cannot be captured."
            ),
            TutorialLesson(
                title = "3. Boost Tiles (+3)",
                instruction = "Purple lightning tiles are Boosts! Roll a 2 to land on the Boost tile and surge 3 extra steps forward.",
                fixedRoll = 2,
                setup = { portrait ->
                    GameEngine.create(
                        MatchConfig(mode = GameMode.HUMO, tokens = 2, map = MapId.ORBIT, names = listOf("You", "Rival")),
                        portrait = portrait,
                        seed = 33L
                    ).apply {
                        turn = 0
                        players[0].tokens[0] = 1 // Orbit boost at index 3
                    }
                },
                expectedHint = "Tap dice -> tap token to hit Boost!",
                successText = "Zoom! Boost tiles propel your token +3 steps automatically."
            ),
            TutorialLesson(
                title = "4. Gate & Exact Finish",
                instruction = "Your token is inside your home lane, 2 steps from the House! Roll a 2 to bring it home with an exact number.",
                fixedRoll = 2,
                setup = { portrait ->
                    GameEngine.create(
                        MatchConfig(mode = GameMode.HUMO, tokens = 2, map = MapId.ORBIT, names = listOf("You", "Rival")),
                        portrait = portrait,
                        seed = 44L
                    ).apply {
                        turn = 0
                        players[0].tokens[0] = map.home - 2
                    }
                },
                expectedHint = "Tap dice -> move into Home!",
                successText = "Home sweet home! You're ready for a full Duo Ludo duel."
            )
        )
    }

    var lessonIndex by remember { mutableIntStateOf(0) }
    var portraitState by remember { mutableStateOf(false) }
    var gameState by remember(lessonIndex) { mutableStateOf(lessons[lessonIndex].setup(portraitState)) }
    var phase by remember(lessonIndex) { mutableStateOf(TurnPhase.ROLL) }
    var legalTokens by remember(lessonIndex) { mutableStateOf<List<Int>>(emptyList()) }
    var lessonCompleted by remember(lessonIndex) { mutableStateOf(false) }

    LaunchedEffect(lessonIndex) {
        anim.reset(0)
        gameState = lessons[lessonIndex].setup(portraitState)
        phase = TurnPhase.ROLL
        legalTokens = emptyList()
        lessonCompleted = false
    }

    BackHandler { onFinish() }

    val lesson = lessons[lessonIndex]

    DuoBackdrop(reducedMotion = settings.reducedMotion) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DuoCircleIconButton(
                        onClick = {
                            onButtonClickSound()
                            onFinish()
                        },
                        modifier = Modifier.testTag("tutorial_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "Interactive Tutorial",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.ink
                        )
                        Text(
                            text = "Lesson ${lessonIndex + 1} of ${lessons.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted
                        )
                    }
                }
            }

            DuoCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.players[0].deep
                    )
                    Text(
                        text = if (lessonCompleted) lesson.successText else lesson.instruction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.ink
                    )
                    if (lessonCompleted) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = palette.players[0].deep,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Lesson complete!",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.players[0].deep
                                )
                            }
                            if (lessonIndex < lessons.lastIndex) {
                                DuoGradientButton(
                                    onClick = {
                                        onButtonClickSound()
                                        lessonIndex++
                                    },
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("tutorial_next_button")
                                ) {
                                    Text("Next Lesson")
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                DuoGradientButton(
                                    onClick = {
                                        onButtonClickSound()
                                        onFinish()
                                    },
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("tutorial_finish_button")
                                ) {
                                    Text("Start Playing")
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                DuoBoardCanvas(
                    gameState = gameState,
                    anim = anim,
                    turnPhase = phase,
                    canRoll = phase == TurnPhase.ROLL,
                    legalTokens = legalTokens,
                    selectedTokenIndex = null,
                    previewPlan = null,
                    rollValue = lesson.fixedRoll,
                    hints = listOf(
                        if (lessonCompleted) "Done!" else lesson.expectedHint,
                        ""
                    ),
                    reducedMotion = settings.reducedMotion,
                    onPortraitDetected = { isPort ->
                        portraitState = isPort
                        if (gameState.portrait != isPort) {
                            gameState = lessons[lessonIndex].setup(isPort)
                        }
                    },
                    onTapHit = { hit ->
                        when (hit) {
                            BoardTapHit.Dice -> {
                                if (phase != TurnPhase.ROLL) return@DuoBoardCanvas
                                onButtonClickSound()
                                phase = TurnPhase.ROLLING
                                scope.launch {
                                    anim.animateRollDice(
                                        targetFace = lesson.fixedRoll,
                                        speedMultiplier = 0.85f,
                                        reducedMotion = settings.reducedMotion
                                    )
                                    legalTokens = GameEngine.legalMoves(gameState, 0, lesson.fixedRoll)
                                    phase = TurnPhase.PICK_TOKEN
                                }
                            }
                            is BoardTapHit.Token, is BoardTapHit.Destination -> {
                                if (phase != TurnPhase.PICK_TOKEN) return@DuoBoardCanvas
                                val ti = when (hit) {
                                    is BoardTapHit.Token -> hit.tokenIndex
                                    is BoardTapHit.Destination -> hit.tokenIndex
                                    else -> return@DuoBoardCanvas
                                }
                                if (!legalTokens.contains(ti)) return@DuoBoardCanvas
                                onButtonClickSound()
                                phase = TurnPhase.MOVING
                                legalTokens = emptyList()
                                scope.launch {
                                    val plan = GameEngine.doMove(gameState, 0, ti, lesson.fixedRoll)
                                    anim.animateTokenMove(
                                        map = gameState.map,
                                        plan = plan,
                                        speedMultiplier = 1.0f,
                                        reducedMotion = settings.reducedMotion,
                                        onStep = onStepSound,
                                        onBoostStart = onBoostSound,
                                        onCapture = { onCaptureSound() },
                                        onHome = onHomeSound
                                    )
                                    gameState = gameState.copy()
                                    phase = TurnPhase.OVER
                                    lessonCompleted = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("tutorial_board_canvas")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    lessons.indices.forEach { idx ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (idx == lessonIndex) palette.players[0].soft else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (idx == lessonIndex) palette.players[0].main else palette.hairline
                            ),
                            onClick = {
                                onButtonClickSound()
                                lessonIndex = idx
                            }
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (idx == lessonIndex) palette.players[0].deep else palette.muted,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                DuoSecondaryButton(
                    onClick = {
                        onButtonClickSound()
                        onFinish()
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Skip Tutorial")
                }
            }
        }
    }
}
