package fr.gaddiction.skellobadge.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/**
 * Choix du responsable à contacter, et ouverture de la messagerie sur son numéro.
 *
 * Le sélecteur système est préféré à une lecture du carnet d'adresses : il accorde à
 * l'application un accès temporaire au seul contact choisi, ce qui évite de réclamer la
 * permission de lire l'ensemble des contacts pour n'en retenir qu'un.
 */
object Contacts {

    data class Entry(val name: String, val number: String)

    fun pickIntent(): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    fun read(context: Context, uri: Uri): Entry? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            Entry(
                name = cursor.getString(0).orEmpty(),
                number = cursor.getString(1).orEmpty(),
            )
        }
    }.getOrNull()

    /** Ouvre l'application de messagerie par défaut sur une conversation avec ce numéro. */
    fun messageIntent(number: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number))
}
