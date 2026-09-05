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

/** Ce que devient une date une fois le planning interprété. */
sealed interface DayPlan {
    val date: LocalDate

    /** Jour non travaillé : repos hebdomadaire, congé, absence, férié. Silence total. */
    data class Off(override val date: LocalDate, val label: String) : DayPlan

    /** Aucun créneau au planning pour cette date. */
    data class Empty(override val date: LocalDate) : DayPlan

    data class Work(
        override val date: LocalDate,
        val blocks: List<WorkBlock>,
        val reminders: List<Reminder>,
    ) : DayPlan
}

/**
 * Tous les réglages qui influencent le calcul. Les valeurs par défaut sont celles
 * calibrées sur un planning Skello réel.
 */
data class PlanningConfig(
    /** Combien de temps avant la prise de poste on prévient. */
    val clockInLead: Duration = Duration.ofMinutes(5),
    /** Combien de temps avant le retour de pause on prévient. */
    val breakInLead: Duration = Duration.ofMinutes(5),
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
     * Repli optionnel : rappeler une coupure méridienne même quand le planning n'en
     * prévoit aucune. Désactivé par défaut — sur un planning Skello réel, la majorité
     * des journées longues sont d'un seul tenant et ce repli produirait surtout du bruit.
     */
    val lunchFallbackEnabled: Boolean = false,
    val lunchFallbackOut: LocalTime = LocalTime.of(12, 0),
    val lunchFallbackIn: LocalTime = LocalTime.of(13, 0),
    /** Durée minimale du bloc pour que le repli se déclenche. */
    val lunchFallbackMinBlock: Duration = Duration.ofHours(6),
)
