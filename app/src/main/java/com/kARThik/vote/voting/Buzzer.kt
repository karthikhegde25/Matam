package com.kARThik.vote.voting

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/** Synthesised buzzer tones (no audio assets required, works fully offline). */
object Buzzer {
    val sounds = listOf("Classic beep", "Double beep", "Long buzz", "Rising chime")
    private const val RATE = 22050

    private fun tone(freq: Double, ms: Int, amp: Double = 0.8): ShortArray {
        val n = RATE * ms / 1000
        return ShortArray(n) { i ->
            val fade = minOf(1.0, minOf(i, n - i) / (RATE * 0.005))
            (sin(2 * PI * freq * i / RATE) * amp * fade * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun silence(ms: Int) = ShortArray(RATE * ms / 1000)

    private fun samples(index: Int): ShortArray = when (index) {
        1 -> tone(1000.0, 140) + silence(70) + tone(1000.0, 140)
        2 -> tone(180.0, 700, 0.9)
        3 -> tone(660.0, 130) + tone(880.0, 130) + tone(1320.0, 230)
        else -> tone(1200.0, 450)
    }

    fun play(index: Int, volume: Float) {
        try {
            val data = samples(index.coerceIn(0, sounds.lastIndex))
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.setVolume(volume.coerceIn(0f, 1f))
            track.setNotificationMarkerPosition(data.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        } catch (_: Exception) {
        }
    }

    fun vibrate(context: Context) {
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            v.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }
}
