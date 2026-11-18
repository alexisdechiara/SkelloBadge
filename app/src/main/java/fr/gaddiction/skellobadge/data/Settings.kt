package fr.gaddiction.skellobadge.data

import fr.gaddiction.skellobadge.domain.NotePause
import fr.gaddiction.skellobadge.domain.ReminderKind

/** D'où vient le planning. */
enum class PlanningSource {
    /** Flux ICS Skello : aucune permission calendrier requise, mise en cache hors ligne. */
    ICS,

    /** Calendrier de l'appareil : utile si le planning est déjà synchronisé sur le téléphone. */
    DEVICE_CALENDAR,
}

/** Ce qu'on ouvre quand l'utilisateur touche la notification. */
enum class BadgeTarget {
    /** Une application installée, désignée par l'utilisateur lors de la configuration. */
    APP,

    /** Une adresse web (badgeuse Skello en ligne). */
    URL,
}

/**
 * Sonnerie livrée avec l'application, dans `res/raw/tone_<nom>.ogg`.
 *
 * Le son de notification par défaut du téléphone est celui de toutes les autres
 * applications : au milieu d'un service, rien ne distingue alors un rappel de badgeage
 * d'un message quelconque. Ces sons-là ne servent qu'ici.
 */
enum class Tone(val label: String, val description: String) {
    CARILLON("Carillon", "Deux notes montantes, nettes sans être agressives"),
    CLOCHETTE("Clochette", "Un seul tintement clair"),
    MARIMBA("Marimba", "Quatre notes boisées, en arpège"),
    BIPS("Bips", "Trois bips aigus, audibles dans le bruit"),
    REVEIL("Réveil", "Bips alternés, façon réveil"),
    ;

    /** Nom de la ressource audio, stable d'une version à l'autre contrairement à son identifiant. */
    val resourceName: String get() = "tone_" + name.lowercase()
}

/** Son d'un rappel ou de l'alarme. */
sealed interface Sound {

    data class Bundled(val tone: Tone) : Sound

    /** Le son par défaut du téléphone : celui des notifications, ou celui des alarmes. */
    data object Phone : Sound

    /** Un son choisi dans la liste du téléphone, désigné par son adresse. */
    data class Picked(val uri: String) : Sound

    /** Forme enregistrée dans les préférences, relue par [parse]. */
    val key: String
        get() = when (this) {
            is Bundled -> PREFIX_TONE + tone.name
            Phone -> KEY_PHONE
            is Picked -> PREFIX_URI + uri
        }

    companion object {
        private const val PREFIX_TONE = "tone:"
        private const val PREFIX_URI = "uri:"
        private const val KEY_PHONE = "phone"

        /** Nul si la clé est absente ou désigne une sonnerie qui n'existe plus. */
        fun parse(key: String?): Sound? = when {
            key == null -> null
            key == KEY_PHONE -> Phone
            key.startsWith(PREFIX_TONE) -> Tone.entries
                .firstOrNull { it.name == key.removePrefix(PREFIX_TONE) }
                ?.let(::Bundled)
            key.startsWith(PREFIX_URI) -> key.removePrefix(PREFIX_URI)
                .takeIf(String::isNotBlank)
                ?.let(::Picked)
            else -> null
        }
    }
}

/**
 * Texte d'un rappel. Le corps accepte deux marqueurs, remplacés au moment de l'affichage :
 * `{heure}` par l'heure de l'action et `{poste}` par le libellé du service.
 */
data class Wording(val title: String, val body: String) {
    fun format(time: String, shift: String): String =
        body.replace(PLACEHOLDER_TIME, time).replace(PLACEHOLDER_SHIFT, shift)

