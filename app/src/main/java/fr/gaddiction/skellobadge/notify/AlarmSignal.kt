package fr.gaddiction.skellobadge.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Son et vibration de l'alarme de retard, en boucle jusqu'à ce qu'on y réponde.
 *
 * Source unique, volontairement : le canal de notification est muet et sans vibration pour
 * cette alarme. Laisser les deux sonner produisait un écho — le système jouant la sonnerie
 * du canal pendant que la lecture en boucle démarrait — et une double vibration.
 *
 * Le signal appartient au processus et non à un écran : il commence au déclenchement du
 * rappel, de sorte qu'il retentisse même si l'affichage plein écran est refusé par le
 * système, et s'arrête à la première réponse, d'où qu'elle vienne.
 */
object AlarmSignal {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val autoStop = Handler(Looper.getMainLooper())

    @Synchronized
    fun start(context: Context) {
        // Un rappel déjà en train de sonner ne doit pas se superposer à lui-même.
        if (player != null || vibrator != null) return

        startVibration(context)
        if (soundAllowed(context)) startSound(context)

        // Filet de sécurité : si le téléphone reste hors de portée, on cesse de sonner au
        // bout de quelques minutes plutôt que de vider la batterie.
        autoStop.postDelayed(::stop, TIMEOUT_MILLIS)
    }

    @Synchronized
    fun stop() {
        autoStop.removeCallbacksAndMessages(null)

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

    /**
     * La vibration part dans tous les cas ; le son respecte le profil sonore du téléphone.
     * En mode vibreur ou silencieux, la vibration est le seul signal perceptible, et le
     * seul qui ne trahisse pas un silence volontaire.
     */
    private fun soundAllowed(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return true
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private fun startSound(context: Context) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        runCatching {
            player = MediaPlayer().apply {
                setDataSource(context.applicationContext, uri)
                setAudioAttributes(alarmAttributes())
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "Lecture de l'alarme impossible", it) }
    }

    private fun startVibration(context: Context) {
        val device = vibratorOf(context) ?: return
        if (!device.hasVibrator()) return
        vibrator = device

        // Motif répété indéfiniment : la valeur 0 désigne l'index auquel reboucler.
        runCatching {
            @Suppress("DEPRECATION")
            device.vibrate(VibrationEffect.createWaveform(PATTERN, 0), alarmAttributes())
        }.onFailure { Log.w(TAG, "Vibration impossible", it) }
    }

    private fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private const val TAG = "AlarmSignal"

    /** Attente, vibration, pause — puis on reboucle. */
    private val PATTERN = longArrayOf(0, 700, 500)

    /** Au-delà, on cesse de sonner ; l'écran d'alarme, lui, reste affiché. */
    private const val TIMEOUT_MILLIS = 5 * 60 * 1000L
}
