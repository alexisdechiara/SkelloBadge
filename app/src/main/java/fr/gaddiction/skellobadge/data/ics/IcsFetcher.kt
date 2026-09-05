package fr.gaddiction.skellobadge.data.ics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Récupère le flux ICS et le conserve sur le disque.
 *
 * Le cache n'est pas une optimisation mais une garantie de fonctionnement : sans réseau
 * au moment de la replanification, on doit continuer à poser les alarmes du planning
 * déjà connu plutôt que de laisser passer une prise de poste.
 */
class IcsFetcher(private val context: Context) {

    data class Payload(
        val body: String?,
        val fromCache: Boolean,
        val error: String? = null,
    )

    private val cacheFile: File get() = File(context.filesDir, CACHE_NAME)

    suspend fun load(url: String): Payload = withContext(Dispatchers.IO) {
        try {
            val body = download(url)
            if (!body.contains("BEGIN:VCALENDAR")) {
                error("La réponse n'est pas un calendrier iCalendar")
            }
            cacheFile.writeText(body)
            Payload(body = body, fromCache = false)
        } catch (t: Throwable) {
            val cached = runCatching { cacheFile.takeIf(File::exists)?.readText() }.getOrNull()
            Payload(
                body = cached,
                fromCache = cached != null,
                error = t.message ?: t.javaClass.simpleName,
            )
        }
    }

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/calendar, text/plain;q=0.8")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CACHE_NAME = "planning.ics"
        const val TIMEOUT_MS = 20_000
        const val USER_AGENT = "SkelloBadge/1.0 (Android)"
    }
}
