package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.game.GameScreen
import com.example.ui.game.GameViewModel
import com.example.ui.screens.ResultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TutorialScreen
import com.example.ui.theme.DuoLudoTheme

object DuoRoutes {
    const val ROUTE_SPLASH = "splash"
    const val ROUTE_SETUP = "setup"
    const val ROUTE_GAME = "game"
    const val ROUTE_RESULT = "result"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_STATS = "stats"
    const val ROUTE_TUTORIAL = "tutorial"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuoLudoApp()
        }
    }
}

@Composable
fun DuoLudoApp(gameViewModel: GameViewModel = viewModel()) {
    val settings by gameViewModel.settings.collectAsStateWithLifecycle()
    val matchState by gameViewModel.matchState.collectAsStateWithLifecycle()
    val matchHistory by gameViewModel.matchHistory.collectAsStateWithLifecycle()
    val aggregateStats by gameViewModel.aggregateStats.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    gameViewModel.onAppBackgrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(matchState.matchCompletedEventId) {
        if (matchState.matchCompletedEventId > 0L && matchState.winnerIndex != null) {
            navController.navigate(DuoRoutes.ROUTE_RESULT) {
                launchSingleTop = true
            }
        }
    }

    DuoLudoTheme(
        themeMode = settings.themeMode,
        colorPreset = settings.colorPreset
    ) {
        NavHost(
            navController = navController,
            startDestination = DuoRoutes.ROUTE_SPLASH
        ) {
            composable(DuoRoutes.ROUTE_SPLASH) {
                SplashScreen(
                    reducedMotion = settings.reducedMotion,
                    onComplete = {
                        gameViewModel.audio.playPop()
                        val target = if (!settings.hasSeenTutorial) {
                            DuoRoutes.ROUTE_SETUP
                        } else {
                            DuoRoutes.ROUTE_SETUP
                        }
                        navController.navigate(target) {
                            popUpTo(DuoRoutes.ROUTE_SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(DuoRoutes.ROUTE_SETUP) {
                SetupScreen(
                    settings = settings,
                    onUpdateSettings = { transform -> gameViewModel.updateSettings(transform) },
                    onPlayClick = { config ->
                        gameViewModel.startNewMatch(config)
                        navController.navigate(DuoRoutes.ROUTE_GAME) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSettings = { navController.navigate(DuoRoutes.ROUTE_SETTINGS) },
                    onOpenStats = { navController.navigate(DuoRoutes.ROUTE_STATS) },
                    onOpenTutorial = { navController.navigate(DuoRoutes.ROUTE_TUTORIAL) },
                    onButtonClickSound = {
                        gameViewModel.audio.playClick()
                        gameViewModel.haptics.tap()
                    }
                )
            }

            composable(DuoRoutes.ROUTE_GAME) {
                GameScreen(
                    matchState = matchState,
                    settings = settings,
                    anim = gameViewModel.anim,
                    onPortraitDetected = { gameViewModel.onBoardPortraitChanged(it) },
                    onBoardTap = { hit -> gameViewModel.onBoardTap(hit) },
                    onConfirmSelectedMove = { gameViewModel.confirmSelectedMove() },
                    onDismissPassDevice = { gameViewModel.dismissPassDevice() },
                    onPause = {
                        gameViewModel.audio.playClick()
                        gameViewModel.setPaused(true)
                    },
                    onResume = {
                        gameViewModel.audio.playClick()
                        gameViewModel.setPaused(false)
                    },
                    onRestart = {
                        gameViewModel.audio.playClick()
                        gameViewModel.restartMatch()
                    },
                    onQuitToMenu = {
                        gameViewModel.audio.playClick()
                        gameViewModel.quitToMenu()
                        navController.popBackStack(DuoRoutes.ROUTE_SETUP, inclusive = false)
                    },
                    onToggleInsight = { gameViewModel.toggleInsight() },
                    onToggleMoveHistory = { gameViewModel.toggleMoveHistory() },
                    onUndo = { gameViewModel.undoLastMove() },
                    onToggleMute = { gameViewModel.toggleMute() },
                    onOpenSettings = {
                        gameViewModel.audio.playClick()
                        navController.navigate(DuoRoutes.ROUTE_SETTINGS)
                    }
                )
            }

            composable(DuoRoutes.ROUTE_RESULT) {
                ResultScreen(
                    matchState = matchState,
                    settings = settings,
                    onRematch = {
                        gameViewModel.audio.playClick()
                        gameViewModel.restartMatch()
                        navController.navigate(DuoRoutes.ROUTE_GAME) {
                            popUpTo(DuoRoutes.ROUTE_SETUP) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onNewSetup = {
                        gameViewModel.audio.playClick()
                        gameViewModel.quitToMenu()
                        navController.popBackStack(DuoRoutes.ROUTE_SETUP, inclusive = false)
                    },
                    onToggleMusic = { shouldPlay ->
                        if (shouldPlay) {
                            if (settings.isMuted) {
                                gameViewModel.updateSettings { it.copy(isMuted = false) }
                            }
                            gameViewModel.audio.startMusic()
                        } else {
                            gameViewModel.audio.stopMusic()
                        }
                    }
                )
            }

            composable(DuoRoutes.ROUTE_SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    onUpdateSettings = { transform -> gameViewModel.updateSettings(transform) },
                    onBack = { navController.popBackStack() },
                    onButtonClickSound = {
                        gameViewModel.audio.playClick()
                        gameViewModel.haptics.tap()
                    }
                )
            }

            composable(DuoRoutes.ROUTE_STATS) {
                StatsScreen(
                    stats = aggregateStats,
                    matches = matchHistory,
                    settings = settings,
                    onClearHistory = { gameViewModel.clearMatchHistory() },
                    onBack = { navController.popBackStack() },
                    onButtonClickSound = {
                        gameViewModel.audio.playClick()
                        gameViewModel.haptics.tap()
                    }
                )
            }

            composable(DuoRoutes.ROUTE_TUTORIAL) {
                TutorialScreen(
                    settings = settings,
                    onFinish = {
                        gameViewModel.updateSettings { it.copy(hasSeenTutorial = true) }
                        navController.popBackStack()
                    },
                    onButtonClickSound = {
                        gameViewModel.audio.playClick()
                        gameViewModel.haptics.tap()
                    },
                    onStepSound = { step -> gameViewModel.audio.playStep(step) },
                    onCaptureSound = {
                        gameViewModel.audio.playCapture()
                        gameViewModel.haptics.capture()
                    },
                    onBoostSound = { gameViewModel.audio.playBoost() },
                    onHomeSound = {
                        gameViewModel.audio.playHome()
                        gameViewModel.haptics.home()
                    }
                )
            }
        }
    }
}
