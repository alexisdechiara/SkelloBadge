package fr.gaddiction.skellobadge.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

import java.time.ZonedDateTime

/**
 * Un créneau brut, tel que fourni par la source de planning (flux ICS Skello ou
 * calendrier de l'appareil). Aucune interprétation métier à ce stade : on ne sait
 * pas encore si c'est du travail, du repos ou une absence.
 */
data class PlanningEvent(
    val uid: String,
    val title: String,
    val note: String? = null,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    /** Vrai all-day déclaré par la source (VALUE=DATE en ICS, flag allDay côté calendrier). */
    val allDayFlag: Boolean = false,
    val url: String? = null,
) {
    val duration: Duration get() = Duration.between(start, end)
}

/** Les moments où l'on doit toucher la badgeuse. */
enum class ReminderKind {
    /** Prise de poste : premier créneau de la journée. */
    CLOCK_IN,

    /** Départ en pause : fin d'un créneau suivi d'un autre après un écart réel. */
    BREAK_OUT,

    /** Retour de pause : reprise après un écart réel. */
    BREAK_IN,

    /**
     * Deux créneaux qui s'enchaînent sans écart : il faut badger la sortie de l'un
     * et l'entrée de l'autre dans la foulée. Un seul rappel plutôt que deux
     * notifications simultanées qui se marcheraient dessus.
     */
    SHIFT_CHANGE,

    /** Fin de poste : dernier créneau de la journée. */
    CLOCK_OUT,

    /**
     * Veille d'une journée de réserve : penser à demander au responsable si l'on remplace
     * quelqu'un. Ce n'est pas un badgeage mais une démarche, d'où l'absence de relance et
     * d'escalade.
     */
    STANDBY_CONFIRM,
}

/**
 * Un créneau de travail. Les créneaux ne sont jamais fusionnés : quand le planning
 * enchaîne deux postes sans écart (10h00→19h00 puis 19h00→21h00), il faut quand même
 * badger la sortie du premier et l'entrée du second — c'est un changement de poste,
 * signalé par un rappel [ReminderKind.SHIFT_CHANGE].
 */
data class WorkBlock(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val title: String,
    val note: String? = null,
    /**
     * Faux pour un créneau de réserve ou un type mis en sourdine : il reste affiché avec
     * ses horaires, mais ne produit aucun rappel.
     */
    val notifies: Boolean = true,
) {
    val duration: Duration get() = Duration.between(start, end)
}

data class Reminder(
    val at: ZonedDateTime,
    val kind: ReminderKind,
    /** Libellé du poste concerné ; pour une bascule, « poste sortant → poste entrant ». */
    val title: String,
    val note: String? = null,
    /** Heure réelle de l'action à effectuer (le rappel peut être avancé de quelques minutes). */
    val actionAt: ZonedDateTime,
) {
    /**
     * Identifiant stable et unique, utilisé comme requestCode de PendingIntent et comme
     * id de notification. Une minute donnée ne peut porter qu'un seul rappel de chaque type.
     */
    val id: Int get() = ((at.toEpochSecond() / 60L).toInt() * 8) + kind.ordinal
}

/**
 * Ce que devient une date une fois le planning interprété.
 *
 * Les rappels figurent sur l'interface elle-même et non sur la seule journée travaillée :
 * la demande de confirmation d'une réserve tombe la veille, qui peut être un jour de repos
 * ou une date absente du planning.
 */
sealed interface DayPlan {
    val date: LocalDate
    val reminders: List<Reminder>

    /** Jour non travaillé : repos hebdomadaire, congé, absence, férié. */
    data class Off(
        override val date: LocalDate,
        val label: String,
        override val reminders: List<Reminder> = emptyList(),
    ) : DayPlan

    /** Aucun créneau au planning pour cette date. */
    data class Empty(
        override val date: LocalDate,
        override val reminders: List<Reminder> = emptyList(),
    ) : DayPlan

    data class Work(
        override val date: LocalDate,
        val blocks: List<WorkBlock>,
        override val reminders: List<Reminder>,
    ) : DayPlan
}

