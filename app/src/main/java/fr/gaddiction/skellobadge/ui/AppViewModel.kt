package fr.gaddiction.skellobadge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.gaddiction.skellobadge.data.AppSettings
import fr.gaddiction.skellobadge.data.FetchErrors
import fr.gaddiction.skellobadge.data.PlanningRepository
import fr.gaddiction.skellobadge.data.SettingsRepository
import fr.gaddiction.skellobadge.data.UpdateChecker
import fr.gaddiction.skellobadge.data.UpdateInstaller
import fr.gaddiction.skellobadge.data.calendar.DeviceCalendarSource
import fr.gaddiction.skellobadge.data.ics.IcsFetcher
import fr.gaddiction.skellobadge.data.ics.IcsParser
import fr.gaddiction.skellobadge.data.toPlanningConfig
import fr.gaddiction.skellobadge.domain.DayPlan
import fr.gaddiction.skellobadge.domain.PlanningEngine
import fr.gaddiction.skellobadge.notify.Notifications
import fr.gaddiction.skellobadge.notify.UpdateNotification
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

    /** Recherche d'une nouvelle version parmi les releases GitHub. */
    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data class Done(val result: UpdateChecker.Result) : UpdateState
    }

    private val updateChecker = UpdateChecker(application)

    /** Version installée, affichée dans les réglages. */
    val installedVersion: String = updateChecker.installedVersion()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    /**
     * Vérifie s'il existe une version plus récente.
     *
     * Automatiquement, une seule fois par session : l'écran d'accueil s'ouvre souvent, et
     * l'API anonyme de GitHub est limitée. À la demande, depuis les réglages, à chaque fois
     * — et même si la vérification automatique est coupée.
     */
    fun checkForUpdate(manual: Boolean = false) {
        if (!manual && _update.value != UpdateState.Idle) return
        if (_update.value == UpdateState.Checking) return
        viewModelScope.launch {
            if (!manual && !settingsRepository.current().updateCheckEnabled) return@launch
            _update.value = UpdateState.Checking
            val result = updateChecker.check()
            _update.value = UpdateState.Done(result)

            if (result !is UpdateChecker.Result.Failed) {
                // Compte comme la vérification du jour : le travail de fond n'a pas à la
                // refaire dans l'heure.
                settingsRepository.update {
                    it.copy(lastUpdateCheckEpochMillis = System.currentTimeMillis())
                }
            }
            if (result is UpdateChecker.Result.UpToDate) {
                UpdateNotification.cancel(getApplication())
            }
        }
    }

    private val installer = UpdateInstaller(application)

    /** Avancement de l'installation : partagé avec le récepteur qui en reçoit l'issue. */
    val install: StateFlow<UpdateInstaller.State> = UpdateInstaller.state

    /** Écran système où autoriser l'installation des mises à jour. */
    fun installPermissionIntent() = installer.permissionIntent()

    /**
     * Télécharge la version, la vérifie et la confie à Android.
     *
     * Sans l'autorisation d'installer, l'état passe à [UpdateInstaller.State.NeedsPermission]
     * et l'écran ouvre les paramètres ; au retour dans l'application, [resumeInstall]
     * reprend là où l'on s'était arrêté, sans qu'il faille retoucher le bouton.
     */
    fun installUpdate(release: UpdateChecker.Release) {
        when (install.value) {
            is UpdateInstaller.State.Downloading, UpdateInstaller.State.Installing -> return
            else -> Unit
        }
        if (!installer.canInstall()) {
            UpdateInstaller.report(UpdateInstaller.State.NeedsPermission(release))
            return
        }
        viewModelScope.launch {
            UpdateInstaller.report(UpdateInstaller.State.Downloading(0f))
            try {
                val apk = installer.download(release) { progress ->
                    UpdateInstaller.report(UpdateInstaller.State.Downloading(progress))
                }
                installer.install(apk)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                UpdateInstaller.report(UpdateInstaller.State.Idle)
                throw cancelled
            } catch (t: Throwable) {
                UpdateInstaller.report(
                    UpdateInstaller.State.Failed(t.message ?: "Téléchargement impossible"),
                )
            }
        }
    }

    /** Retour des paramètres système : l'autorisation vient peut-être d'être accordée. */
    fun resumeInstall() {
        val pending = install.value as? UpdateInstaller.State.NeedsPermission ?: return
        if (installer.canInstall()) installUpdate(pending.release)
    }

    fun resetInstall() {
        UpdateInstaller.report(UpdateInstaller.State.Idle)
    }

    /** « Plus tard » : cette version ne sera plus proposée, la suivante le sera. */
    fun dismissUpdate(version: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(dismissedUpdateVersion = version) }
            UpdateNotification.cancel(getApplication())
        }
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

    /** Éprouve la demande de confirmation et l'ouverture de la messagerie. */
    fun sendConfirmTest() {
        viewModelScope.launch {
            AlarmScheduler(getApplication()).scheduleConfirmTest(settingsRepository.current())
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
            val updated = settingsRepository.update(transform)
            // Le son des rappels vit dans les canaux de notification, pas dans les alarmes
            // posées : il s'applique tout de suite, y compris aux rappels déjà programmés.
            Notifications.applyReminderSound(getApplication(), updated.reminderSound)
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
