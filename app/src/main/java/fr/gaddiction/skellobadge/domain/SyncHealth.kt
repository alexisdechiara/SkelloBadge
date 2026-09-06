package fr.gaddiction.skellobadge.domain

import java.time.Duration

/**
 * Depuis quand le planning n'a plus été réellement récupéré.
 *
 * L'application se rabat sur sa dernière copie dès qu'une récupération échoue, et
 * continue de sonner dessus : mieux vaut des rappels un peu datés que plus de rappels du
 * tout. Le revers est qu'un flux définitivement mort — jeton régénéré, compte modifié —
 * ne se voit jamais de l'extérieur, puisque tout continue comme avant.
 *
 * D'où ce constat, tenu à part du reste : il ne dit pas qu'une récupération a échoué,
 * mais qu'aucune n'a réussi depuis assez longtemps pour que le planning affiché ait
 * cessé d'être crédible.
 */
object SyncHealth {

    /**
     * Vingt-quatre heures. En dessous, on absorbe en silence tout ce qui relève du réseau
     * ordinaire : une nuit en mode avion, un week-end là où ça capte mal. Au-delà, ce
     * n'est plus une coupure, et le planning d'hier a déjà pu changer.
     */
    val STALE_AFTER: Duration = Duration.ofHours(24)

    /**
     * Ancienneté du planning si elle dépasse le seuil, sinon `null`.
     *
     * Une valeur nulle de [lastSuccessEpochMillis] signifie qu'aucune récupération n'a
     * jamais abouti : c'est une configuration qui n'a pas encore fonctionné, pas un flux
     * qui se serait tari, et l'installation guidée l'a déjà signalé à sa manière.
     */
    fun staleFor(lastSuccessEpochMillis: Long, nowEpochMillis: Long): Duration? {
        if (lastSuccessEpochMillis <= 0L) return null
        val age = Duration.ofMillis(nowEpochMillis - lastSuccessEpochMillis)
        return age.takeIf { it >= STALE_AFTER }
    }

    /** « 1 jour », « 3 jours » : le seuil étant d'un jour, l'unité ne descend pas plus bas. */
    fun describe(age: Duration): String {
        val days = age.toDays().coerceAtLeast(1)
        return days.toString() + (if (days > 1) " jours" else " jour")
    }
}
