package fr.gaddiction.skellobadge.data

/**
 * Traduit un échec de récupération du planning en geste de réparation.
 *
 * Un code de retour HTTP décrit ce que le serveur a répondu, jamais ce que l'utilisateur
 * doit corriger. Ces messages disent l'un et l'autre : ce qui ne va pas, et quoi faire.
 * La cause technique reste affichée à part, en petit, pour le diagnostic.
 */
object FetchErrors {

    fun explain(error: String): String {
        val lowered = error.lowercase()
        return when {
            error.contains("404") ->
                "Ce lien ne mène à aucun planning. Vérifie que tu as copié l'adresse " +
                    "entière depuis Skello, jusqu'au .ics final."

            error.contains("401") || error.contains("403") ->
                "Skello refuse ce lien. Il a peut-être été régénéré : reprends-le dans " +
                    "ton compte Skello."

            error.contains("500") || error.contains("502") || error.contains("503") ->
                "Skello rencontre un incident de son côté. Réessaie plus tard."

            lowered.contains("timeout") || lowered.contains("timed out") ->
                "Skello n'a pas répondu à temps. Réessaie dans un instant."

            lowered.contains("unable to resolve host") || lowered.contains("unknownhost") ->
                "Pas de connexion. Vérifie le réseau du téléphone, puis réessaie."

            lowered.contains("icalendar") ->
                "Cette adresse ne renvoie pas un calendrier. Dans Skello, prends le lien " +
                    "d'abonnement au planning, pas celui de la page."

            else -> "Le planning n'a pas pu être lu. Vérifie l'adresse, puis réessaie."
        }
    }
}