    companion object {
        const val PLACEHOLDER_TIME = "{heure}"
        const val PLACEHOLDER_SHIFT = "{poste}"

        /**
         * Formulations par défaut, entièrement remplaçables depuis les réglages.
         *
         * Chaque titre désigne une action distincte. « Badge ta sortie » servait autrefois
         * au départ en pause comme à la fin de poste : sur un écran verrouillé, à 12h05
         * comme à 17h00, on lisait la même phrase au moment précis où il faut décider vite.
         *
         * Départ et retour restent neutres à dessein : selon l'écart au planning, ils
         * couvrent aussi bien une coupure du midi qu'un enchaînement de deux services.
         */
        val DEFAULTS: Map<ReminderKind, Wording> = mapOf(
            ReminderKind.CLOCK_IN to Wording(
                "Badge ton entrée",
                "Entrée à {heure} · {poste}",
            ),
            ReminderKind.BREAK_OUT to Wording(
                "Badge ton départ",
                "Sortie à {heure}",
            ),
            ReminderKind.BREAK_IN to Wording(
                "Badge ton retour",
                "Reprise à {heure} · {poste}",
            ),
            ReminderKind.SHIFT_CHANGE to Wording(
                "Badge le changement de poste",
                "À {heure} : {poste}",
            ),
            ReminderKind.CLOCK_OUT to Wording(
                "Badge ta sortie",
                "Fin de service à {heure}",
            ),
            ReminderKind.STANDBY_CONFIRM to Wording(
                "Demande à Quentin",
                "Tu es de réserve demain sur {poste}. Remplacement ou repos ?",
            ),
        )
    }
}

