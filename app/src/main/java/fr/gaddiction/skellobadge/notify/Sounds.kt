package fr.gaddiction.skellobadge.notify

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import fr.gaddiction.skellobadge.data.Sound

/** Passage d'un [Sound] à ce qu'Android sait jouer. */
object Sounds {

    /**
     * Adresse du son. Une sonnerie intégrée est désignée par son nom de ressource et non
     * par son identifiant numérique : le canal de notification garde cette adresse d'une
     * version à l'autre, alors que l'identifiant peut changer à chaque compilation.
     */
    fun uriOf(context: Context, sound: Sound, forAlarm: Boolean): Uri = when (sound) {
        is Sound.Bundled -> Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath("raw")
            .appendPath(sound.tone.resourceName)
            .build()

        Sound.Phone -> phoneDefault(forAlarm)
        is Sound.Picked -> Uri.parse(sound.uri)
    }

    fun phoneDefault(forAlarm: Boolean): Uri =
        if (forAlarm) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: Settings.System.DEFAULT_NOTIFICATION_URI
        } else {
            Settings.System.DEFAULT_NOTIFICATION_URI
        }

    fun label(context: Context, sound: Sound): String = when (sound) {
        is Sound.Bundled -> sound.tone.label
        Sound.Phone -> "Son du téléphone"
        // Le titre se lit dans la médiathèque : on le garde, l'écran le redemande à chaque
        // recomposition.
        is Sound.Picked -> titles.getOrPut(sound.uri) {
            runCatching {
                RingtoneManager.getRingtone(context, Uri.parse(sound.uri))?.getTitle(context)
            }.getOrNull()?.takeIf(String::isNotBlank) ?: "Son choisi"
        }
    }

    private val titles = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Sélecteur de sons du téléphone. « Par défaut » et « Aucun » sont masqués : le premier
     * figure déjà dans la liste de l'application, le second ferait d'un rappel une
     * notification muette, ce que les réglages Android permettent déjà en connaissance de cause.
     */
    fun pickerIntent(current: Sound, forAlarm: Boolean): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                if (forAlarm) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION,
            )
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                if (forAlarm) "Son de l'alarme" else "Son des rappels",
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            if (current is Sound.Picked) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(current.uri))
            }
        }

    fun attributes(forAlarm: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(
            if (forAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION,
        )
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}

/**
 * Écoute d'un son depuis les réglages, sur le flux où il retentira pour de vrai : on
 * entend ainsi le volume réel, celui des notifications ou celui des alarmes.
 */
object SoundPreview {

    private var player: MediaPlayer? = null
    private val autoStop = Handler(Looper.getMainLooper())

    @Synchronized
    fun play(context: Context, sound: Sound, forAlarm: Boolean) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(context.applicationContext, Sounds.uriOf(context, sound, forAlarm))
                setAudioAttributes(Sounds.attributes(forAlarm))
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.onFailure {
            Log.w(TAG, "Écoute impossible", it)
            stop()
        }
        // Une sonnerie de réveil peut durer une minute : quelques secondes suffisent à juger.
        autoStop.postDelayed(::stop, MAX_MILLIS)
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
    }

    private const val TAG = "SoundPreview"
    private const val MAX_MILLIS = 4_000L
}
