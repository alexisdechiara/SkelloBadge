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
            prefs[Keys.BREAK_OUT_LEAD] = updated.breakOutLeadMinutes
            prefs[Keys.CLOCK_OUT_LEAD] = updated.clockOutLeadMinutes
            prefs[Keys.SHIFT_CHANGE_GAP] = updated.shiftChangeMaxGapMinutes
            prefs[Keys.WORKING_DATES] = updated.workingStandbyDates
            prefs[Keys.NAG_INTERVAL] = updated.nagIntervalMinutes
            prefs[Keys.NAG_MAX] = updated.nagMaxCount
            prefs[Keys.FULLSCREEN_ENABLED] = updated.fullScreenAlarmEnabled
            prefs[Keys.FULLSCREEN_AFTER] = updated.fullScreenAlarmAfterMinutes
            prefs[Keys.LUNCH_FALLBACK] = updated.lunchFallbackEnabled
            prefs[Keys.LUNCH_START] = updated.lunchStartMinutes
            prefs[Keys.LUNCH_END] = updated.lunchEndMinutes
            prefs[Keys.STANDBY_PATTERNS] = updated.standbyPatterns
            prefs[Keys.STANDBY_ASK] = updated.standbyAskEnabled
            prefs[Keys.ASK_WORKING] = updated.askWorkingMinutes
            prefs[Keys.ASK_REST] = updated.askRestMinutes
            prefs[Keys.CONTACT_NAME] = updated.contactName
            prefs[Keys.CONTACT_NUMBER] = updated.contactNumber
            prefs[Keys.DISABLED_TYPES] = updated.disabledShiftTypes
            prefs[Keys.KNOWN_TYPES] = updated.knownShiftTypes
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
            breakOutLeadMinutes = prefs[Keys.BREAK_OUT_LEAD] ?: defaults.breakOutLeadMinutes,
            clockOutLeadMinutes = prefs[Keys.CLOCK_OUT_LEAD] ?: defaults.clockOutLeadMinutes,
            workingStandbyDates = prefs[Keys.WORKING_DATES] ?: defaults.workingStandbyDates,
            shiftChangeMaxGapMinutes = prefs[Keys.SHIFT_CHANGE_GAP]
                ?: defaults.shiftChangeMaxGapMinutes,
            nagIntervalMinutes = prefs[Keys.NAG_INTERVAL] ?: defaults.nagIntervalMinutes,
            nagMaxCount = prefs[Keys.NAG_MAX] ?: defaults.nagMaxCount,
            fullScreenAlarmEnabled = prefs[Keys.FULLSCREEN_ENABLED]
                ?: defaults.fullScreenAlarmEnabled,
            fullScreenAlarmAfterMinutes = prefs[Keys.FULLSCREEN_AFTER]
                ?: defaults.fullScreenAlarmAfterMinutes,
            lunchFallbackEnabled = prefs[Keys.LUNCH_FALLBACK] ?: defaults.lunchFallbackEnabled,
            lunchStartMinutes = prefs[Keys.LUNCH_START] ?: defaults.lunchStartMinutes,
            lunchEndMinutes = prefs[Keys.LUNCH_END] ?: defaults.lunchEndMinutes,
            standbyPatterns = prefs[Keys.STANDBY_PATTERNS] ?: defaults.standbyPatterns,
            standbyAskEnabled = prefs[Keys.STANDBY_ASK] ?: defaults.standbyAskEnabled,
            askWorkingMinutes = prefs[Keys.ASK_WORKING] ?: defaults.askWorkingMinutes,
            askRestMinutes = prefs[Keys.ASK_REST] ?: defaults.askRestMinutes,
            contactName = prefs[Keys.CONTACT_NAME] ?: defaults.contactName,
            contactNumber = prefs[Keys.CONTACT_NUMBER] ?: defaults.contactNumber,
            disabledShiftTypes = prefs[Keys.DISABLED_TYPES] ?: defaults.disabledShiftTypes,
            knownShiftTypes = prefs[Keys.KNOWN_TYPES] ?: defaults.knownShiftTypes,
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
        val BREAK_OUT_LEAD = intPreferencesKey("break_out_lead")
        val CLOCK_OUT_LEAD = intPreferencesKey("clock_out_lead")
        val WORKING_DATES = stringSetPreferencesKey("working_standby_dates")
        val SHIFT_CHANGE_GAP = intPreferencesKey("shift_change_gap")
        val NAG_INTERVAL = intPreferencesKey("nag_interval")
        val NAG_MAX = intPreferencesKey("nag_max")
        val FULLSCREEN_ENABLED = booleanPreferencesKey("fullscreen_enabled")
        val FULLSCREEN_AFTER = intPreferencesKey("fullscreen_after")
        val LUNCH_FALLBACK = booleanPreferencesKey("lunch_fallback")
        val LUNCH_START = intPreferencesKey("lunch_start_minutes")
        val LUNCH_END = intPreferencesKey("lunch_end_minutes")
        val STANDBY_PATTERNS = stringSetPreferencesKey("standby_patterns")
        val STANDBY_ASK = booleanPreferencesKey("standby_ask")
        val ASK_WORKING = intPreferencesKey("ask_working_minutes")
        val ASK_REST = intPreferencesKey("ask_rest_minutes")
        val CONTACT_NAME = stringPreferencesKey("contact_name")
        val CONTACT_NUMBER = stringPreferencesKey("contact_number")
        val DISABLED_TYPES = stringSetPreferencesKey("disabled_types")
        val KNOWN_TYPES = stringSetPreferencesKey("known_types")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
    }
}
