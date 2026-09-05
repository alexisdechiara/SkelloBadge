package fr.gaddiction.skellobadge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Toute la configuration de l'application tient ici. Aucun compte, aucun serveur :
 * une poignée de préférences locales, écrites une fois pendant l'installation guidée.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map(::decode)

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(current())
        context.dataStore.edit { prefs ->
            prefs[Keys.CONFIGURED] = updated.configured
            prefs[Keys.SOURCE] = updated.source.name
            prefs[Keys.ICS_URL] = updated.icsUrl
            prefs[Keys.CALENDAR_IDS] = updated.calendarIds.map(Long::toString).toSet()
            prefs[Keys.TARGET_KIND] = updated.targetKind.name
            prefs[Keys.TARGET_PACKAGE] = updated.targetPackage
            prefs[Keys.TARGET_LABEL] = updated.targetLabel
            prefs[Keys.TARGET_URL] = updated.targetUrl
            prefs[Keys.CLOCK_IN_LEAD] = updated.clockInLeadMinutes
            prefs[Keys.BREAK_IN_LEAD] = updated.breakInLeadMinutes
            prefs[Keys.SHIFT_CHANGE_GAP] = updated.shiftChangeMaxGapMinutes
            prefs[Keys.NAG_AFTER] = updated.nagAfterMinutes
            prefs[Keys.LUNCH_FALLBACK] = updated.lunchFallbackEnabled
            prefs[Keys.LAST_SYNC] = updated.lastSyncEpochMillis
            prefs[Keys.LAST_SYNC_ERROR] = updated.lastSyncError
        }
    }

    private fun decode(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            configured = prefs[Keys.CONFIGURED] ?: defaults.configured,
            source = prefs[Keys.SOURCE]
                ?.let { name -> PlanningSource.entries.firstOrNull { it.name == name } }
                ?: defaults.source,
            icsUrl = prefs[Keys.ICS_URL] ?: defaults.icsUrl,
            calendarIds = prefs[Keys.CALENDAR_IDS]
                ?.mapNotNull(String::toLongOrNull)
                ?.toSet()
                ?: defaults.calendarIds,
            targetKind = prefs[Keys.TARGET_KIND]
                ?.let { name -> BadgeTarget.entries.firstOrNull { it.name == name } }
                ?: defaults.targetKind,
            targetPackage = prefs[Keys.TARGET_PACKAGE] ?: defaults.targetPackage,
            targetLabel = prefs[Keys.TARGET_LABEL] ?: defaults.targetLabel,
            targetUrl = prefs[Keys.TARGET_URL] ?: defaults.targetUrl,
            clockInLeadMinutes = prefs[Keys.CLOCK_IN_LEAD] ?: defaults.clockInLeadMinutes,
            breakInLeadMinutes = prefs[Keys.BREAK_IN_LEAD] ?: defaults.breakInLeadMinutes,
            shiftChangeMaxGapMinutes = prefs[Keys.SHIFT_CHANGE_GAP] ?: defaults.shiftChangeMaxGapMinutes,
            nagAfterMinutes = prefs[Keys.NAG_AFTER] ?: defaults.nagAfterMinutes,
            lunchFallbackEnabled = prefs[Keys.LUNCH_FALLBACK] ?: defaults.lunchFallbackEnabled,
            lastSyncEpochMillis = prefs[Keys.LAST_SYNC] ?: defaults.lastSyncEpochMillis,
            lastSyncError = prefs[Keys.LAST_SYNC_ERROR] ?: defaults.lastSyncError,
        )
    }

    private object Keys {
        val CONFIGURED = booleanPreferencesKey("configured")
        val SOURCE = stringPreferencesKey("source")
        val ICS_URL = stringPreferencesKey("ics_url")
        val CALENDAR_IDS = stringSetPreferencesKey("calendar_ids")
        val TARGET_KIND = stringPreferencesKey("target_kind")
        val TARGET_PACKAGE = stringPreferencesKey("target_package")
        val TARGET_LABEL = stringPreferencesKey("target_label")
        val TARGET_URL = stringPreferencesKey("target_url")
        val CLOCK_IN_LEAD = intPreferencesKey("clock_in_lead")
        val BREAK_IN_LEAD = intPreferencesKey("break_in_lead")
        val SHIFT_CHANGE_GAP = intPreferencesKey("shift_change_gap")
        val NAG_AFTER = intPreferencesKey("nag_after")
        val LUNCH_FALLBACK = booleanPreferencesKey("lunch_fallback")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
    }
}
