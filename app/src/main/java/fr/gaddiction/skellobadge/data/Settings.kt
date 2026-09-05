package fr.gaddiction.skellobadge.data

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
 * Texte d'un rappel. Le corps accepte deux marqueurs, remplacés au moment de l'affichage :
 * `{heure}` par l'heure de l'action et `{poste}` par le libellé du service.
 */
data class Wording(val title: String, val body: String) {
    fun format(time: String, shift: String): String =
        body.replace(PLACEHOLDER_TIME, time).replace(PLACEHOLDER_SHIFT, shift)

    companion object {
        const val PLACEHOLDER_TIME = "{heure}"
        const val PLACEHOLDER_SHIFT = "{poste}"

        /** Formulations par défaut, entièrement remplaçables depuis les réglages. */
        val DEFAULTS: Map<ReminderKind, Wording> = mapOf(
            ReminderKind.CLOCK_IN to Wording(
                "Badge ton entrée",
                "Prise de poste à {heure} · {poste}",
            ),
            ReminderKind.BREAK_OUT to Wording(
                "Badge ta sortie",
                "Pause à partir de {heure}",
            ),
            ReminderKind.BREAK_IN to Wording(
                "Badge ton retour",
                "Reprise à {heure} · {poste}",
            ),
            ReminderKind.SHIFT_CHANGE to Wording(
                "Badge la bascule",
                "À {heure} : {poste}",
            ),
            ReminderKind.CLOCK_OUT to Wording(
                "Badge ta sortie",
                "Fin de service à {heure}",
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
    val shiftChangeMaxGapMinutes: Int = 5,

    /** Intervalle entre deux relances tant que le badgeage n'est pas confirmé. */
    val nagIntervalMinutes: Int = 1,
    /** Garde-fou : au-delà, on cesse de relancer même sans réponse. */
    val nagMaxCount: Int = 30,

    /** Alarme plein écran, façon réveil, si le rappel reste ignoré trop longtemps. */
    val fullScreenAlarmEnabled: Boolean = true,
    val fullScreenAlarmAfterMinutes: Int = 5,

    /** Rappel de coupure sur les journées longues dont le planning ne prévoit rien. */
    val lunchFallbackEnabled: Boolean = true,

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
        /** « Skello : la badgeuse » sur le Play Store. */
        const val SKELLO_PUNCH_CLOCK_PACKAGE = "com.skellopunchclock"
        const val SKELLO_PUNCH_CLOCK_LABEL = "Skello : la badgeuse"

        /** L'application des équipes, au cas où ce serait elle la cible voulue. */
        const val SKELLO_TEAM_PACKAGE = "app.skello.skello"
    }
}
