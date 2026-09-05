package fr.gaddiction.skellobadge.data

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

data class AppSettings(
    /** Passe à vrai une fois la configuration initiale terminée. On ne redemande plus rien ensuite. */
    val configured: Boolean = false,

    val source: PlanningSource = PlanningSource.ICS,
    val icsUrl: String = "",
    val calendarIds: Set<Long> = emptySet(),

    val targetKind: BadgeTarget = BadgeTarget.APP,
    val targetPackage: String = "",
    val targetLabel: String = "",
    val targetUrl: String = "",

    val clockInLeadMinutes: Int = 5,
    val breakInLeadMinutes: Int = 5,
    val shiftChangeMaxGapMinutes: Int = 5,

    /** Relance si la notification est restée sans réponse. 0 pour désactiver. */
    val nagAfterMinutes: Int = 5,

    /**
     * Rappel de coupure méridienne sur les journées d'un seul tenant. Désactivé par
     * défaut : sur un planning Skello réel la plupart des journées longues sont
     * continues, et ce repli produirait surtout du bruit.
     */
    val lunchFallbackEnabled: Boolean = false,

    val lastSyncEpochMillis: Long = 0L,
    val lastSyncError: String = "",
) {
    /** Vrai si l'application dispose de tout ce qu'il lui faut pour travailler seule. */
    val isUsable: Boolean
        get() = configured && when (source) {
            PlanningSource.ICS -> icsUrl.isNotBlank()
            PlanningSource.DEVICE_CALENDAR -> calendarIds.isNotEmpty()
        }
}
