package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager as AndroidAudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Native Android PCM Synthesizer preserving every sound effect & generative music loop
 * from Duo Ludo's WebAudio engine without requiring external audio files or network calls.
 */
class DuoAudioManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleRate = 22050
    private val pentatonic = intArrayOf(0, 2, 4, 7, 9)

    @Volatile
    private var masterVol = 0.9f

    @Volatile
    private var sfxVol = 0.9f

    @Volatile
    private var musicVol = 0.5f

    @Volatile
    private var isMuted = false

    @Volatile
    private var hasFocus = true

    private var musicJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private val sysAudio = context.getSystemService(Context.AUDIO_SERVICE) as? AndroidAudioManager

    private val focusListener = AndroidAudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AndroidAudioManager.AUDIOFOCUS_LOSS,
            AndroidAudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AndroidAudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasFocus = false
                stopMusic()
            }
            AndroidAudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
            }
        }
    }

    fun updateVolumes(master: Float, music: Float, sfx: Float, muted: Boolean) {
        masterVol = master.coerceIn(0f, 1f)
        musicVol = music.coerceIn(0f, 1f)
        sfxVol = sfx.coerceIn(0f, 1f)
        isMuted = muted
        if (muted) stopMusic()
    }

    private fun effectiveSfxGain(): Float =
        if (isMuted || !hasFocus) 0f else (masterVol * sfxVol).coerceIn(0f, 1f)

    private fun effectiveMusicGain(): Float =
        if (isMuted || !hasFocus) 0f else (masterVol * musicVol).coerceIn(0f, 1f)

    private fun requestFocusIfNeeded() {
        val am = sysAudio ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                focusRequest = AudioFocusRequest.Builder(AndroidAudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
            }
            focusRequest?.let { am.requestAudioFocus(it) }
        }
    }

    fun releaseFocus() {
        stopMusic()
        val am = sysAudio ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        }
    }

    private fun midiToHz(n: Int): Double = 440.0 * 2.0.pow((n - 69) / 12.0)

    private enum class Wave { SINE, TRIANGLE, SAW, SQUARE }

    private data class ToneSpec(
        val atSec: Double = 0.0,
        val freqStart: Double,
        val freqEnd: Double = freqStart,
        val durSec: Double,
        val vol: Double = 0.2,
        val attSec: Double = 0.004,
        val wave: Wave = Wave.SINE
    )

    private data class BurstSpec(
        val atSec: Double = 0.0,
        val centerHz: Double,
        val durSec: Double,
        val vol: Double = 0.15,
        val attSec: Double = 0.003
    )

    private fun renderAndPlay(
        totalDurSec: Double,
        gainScale: Float,
        tones: List<ToneSpec> = emptyList(),
        bursts: List<BurstSpec> = emptyList()
    ) {
        if (gainScale <= 0.001f) return
        scope.launch {
            val totalSamples = max(256, (totalDurSec * sampleRate).toInt())
            val buf = FloatArray(totalSamples)

            for (t in tones) {
                val startIdx = (t.atSec * sampleRate).toInt().coerceIn(0, totalSamples - 1)
                val lenSamples = max(1, (t.durSec * sampleRate).toInt())
                val attSamples = max(1, (t.attSec * sampleRate).toInt())
                var phase = 0.0
                for (i in 0 until lenSamples) {
                    val idx = startIdx + i
                    if (idx >= totalSamples) break
                    val frac = i.toDouble() / lenSamples
                    val freq = t.freqStart * (t.freqEnd / t.freqStart).pow(frac)
                    phase += (2.0 * PI * freq) / sampleRate
                    val raw = when (t.wave) {
                        Wave.SINE -> sin(phase)
                        Wave.TRIANGLE -> (2.0 / PI) * kotlin.math.asin(sin(phase))
                        Wave.SAW -> 2.0 * (phase / (2.0 * PI) - floor(0.5 + phase / (2.0 * PI)))
                        Wave.SQUARE -> if (sin(phase) >= 0) 1.0 else -1.0
                    }
                    val env = if (i < attSamples) {
                        i.toDouble() / attSamples
                    } else {
                        exp(-4.5 * (i - attSamples).toDouble() / max(1, lenSamples - attSamples))
                    }
                    buf[idx] += (raw * env * t.vol).toFloat()
                }
            }

            for (b in bursts) {
                val startIdx = (b.atSec * sampleRate).toInt().coerceIn(0, totalSamples - 1)
                val lenSamples = max(1, (b.durSec * sampleRate).toInt())
                val attSamples = max(1, (b.attSec * sampleRate).toInt())
                var lp = 0.0
                val alpha = (2.0 * PI * b.centerHz / sampleRate).coerceIn(0.02, 0.85)
                for (i in 0 until lenSamples) {
                    val idx = startIdx + i
                    if (idx >= totalSamples) break
                    val n = Random.nextDouble(-1.0, 1.0)
                    lp += alpha * (n - lp)
                    val env = if (i < attSamples) {
                        i.toDouble() / attSamples
                    } else {
                        exp(-5.0 * (i - attSamples).toDouble() / max(1, lenSamples - attSamples))
                    }
                    buf[idx] += (lp * env * b.vol).toFloat()
                }
            }

            val pcm = ShortArray(totalSamples)
            for (i in 0 until totalSamples) {
                val v = (buf[i] * gainScale * 0.65f).coerceIn(-1f, 1f)
                pcm[i] = (v * Short.MAX_VALUE).toInt().toShort()
            }

            playPcmStatic(pcm)
        }
    }

    private fun playPcmStatic(pcm: ShortArray) {
        try {
            requestFocusIfNeeded()
            val byteSize = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) {
                    try {
                        t.stop()
                        t.release()
                    } catch (_: Throwable) {
                    }
                }

                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        } catch (_: Throwable) {
            // AudioTrack may fail in headless unit test environments; ignore safely
        }
    }

    fun playClick() {
        renderAndPlay(
            totalDurSec = 0.08,
            gainScale = effectiveSfxGain(),
            tones = listOf(ToneSpec(freqStart = 880.0, freqEnd = 620.0, durSec = 0.05, vol = 0.14, wave = Wave.TRIANGLE)),
            bursts = listOf(BurstSpec(centerHz = 2800.0, durSec = 0.025, vol = 0.08))
        )
    }

    fun playPop() {
        renderAndPlay(
            totalDurSec = 0.12,
            gainScale = effectiveSfxGain(),
            tones = listOf(ToneSpec(freqStart = 420.0, freqEnd = 900.0, durSec = 0.10, vol = 0.18))
        )
    }

    fun playWhoosh() {
        renderAndPlay(
            totalDurSec = 0.25,
            gainScale = effectiveSfxGain(),
            tones = listOf(ToneSpec(freqStart = 320.0, freqEnd = 480.0, durSec = 0.14, vol = 0.08)),
            bursts = listOf(BurstSpec(centerHz = 1100.0, durSec = 0.22, vol = 0.12, attSec = 0.06))
        )
    }

    fun playDiceRoll(durationMs: Long = 900L) {
        val total = (durationMs / 1000.0).coerceIn(0.35, 1.4)
        val tones = mutableListOf<ToneSpec>()
        val bursts = mutableListOf<BurstSpec>()
        var t = 0.0
        var gap = 0.045
        while (t < total - 0.08) {
            val fade = 1.0 - (t / total) * 0.45
            bursts.add(BurstSpec(atSec = t, centerHz = 1500.0 + Random.nextDouble(1800.0), durSec = 0.05, vol = 0.28 * fade))
            tones.add(
                ToneSpec(
                    atSec = t,
                    freqStart = 150.0 + Random.nextDouble(70.0),
                    freqEnd = 70.0,
                    durSec = 0.07,
                    vol = 0.18 * fade,
                    wave = Wave.TRIANGLE
                )
            )
            t += gap
            gap *= 1.13
        }
        tones.add(ToneSpec(atSec = max(0.0, total - 0.04), freqStart = 120.0, freqEnd = 44.0, durSec = 0.2, vol = 0.42))
        renderAndPlay(totalDurSec = total + 0.18, gainScale = effectiveSfxGain(), tones = tones, bursts = bursts)
    }

    fun playStep(stepIndex: Int) {
        val k = stepIndex % 10
        val f = midiToHz(67 + pentatonic[k % 5] + 12 * (k / 5))
        renderAndPlay(
            totalDurSec = 0.13,
            gainScale = effectiveSfxGain(),
            tones = listOf(
                ToneSpec(freqStart = f, durSec = 0.11, vol = 0.20, wave = Wave.TRIANGLE),
                ToneSpec(freqStart = 190.0, freqEnd = 95.0, durSec = 0.07, vol = 0.22)
            ),
            bursts = listOf(BurstSpec(centerHz = 2800.0, durSec = 0.02, vol = 0.10))
        )
    }

    fun playLand() {
        renderAndPlay(
            totalDurSec = 0.14,
            gainScale = effectiveSfxGain(),
            tones = listOf(ToneSpec(freqStart = 150.0, freqEnd = 70.0, durSec = 0.12, vol = 0.30)),
            bursts = listOf(BurstSpec(centerHz = 700.0, durSec = 0.07, vol = 0.12))
        )
    }

    fun playCapture() {
        renderAndPlay(
            totalDurSec = 0.72,
            gainScale = effectiveSfxGain(),
            tones = listOf(
                ToneSpec(freqStart = 330.0, freqEnd = 52.0, durSec = 0.42, vol = 0.28, wave = Wave.SAW),
                ToneSpec(freqStart = 740.0, freqEnd = 200.0, durSec = 0.16, vol = 0.12, wave = Wave.SQUARE),
                ToneSpec(atSec = 0.14, freqStart = midiToHz(60), durSec = 0.3, vol = 0.14, wave = Wave.TRIANGLE),
                ToneSpec(atSec = 0.28, freqStart = midiToHz(55), durSec = 0.4, vol = 0.14, wave = Wave.TRIANGLE)
            ),
            bursts = listOf(BurstSpec(centerHz = 900.0, durSec = 0.36, vol = 0.35))
        )
    }

    fun playBoost() {
        renderAndPlay(
            totalDurSec = 0.68,
            gainScale = effectiveSfxGain(),
            tones = listOf(
                ToneSpec(freqStart = 220.0, freqEnd = 1100.0, durSec = 0.45, vol = 0.14, wave = Wave.SAW),
                ToneSpec(atSec = 0.4, freqStart = midiToHz(84), durSec = 0.25, vol = 0.16)
            ),
            bursts = listOf(BurstSpec(centerHz = 1800.0, durSec = 0.48, vol = 0.25, attSec = 0.1))
        )
    }

    fun playSix() {
        val notes = listOf(72, 76, 79, 84, 88)
        val tones = notes.mapIndexed { i, m ->
            ToneSpec(atSec = i * 0.055, freqStart = midiToHz(m), durSec = 0.35, vol = 0.15, wave = Wave.TRIANGLE)
        }
        renderAndPlay(totalDurSec = 0.65, gainScale = effectiveSfxGain(), tones = tones)
    }

    fun playExtra() {
        renderAndPlay(
            totalDurSec = 0.42,
            gainScale = effectiveSfxGain(),
            tones = listOf(
                ToneSpec(freqStart = midiToHz(79), durSec = 0.16, vol = 0.15, wave = Wave.TRIANGLE),
                ToneSpec(atSec = 0.1, freqStart = midiToHz(86), durSec = 0.28, vol = 0.15, wave = Wave.TRIANGLE)
            )
        )
    }

    fun playHome() {
        val notes = listOf(72, 76, 79, 84, 88)
        val tones = notes.mapIndexed { i, m ->
            ToneSpec(atSec = i * 0.085, freqStart = midiToHz(m), durSec = 0.5, vol = 0.18)
        }
        renderAndPlay(
            totalDurSec = 0.9,
            gainScale = effectiveSfxGain(),
            tones = tones,
            bursts = listOf(BurstSpec(atSec = 0.35, centerHz = 4500.0, durSec = 0.35, vol = 0.08))
        )
    }

    fun playNoMove() {
        renderAndPlay(
            totalDurSec = 0.25,
            gainScale = effectiveSfxGain(),
            tones = listOf(ToneSpec(freqStart = 260.0, freqEnd = 170.0, durSec = 0.22, vol = 0.15, wave = Wave.TRIANGLE))
        )
    }

    fun playWin() {
        val chords = listOf(
            listOf(60, 64, 67),
            listOf(65, 69, 72),
            listOf(67, 71, 74),
            listOf(72, 76, 79, 84)
        )
        val tones = mutableListOf<ToneSpec>()
        chords.forEachIndexed { i, chord ->
            val dur = if (i == 3) 1.5 else 0.4
            chord.forEach { n ->
                tones.add(ToneSpec(atSec = i * 0.22, freqStart = midiToHz(n), durSec = dur, vol = 0.12, wave = Wave.TRIANGLE))
                tones.add(ToneSpec(atSec = i * 0.22, freqStart = midiToHz(n - 12), durSec = dur, vol = 0.06))
            }
        }
        renderAndPlay(totalDurSec = 2.25, gainScale = effectiveSfxGain(), tones = tones)
    }

    fun startMusic() {
        if (isMuted || !hasFocus || musicJob?.isActive == true) return
        val chords = listOf(
            listOf(60, 64, 67, 71),
            listOf(57, 60, 64, 67),
            listOf(53, 57, 60, 64),
            listOf(55, 59, 62, 64)
        )
        val pattern = listOf(0, 1, 2, 3, 2, 1, 3, 2)
        val barSec = 2.4

        musicJob = scope.launch {
            var bar = 0
            while (isActive) {
                val gain = effectiveMusicGain()
                if (gain <= 0.001f) {
                    delay(400)
                    continue
                }
                val c = chords[bar % chords.size]
                val tones = mutableListOf<ToneSpec>()
                c.forEach { n ->
                    tones.add(ToneSpec(atSec = 0.0, freqStart = midiToHz(n - 12), durSec = barSec, vol = 0.05, attSec = 0.4))
                }
                tones.add(ToneSpec(atSec = 0.0, freqStart = midiToHz(c[0] - 24), durSec = barSec * 0.48, vol = 0.12))
                tones.add(ToneSpec(atSec = barSec / 2, freqStart = midiToHz(c[0] - 24), durSec = barSec * 0.48, vol = 0.09))
                pattern.forEachIndexed { idx, ci ->
                    tones.add(
                        ToneSpec(
                            atSec = (idx * barSec) / 8.0,
                            freqStart = midiToHz(c[ci] + 12),
                            durSec = 0.48,
                            vol = 0.085,
                            wave = Wave.TRIANGLE
                        )
                    )
                }
                renderAndPlay(totalDurSec = barSec, gainScale = gain, tones = tones)
                bar++
                delay((barSec * 1000).toLong())
            }
        }
    }

    fun stopMusic() {
        musicJob?.cancel()
        musicJob = null
    }
}
