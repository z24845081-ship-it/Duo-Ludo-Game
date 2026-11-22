# Duo Ludo — Native Android App

A native Kotlin & Jetpack Compose conversion of **Duo Ludo**, preserving every original board geometry, rule, sound synthesizer, and adaptive dice insight while adding Classic/Drama dice separation, upgraded Robo AI (Chill / Smart / Genius), interactive tutorial, pause/resume & undo safety, theme & color presets, and offline Room match history.

---

## Project Structure

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Navigation Compose host, edge-to-edge, lifecycle handling
├── engine/
│   ├── Models.kt                    # Core game state, map, roll, move, and config models
│   ├── MapManager.kt                # 5 geometric maps: Orbit, Citadel, Prism, Rhombus, Infinity
│   ├── DiceEngine.kt                # Classic (uniform 1/6) & Drama (bounded adaptive) dice
│   ├── GameEngine.kt                # Pure rules: entry, captures, safe tiles, boosts, home lane
│   └── RoboAI.kt                    # Chill, Smart, and 2-ply lookahead Genius AI
├── data/
│   ├── SettingsModels.kt            # Theme, Blue/Pink presets, audio, gameplay & motion settings
│   ├── LocalStorageManager.kt       # DataStore Preferences persistence
│   └── MatchHistoryDatabase.kt      # Room SQLite database & aggregate player statistics
├── audio/
│   ├── AudioManager.kt              # Native AudioTrack PCM SFX & generative music synthesizer
│   └── HapticManager.kt             # Tactile vibration feedback respecting user settings
└── ui/
    ├── theme/                       # Color.kt, Type.kt (Inter & Plus Jakarta Sans), Theme.kt
    ├── components/DuoComponents.kt  # Custom brand mark, frosted cards, gradient buttons
    ├── game/
    │   ├── AnimationController.kt   # Pause-aware dice, hop, capture & particle animations
    │   ├── GameRenderer.kt          # Jetpack Compose Canvas board, tokens, preview paths & dice
    │   ├── GameViewModel.kt         # StateFlow match orchestrator, timer, undo & lifecycle
    │   └── GameScreen.kt            # Game HUD, board stage, Dice Insight, Move History, Pause
    └── screens/
        ├── SplashScreen.kt          # Animated Duo Ludo welcome screen
        ├── SetupScreen.kt           # Match config, live map carousel preview & quick navigation
        ├── SettingsScreen.kt        # Functional appearance, gameplay, audio & motion settings
        ├── ResultScreen.kt          # Confetti celebration, head-to-head stats & rematch
        ├── StatsScreen.kt           # Lifetime wins, streaks, captures & match history ledger
        ├── TutorialScreen.kt        # Interactive 4-lesson playable tutorial
        └── RulesDialog.kt           # Complete how-to-play guide
```

---

## Importing into Android Studio

1. Extract the downloaded project ZIP to a folder on your computer.
2. Open **Android Studio** (Ladybug / Meerkat or newer recommended).
3. Select **File → Open...** and choose the extracted root folder (containing `settings.gradle.kts`).
4. Wait for Gradle Sync to finish (uses `compileSdk = 36`, `minSdk = 24`, JDK 11+).

---

## Building & Downloading the APK (No Android Studio Needed)

### Option A — Directly in Google AI Studio (Easiest)
Use the **Settings / Export menu** in AI Studio to generate and download the compiled APK directly from the cloud—no installation required.

### Option B — Free Cloud Build via GitHub Actions (No Android Studio, Low-End Laptop Friendly)
This project includes `.github/workflows/build-apk.yml`:
1. Click **Push to GitHub** in AI Studio (or upload the exported ZIP to a free GitHub repository).
2. Open your repository on GitHub and click the **Actions** tab.
3. Open the **Build Duo Ludo APK** workflow run (triggers automatically on push, or click **Run workflow**).
4. Download the **`duo-ludo-debug-apk`** artifact containing `app-debug.apk` — 100% free and watermark-free.

### Option C — Lightweight Command Line (Without Android Studio)
You do not need full Android Studio to compile locally—only **JDK 17+**, **Android SDK Command-Line Tools** (`ANDROID_HOME` / `ANDROID_SDK_ROOT` set), and **Gradle 9.3.1**:
```bash
# Decode the included debug keystore once (Linux/macOS; on Windows use certutil -decode)
base64 -d debug.keystore.base64 > debug.keystore

# Build the debug APK
gradle :app:assembleDebug
```
The compiled debug APK is generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Option D — Android Studio
1. Extract the downloaded project ZIP and select **File → Open...** on the root folder.
2. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)** and click **locate** when finished.

To run the automated unit & Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```

---

## Installing via USB Debugging (ADB)

1. On your Android phone, open **Settings → About phone** and tap **Build number** 7 times to unlock **Developer options**.
2. Open **Settings → System → Developer options** and enable **USB debugging**.
3. Connect your phone to your computer with a USB cable and tap **Allow** on the *"Allow USB debugging?"* prompt on your phone.
4. Verify your device is recognized:
   ```bash
   adb devices
   ```
5. Install the APK directly:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
6. Launch **Duo Ludo** from your phone's app drawer (or run `adb shell monkey -p com.aistudio.duoludo.kxmpzq 1`).

---

## Troubleshooting Installation

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: Uninstall any older debug build first (`adb uninstall com.aistudio.duoludo.kxmpzq`), then re-run `adb install -r ...`.
- **`device unauthorized`**: Unplug and replug the USB cable, then accept the RSA fingerprint dialog on your phone screen.
- **Device not listed under `adb devices`**: Switch USB connection mode from *Charging only* to *File Transfer (MTP)* on your phone, or install your device manufacturer's USB driver (on Windows).
- **"App not installed" when sideloading APK manually**: Enable *Install unknown apps* for your file manager in Android Settings.
