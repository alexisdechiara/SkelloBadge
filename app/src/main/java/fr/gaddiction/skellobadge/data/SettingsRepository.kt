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
import fr.gaddiction.skellobadge.domain.ReminderKind
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
            prefs[Keys.NAG_INTERVAL] = updated.nagIntervalMinutes
            prefs[Keys.NAG_MAX] = updated.nagMaxCount
            prefs[Keys.FULLSCREEN_ENABLED] = updated.fullScreenAlarmEnabled
            prefs[Keys.FULLSCREEN_AFTER] = updated.fullScreenAlarmAfterMinutes
            prefs[Keys.LUNCH_FALLBACK] = updated.lunchFallbackEnabled
            prefs[Keys.STANDBY_PATTERNS] = updated.standbyPatterns
            prefs[Keys.DISABLED_TYPES] = updated.disabledShiftTypes
            prefs[Keys.LAST_SYNC] = updated.lastSyncEpochMillis
            prefs[Keys.LAST_SYNC_ERROR] = updated.lastSyncError

            ReminderKind.entries.forEach { kind ->
                val wording = updated.wordingFor(kind)
                prefs[wordingTitleKey(kind)] = wording.title
                prefs[wordingBodyKey(kind)] = wording.body
            }
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
            shiftChangeMaxGapMinutes = prefs[Keys.SHIFT_CHANGE_GAP]
                ?: defaults.shiftChangeMaxGapMinutes,
            nagIntervalMinutes = prefs[Keys.NAG_INTERVAL] ?: defaults.nagIntervalMinutes,
            nagMaxCount = prefs[Keys.NAG_MAX] ?: defaults.nagMaxCount,
            fullScreenAlarmEnabled = prefs[Keys.FULLSCREEN_ENABLED]
                ?: defaults.fullScreenAlarmEnabled,
            fullScreenAlarmAfterMinutes = prefs[Keys.FULLSCREEN_AFTER]
                ?: defaults.fullScreenAlarmAfterMinutes,
            lunchFallbackEnabled = prefs[Keys.LUNCH_FALLBACK] ?: defaults.lunchFallbackEnabled,
            standbyPatterns = prefs[Keys.STANDBY_PATTERNS] ?: defaults.standbyPatterns,
            disabledShiftTypes = prefs[Keys.DISABLED_TYPES] ?: defaults.disabledShiftTypes,
            wording = ReminderKind.entries.associateWith { kind ->
                val fallback = Wording.DEFAULTS.getValue(kind)
                Wording(
                    title = prefs[wordingTitleKey(kind)] ?: fallback.title,
                    body = prefs[wordingBodyKey(kind)] ?: fallback.body,
                )
            },
            lastSyncEpochMillis = prefs[Keys.LAST_SYNC] ?: defaults.lastSyncEpochMillis,
            lastSyncError = prefs[Keys.LAST_SYNC_ERROR] ?: defaults.lastSyncError,
        )
    }

    private fun wordingTitleKey(kind: ReminderKind) =
        stringPreferencesKey("wording_title_" + kind.name)

    private fun wordingBodyKey(kind: ReminderKind) =
        stringPreferencesKey("wording_body_" + kind.name)

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
        val NAG_INTERVAL = intPreferencesKey("nag_interval")
        val NAG_MAX = intPreferencesKey("nag_max")
        val FULLSCREEN_ENABLED = booleanPreferencesKey("fullscreen_enabled")
        val FULLSCREEN_AFTER = intPreferencesKey("fullscreen_after")
        val LUNCH_FALLBACK = booleanPreferencesKey("lunch_fallback")
        val STANDBY_PATTERNS = stringSetPreferencesKey("standby_patterns")
        val DISABLED_TYPES = stringSetPreferencesKey("disabled_types")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
    }
}
