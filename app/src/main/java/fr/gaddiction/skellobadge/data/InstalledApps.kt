package fr.gaddiction.skellobadge.data

import android.content.Context
import android.content.Intent

/**
 * Liste les applications lançables pour que l'utilisateur désigne lui-même la badgeuse.
 *
 * On évite ainsi de coder en dur un nom de paquet Skello, qui pourrait changer, et on se
 * passe de la permission QUERY_ALL_PACKAGES : la déclaration <queries> du manifeste
 * suffit pour une résolution sur MAIN/LAUNCHER.
 */
object InstalledApps {

    data class Entry(val packageName: String, val label: String)

    fun launchable(context: Context): List<Entry> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                Entry(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                )
            }
            .distinctBy(Entry::packageName)
            .sortedBy { it.label.lowercase() }
    }
}
