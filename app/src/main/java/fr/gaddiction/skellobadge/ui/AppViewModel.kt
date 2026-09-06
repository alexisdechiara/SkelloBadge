package fr.gaddiction.skellobadge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.FetchErrors
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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

    /**
     * Les types de service rencontrés dans le planning, pour que l'utilisateur puisse en
     * mettre certains en sourdine. La liste est déduite du flux plutôt que saisie à la
     * main : elle suit donc automatiquement les libellés que l'établissement utilise.
     */
    val shiftTypes: StateFlow<List<String>> = planning
        .map { snapshot ->
            snapshot?.days.orEmpty()
                .filterIsInstance<DayPlan.Work>()
                .flatMap { it.blocks }
                .map { it.title }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Résultat de la vérification du lien pendant la configuration initiale. */
    sealed interface LinkCheck {
        data object Idle : LinkCheck
        data object Running : LinkCheck
        data class Ok(val shifts: Int, val daysOff: Int) : LinkCheck

        /**
         * [message] dit quoi faire ; [detail] conserve la cause technique, reléguée en
         * petit. Un code HTTP seul n'apprend rien à qui vient de coller une adresse.
         */
        data class Failed(val message: String, val detail: String) : LinkCheck
    }

    private val _linkCheck = MutableStateFlow<LinkCheck>(LinkCheck.Idle)
    val linkCheck: StateFlow<LinkCheck> = _linkCheck.asStateFlow()

    fun calendars(): List<DeviceCalendarSource.CalendarInfo> = deviceCalendar.calendars()

    fun resetLinkCheck() {
        _linkCheck.value = LinkCheck.Idle
    }

    /**
     * Bascule une journée de réserve en journée travaillée, et inversement.
     *
     * C'est le geste qui rattrape le cas le plus risqué : le jour où l'on est appelé en
     * renfort sur un créneau qui, par défaut, ne sonne pas.
     */
    fun toggleWorkingDay(date: LocalDate) {
        val key = date.toString()
        update { settings ->
            val dates = settings.workingStandbyDates.toMutableSet()
            if (key in dates) dates -= key else dates += key
            settings.copy(workingStandbyDates = dates)
        }
    }

    /** Programme un rappel de démonstration dans quelques secondes. */
    fun sendTestReminder() {
        viewModelScope.launch {
            AlarmScheduler(getApplication()).scheduleTest(settingsRepository.current())
        }
    }

    /** Même chose, mais en déclenchant directement l'escalade plein écran. */
    fun sendFullScreenTest() {
        viewModelScope.launch {
            AlarmScheduler(getApplication()).scheduleFullScreenTest(settingsRepository.current())
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
                    LinkCheck.Failed(FetchErrors.explain(payload.error), payload.error)

                payload.body == null ->
                    LinkCheck.Failed(
                        "Skello a répondu, mais sans planning. Vérifie que l'adresse est " +
                            "bien celle de l'abonnement au calendrier.",
                        "réponse vide",
                    )

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
