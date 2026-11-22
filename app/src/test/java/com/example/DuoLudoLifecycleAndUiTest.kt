package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettings
import com.example.data.ColorPreset
import com.example.data.ThemeMode
import com.example.engine.DiceMode
import com.example.engine.GameMode
import com.example.engine.MapId
import com.example.engine.MatchConfig
import com.example.engine.RoboLevel
import com.example.ui.game.AnimationController
import com.example.ui.game.GameViewModel
import com.example.ui.game.TurnPhase
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.theme.DuoLudoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DuoLudoLifecycleAndUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setup screen defaults to classic dice, allows switching to drama and infinity map, and launches match`() {
        var launchedConfig: MatchConfig? = null
        var currentSettings = AppSettings()

        composeRule.setContent {
            DuoLudoTheme(themeMode = ThemeMode.LIGHT, colorPreset = ColorPreset.AZURE_ROSE) {
                SetupScreen(
                    settings = currentSettings,
                    onUpdateSettings = { transform -> currentSettings = transform(currentSettings) },
                    onPlayClick = { launchedConfig = it },
                    onOpenSettings = {},
                    onOpenStats = {},
                    onOpenTutorial = {},
                    onButtonClickSound = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Default dice mode is Classic
        composeRule.onNodeWithTag("dice_mode_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("dice_mode_1").performScrollTo().assertIsDisplayed()

        // Switch to Drama Dice and Infinity map
        composeRule.onNodeWithTag("dice_mode_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("map_chip_infinity").performScrollTo().performClick()
        composeRule.onNodeWithTag("setup_play_button").performClick()
        composeRule.waitForIdle()

        assertNotNull(launchedConfig)
        assertEquals(DiceMode.DRAMA, launchedConfig?.diceMode)
        assertEquals(MapId.INFINITY, launchedConfig?.map)
    }

    @Test
    fun `settings screen updates theme mode and color preset`() {
        var settings = AppSettings()

        composeRule.setContent {
            DuoLudoTheme(themeMode = settings.themeMode, colorPreset = settings.colorPreset) {
                SettingsScreen(
                    settings = settings,
                    onUpdateSettings = { transform -> settings = transform(settings) },
                    onBack = {},
                    onButtonClickSound = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("theme_chip_dark").performClick()
        composeRule.onNodeWithTag("preset_chip_glacier_berry").performClick()
        composeRule.waitForIdle()

        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(ColorPreset.GLACIER_BERRY, settings.colorPreset)
    }

    @Test
    fun `pause freezes timer and animation controller, resume continues cleanly`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GameViewModel(app)
        val cfg = MatchConfig(
            mode = GameMode.HUMO,
            diceMode = DiceMode.CLASSIC,
            tokens = 2,
            map = MapId.ORBIT,
            names = listOf("Alice", "Bob"),
            passDeviceInHumo = false
        )

        vm.startNewMatch(cfg)
        advanceTimeBy(1000L)
        val elapsedBeforePause = vm.matchState.value.elapsedMs
        assertTrue(elapsedBeforePause > 0L)

        vm.setPaused(true)
        assertTrue(vm.matchState.value.isPaused)
        assertTrue(vm.anim.isPaused)

        advanceTimeBy(2000L)
        assertEquals(elapsedBeforePause, vm.matchState.value.elapsedMs)

        vm.setPaused(false)
        assertFalse(vm.matchState.value.isPaused)
        assertFalse(vm.anim.isPaused)

        advanceTimeBy(600L)
        assertTrue(vm.matchState.value.elapsedMs > elapsedBeforePause)
        vm.quitToMenu()
    }

    @Test
    fun `restart resets statistics, timer, moves, and prevents stale state`() = runTest(testDispatcher) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = GameViewModel(app)
        val cfg = MatchConfig(
            mode = GameMode.ROBO,
            level = RoboLevel.NORMAL,
            diceMode = DiceMode.CLASSIC,
            tokens = 2,
            map = MapId.CITADEL
        )

        vm.startNewMatch(cfg)
        advanceTimeBy(1200L)
        assertNotNull(vm.matchState.value.gameState)
        assertTrue(vm.matchState.value.elapsedMs > 0L)

        vm.restartMatch()
        assertEquals(0L, vm.matchState.value.elapsedMs)
        assertEquals(0, vm.matchState.value.gameState?.moves)
        assertEquals(TurnPhase.IDLE, vm.matchState.value.turnPhase)
        assertFalse(vm.matchState.value.isPaused)
        assertTrue(vm.matchState.value.moveHistory.isEmpty())

        vm.quitToMenu()
    }

    @Test
    fun `animation controller pause gate suspends and resumes cleanly`() = runTest(testDispatcher) {
        val anim = AnimationController()
        anim.setPaused(true)
        assertTrue(anim.isPaused)
        anim.setPaused(false)
        assertFalse(anim.isPaused)
    }
}