data class AppSettings(
    /** Passe à vrai une fois la configuration initiale terminée. On ne redemande plus rien ensuite. */
    val configured: Boolean = false,

    val source: PlanningSource = PlanningSource.ICS,
    val icsUrl: String = "",
    val calendarIds: Set<Long> = emptySet(),

    val targetKind: BadgeTarget = BadgeTarget.APP,
    /** Pré-réglé sur la badgeuse Skello ; reste modifiable dans la liste des applications. */
    val targetPackage: String = SKELLO_PUNCH_CLOCK_PACKAGE,
    val targetLabel: String = SKELLO_PUNCH_CLOCK_LABEL,
    val targetUrl: String = "",

    /**
     * Une minute d'avance : le directeur étant pointilleux sur l'horaire, le rappel doit
     * tomber juste avant l'heure exacte, pas cinq minutes plus tôt où il serait oublié.
     */
    val clockInLeadMinutes: Int = 1,
    val breakInLeadMinutes: Int = 1,
    /** Les sorties sont rappelées à l'heure pile par défaut : badger trop tôt fausserait le pointage. */
    val breakOutLeadMinutes: Int = 0,
    val clockOutLeadMinutes: Int = 0,
    /** Écart à partir duquel deux créneaux consécutifs comptent comme une pause. */
    val breakMinGapMinutes: Int = 0,

    /** Intervalle entre deux relances tant que le badgeage n'est pas confirmé. */
    val nagIntervalMinutes: Int = 1,
    /** Garde-fou : au-delà, on cesse de relancer même sans réponse. */
    val nagMaxCount: Int = 30,

    /** Alarme plein écran, façon réveil, si le rappel reste ignoré trop longtemps. */
    val fullScreenAlarmEnabled: Boolean = true,
    val fullScreenAlarmAfterMinutes: Int = 5,

    /** Son des rappels, commun à tous leurs types. */
    val reminderSound: Sound = DEFAULT_REMINDER_SOUND,
    /**
     * Son de l'alarme plein écran. Celui du réveil par défaut : il est fait pour tirer du
     * sommeil, et le téléphone le joue au volume des alarmes.
     */
    val alarmSound: Sound = Sound.Phone,

    /** Rappel de pause sur les journées longues dont le planning ne prévoit rien. */
    val lunchFallbackEnabled: Boolean = true,
    /** Bornes de la pause forcée, en minutes depuis minuit. */
    val lunchStartMinutes: Int = 12 * 60,
    val lunchEndMinutes: Int = 13 * 60,

    /**
     * Lire l'heure de pause dans la note du créneau : « Pause cantine 12h-13h », « Repas
     * 11h45 → 12h30 », ou le trou entre deux animations datées. Prime sur la pause forcée.
     */
    val notePauseEnabled: Boolean = true,
    /** Mots qui signalent la pause dans une note, comparés sans casse ni accents. */
    val notePauseKeywords: Set<String> = NotePause.DEFAULT_KEYWORDS,

    /**
     * Un créneau dont le libellé contient l'un de ces fragments est une journée de
     * réserve : il reste affiché avec ses horaires, mais ne déclenche aucun rappel.
     * « EG ou Off » signifie que la présence n'est requise qu'en renfort de dernière minute.
     */
    val standbyPatterns: Set<String> = setOf("ou off"),

    /**
     * Dates, au format ISO, où l'utilisateur a déclaré qu'il travaillerait finalement.
     *
     * Une journée de réserve ne sonne pas, mais c'est justement le jour où l'on est appelé
     * en renfort qu'un oubli coûte le plus cher : ce jour-là, un bouton sur la carte du
     * jour rétablit tous les rappels.
     */
    val workingStandbyDates: Set<String> = emptySet(),

    /**
     * Rappel, la veille au soir, de demander au responsable si l'on remplace quelqu'un.
     * Le planning porte lui-même la consigne « à confirmer la veille » sur ces créneaux.
     */
    val standbyAskEnabled: Boolean = true,
    /**
     * Heure de la demande, la veille, en minutes depuis minuit. Deux valeurs selon la
     * disponibilité : fin de journée quand on travaille, milieu d'après-midi en repos.
     */
    val askWorkingMinutes: Int = 17 * 60,
    val askRestMinutes: Int = 14 * 60,

    /** Responsable à qui poser la question, choisi dans les contacts du téléphone. */
    val contactName: String = "",
    val contactNumber: String = "",

    /** Types de service explicitement mis en sourdine par l'utilisateur. */
    val disabledShiftTypes: Set<String> = emptySet(),

    /**
     * Tous les types de service jamais rencontrés dans le planning, accumulés au fil des
     * synchronisations. Sans cette mémoire, un type absent de la fenêtre courante
     * disparaîtrait de la liste de sélection, et son réglage deviendrait inatteignable.
     */
    val knownShiftTypes: Set<String> = emptySet(),

    /** Formulation de chaque type de rappel. Toute entrée absente retombe sur le défaut. */
    val wording: Map<ReminderKind, Wording> = Wording.DEFAULTS,

    val lastSyncEpochMillis: Long = 0L,
    val lastSyncError: String = "",

    /**
     * Chercher, une fois par jour, une version plus récente parmi les releases GitHub.
     * L'application étant installée hors du Play Store, rien d'autre ne la tient à jour.
     */
    val updateCheckEnabled: Boolean = true,
    val lastUpdateCheckEpochMillis: Long = 0L,
    /** Dernière version déjà annoncée par notification : on ne la répète pas chaque jour. */
    val notifiedUpdateVersion: String = "",
    /** Version écartée d'un « Plus tard » : sa bannière ne revient pas, la suivante si. */
    val dismissedUpdateVersion: String = "",
) {
    fun wordingFor(kind: ReminderKind): Wording =
        wording[kind] ?: Wording.DEFAULTS.getValue(kind)

    /** Le libellé désigne-t-il une journée de réserve ? */
    fun isStandby(title: String): Boolean {
        val normalized = title.lowercase()
        return standbyPatterns.any { it.isNotBlank() && normalized.contains(it.lowercase()) }
    }

    /** L'utilisateur a-t-il déclaré qu'il travaillerait ce jour-là ? */
    fun worksOn(date: java.time.LocalDate): Boolean = date.toString() in workingStandbyDates

    /** Vrai si l'application dispose de tout ce qu'il lui faut pour travailler seule. */
    val isUsable: Boolean
        get() = configured && when (source) {
            PlanningSource.ICS -> icsUrl.isNotBlank()
            PlanningSource.DEVICE_CALENDAR -> calendarIds.isNotEmpty()
        }

    companion object {
        val DEFAULT_REMINDER_SOUND: Sound = Sound.Bundled(Tone.CARILLON)

        /**
         * « Skello : la badgeuse » sur le Play Store, mais l'application s'installe sous
         * le nom « Badgeuse » : c'est ce libellé-là que l'utilisateur reconnaît, et celui
         * sur lequel il faut chercher dans la liste des applications.
         */
        const val SKELLO_PUNCH_CLOCK_PACKAGE = "com.skellopunchclock"
        const val SKELLO_PUNCH_CLOCK_LABEL = "Badgeuse"

        /** L'application des équipes, au cas où ce serait elle la cible voulue. */
        const val SKELLO_TEAM_PACKAGE = "app.skello.skello"
    }
}
