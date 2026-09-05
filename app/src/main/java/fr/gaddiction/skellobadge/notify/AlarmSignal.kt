package fr.gaddiction.skellobadge.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Son et vibration de l'alarme de retard, en boucle jusqu'à ce qu'on y réponde.
 *
 * Le canal de notification ne joue son son qu'une fois : pour qu'un badgeage oublié soit
 * réellement impossible à manquer, il faut piloter la lecture soi-même.
 *
 * La vibration est déclenchée dans tous les cas, y compris quand le téléphone est en mode
 * vibreur ou silencieux — c'est alors le seul signal perceptible. Le son, lui, respecte le
 * profil sonore : il ne se déclenche qu'en mode normal, pour ne pas trahir un silence
 * volontaire au milieu d'une réunion.
 */
class AlarmSignal(private val context: Context) {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    fun start() {
        startVibration()
        if (soundAllowed()) startSound()
    }

    fun stop() {
        runCatching {
            player?.apply {
                if (isPlaying) stop()
                release()
            }
        }
        player = null

        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun soundAllowed(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return true
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        runCatching {
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "Lecture de l'alarme impossible", it) }
    }

    private fun startVibration() {
        val device = vibratorOf(context) ?: return
        if (!device.hasVibrator()) return
        vibrator = device

        // Motif répété indéfiniment : la valeur 0 désigne l'index auquel reboucler.
        val effect = VibrationEffect.createWaveform(PATTERN, 0)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        runCatching {
            @Suppress("DEPRECATION")
            device.vibrate(effect, attributes)
        }.onFailure { Log.w(TAG, "Vibration impossible", it) }
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private companion object {
        const val TAG = "AlarmSignal"

        /** Attente, vibration, pause — puis on reboucle. */
        val PATTERN = longArrayOf(0, 700, 500)
    }
}
