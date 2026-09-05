package fr.gaddiction.skellobadge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.PlanningRepository
import fr.gaddiction.skellobadge.data.SettingsRepository
import fr.gaddiction.skellobadge.data.calendar.DeviceCalendarSource
import fr.gaddiction.skellobadge.data.ics.IcsFetcher
import fr.gaddiction.skellobadge.data.ics.IcsParser
import fr.gaddiction.skellobadge.data.toPlanningConfig
import fr.gaddiction.skellobadge.domain.DayPlan
import fr.gaddiction.skellobadge.domain.PlanningEngine
import fr.gaddiction.skellobadge.schedule.AlarmScheduler
import fr.gaddiction.skellobadge.schedule.RefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val planningRepository = PlanningRepository(application)
    private val deviceCalendar = DeviceCalendarSource(application)

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _planning = MutableStateFlow<PlanningRepository.Snapshot?>(null)
    val planning: StateFlow<PlanningRepository.Snapshot?> = _planning.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Résultat de la vérification du lien pendant la configuration initiale. */
    sealed interface LinkCheck {
        data object Idle : LinkCheck
        data object Running : LinkCheck
        data class Ok(val shifts: Int, val daysOff: Int) : LinkCheck
        data class Failed(val message: String) : LinkCheck
    }

    private val _linkCheck = MutableStateFlow<LinkCheck>(LinkCheck.Idle)
    val linkCheck: StateFlow<LinkCheck> = _linkCheck.asStateFlow()

    fun calendars(): List<DeviceCalendarSource.CalendarInfo> = deviceCalendar.calendars()

    fun resetLinkCheck() {
        _linkCheck.value = LinkCheck.Idle
    }

    /** Programme un rappel de démonstration dans quelques secondes. */
    fun sendTestReminder() {
        viewModelScope.launch {
            AlarmScheduler(getApplication()).scheduleTest(settingsRepository.current())
        }
    }

    /**
     * Vérifie le lien avant de valider la configuration.
     *
     * Sans ce contrôle, une adresse erronée ne se manifesterait que par une absence de
     * rappels — c'est-à-dire précisément le symptôme que l'application est censée
     * supprimer, et le plus difficile à remarquer.
     */
    fun verifyIcsLink(url: String) {
        viewModelScope.launch {
            _linkCheck.value = LinkCheck.Running
            val zone = ZoneId.systemDefault()
            val payload = IcsFetcher(getApplication()).load(url)

            _linkCheck.value = when {
                payload.error != null && !payload.fromCache ->
                    LinkCheck.Failed(payload.error)

                payload.body == null ->
                    LinkCheck.Failed("Réponse vide")

                else -> {
                    val events = IcsParser.parse(payload.body, zone).events
                    val config = AppSettings().toPlanningConfig()
                    val days = PlanningEngine.build(events, config)
                    LinkCheck.Ok(
                        shifts = days.filterIsInstance<DayPlan.Work>().sumOf { it.blocks.size },
                        daysOff = days.count { it is DayPlan.Off },
                    )
                }
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            refresh()
        }
    }

    /**
     * Recharge le planning pour l'affichage et demande une replanification des alarmes.
     * Les deux sont volontairement distincts : l'écran doit rester réactif même si le
     * travail de fond est différé par le système.
     */
    fun refresh() {
        viewModelScope.launch {
            val current = settingsRepository.current()
            if (!current.isUsable) {
                _planning.value = null
                return@launch
            }
            _loading.value = true
            try {
                _planning.value = planningRepository.load(current, ZonedDateTime.now())
                RefreshWorker.refreshNow(getApplication())
            } finally {
                _loading.value = false
            }
        }
    }
}