/**
 * Tous les réglages qui influencent le calcul. Les valeurs par défaut sont celles
 * calibrées sur un planning Skello réel.
 */
data class PlanningConfig(
    /** Combien de temps avant la prise de poste on prévient. */
    val clockInLead: Duration = Duration.ofMinutes(1),
    /** Combien de temps avant le retour de pause on prévient. */
    val breakInLead: Duration = Duration.ofMinutes(1),
    /** Les sorties (fin de poste, départ en pause) sont rappelées à l'heure pile par défaut. */
    val clockOutLead: Duration = Duration.ZERO,
    val breakOutLead: Duration = Duration.ZERO,

    /**
     * En dessous de cet écart, deux créneaux consécutifs sont traités comme un
     * changement de poste (un seul rappel groupé) plutôt que comme une pause
     * (deux rappels distincts). Au-delà, c'est une vraie coupure.
     */
    val shiftChangeMaxGap: Duration = Duration.ofMinutes(5),

    /**
     * Skello encode les jours non travaillés comme un créneau de minuit à minuit.
     * Le seuil est à 23 h pour absorber les journées de changement d'heure (23 h ou 25 h).
     */
    val offDayMinDuration: Duration = Duration.ofHours(23),

    /**
     * Rappeler une coupure méridienne même quand le planning n'en prévoit aucune.
     * La plupart des journées longues étant d'un seul tenant au planning alors que la
     * coupure est bien prise, ce repli est actif par défaut.
     */
    val lunchFallbackEnabled: Boolean = true,
    val lunchFallbackOut: LocalTime = LocalTime.of(12, 0),
    val lunchFallbackIn: LocalTime = LocalTime.of(13, 0),
    /** Durée minimale du bloc pour que le repli se déclenche. */
    val lunchFallbackMinBlock: Duration = Duration.ofHours(6),

    /**
     * Fragments de libellé désignant un créneau de réserve. « EG ou Off » signifie que la
     * présence n'est requise qu'en renfort de dernière minute : le créneau reste visible
     * au planning, mais ne doit pas sonner.
     */
    val standbyPatterns: Set<String> = setOf("ou off"),

    /**
     * Rappel, la veille, de demander au responsable ce qu'il en est. Le planning lui-même
     * porte souvent la consigne « à confirmer la veille » sur ces créneaux.
     */
    val standbyAskEnabled: Boolean = true,

    /**
     * Deux heures selon la disponibilité de la veille : en fin de journée quand on
     * travaille, au milieu de l'après-midi quand on est en repos.
     */
    val askTimeWorking: LocalTime = LocalTime.of(17, 0),
    val askTimeRest: LocalTime = LocalTime.of(14, 0),

    /** Types de service que l'utilisateur a explicitement mis en sourdine. */
    val disabledTypes: Set<String> = emptySet(),

    /**
     * Journées où l'utilisateur a déclaré qu'il travaillerait finalement. Elles rétablissent
     * tous les rappels du jour, quelles que soient les règles de sourdine par ailleurs.
     */
    val workingDates: Set<LocalDate> = emptySet(),
) {
    /** Un créneau produit-il des rappels ? */
    fun notifiesFor(title: String): Boolean {
        if (title in disabledTypes) return false
        val normalized = title.lowercase()
        return standbyPatterns.none { it.isNotBlank() && normalized.contains(it.lowercase()) }
    }

    /** Le libellé désigne-t-il une journée de réserve, avant toute décision manuelle ? */
    fun isStandbyTitle(title: String): Boolean {
        val normalized = title.lowercase()
        return standbyPatterns.any { it.isNotBlank() && normalized.contains(it.lowercase()) }
    }

    /**
     * Le libellé annonce-t-il une alternative — « EG ou Bureau », « EG ou Off » ?
     *
     * Ces journées ont en commun de n'être fixées que la veille. Elles n'ont en revanche
     * pas les mêmes conséquences : « EG ou Bureau » se travaille dans les deux cas et
     * garde donc ses rappels de badgeage, là où « EG ou Off » peut se solder par un repos.
     */
    fun isAlternativeTitle(title: String): Boolean = title.lowercase().contains(" ou ")
}
