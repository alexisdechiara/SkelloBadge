# Plan 002 : Ne plus recharger le planning ni replanifier à chaque frappe dans les réglages

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat e862b61..HEAD -- app/src/main/java/fr/gaddiction/skellobadge/ui/AppViewModel.kt app/src/main/java/fr/gaddiction/skellobadge/ui/settings/SettingsScreen.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/001-settings-update-atomique.md
- **Category**: perf
- **Planned at**: commit `e862b61`, 2026-11-18

## Why this matters

Chaque appel à `AppViewModel.update` enregistre les réglages, recharge le planning
**par le réseau** (`PlanningRepository.load` → `IcsFetcher.load`), puis relance le
`RefreshWorker`, qui le télécharge **une seconde fois** et repose toutes les alarmes.
Or les champs « Titre » et « Texte » des rappels appellent `update` à **chaque
caractère**. Taper 20 caractères = ~40 requêtes HTTP, 20 réécritures des réglages,
20 replanifications.

Effet visible en plus : le champ texte affiche la valeur relue depuis DataStore, qui
arrive en différé. En tapant vite, des caractères disparaissent ou le curseur saute.

Cerise : chaque replanification annule aussi les relances d'un rappel en cours (bug
connu, traité à part). Moins de replanifications = moins d'exposition à ce bug.

## Current state

- `app/src/main/java/fr/gaddiction/skellobadge/ui/AppViewModel.kt` — ViewModel unique de l'appli.
- `app/src/main/java/fr/gaddiction/skellobadge/ui/settings/SettingsScreen.kt` — écran des réglages.

`AppViewModel.kt:263-293` :

```kotlin
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
     * …
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
```

`refresh()` est aussi appelé par `MainActivity.onStart()` (`MainActivity.kt:54`) : ce
chemin-là **ne change pas**.

`SettingsScreen.kt:992-1043`, l'éditeur de formulations, lit la valeur dans les réglages
et écrit à chaque frappe :

```kotlin
@Composable
private fun WordingEditor(
    settings: AppSettings,
    haptics: Haptics,
    onReset: () -> Unit,
    onChange: (ReminderKind, Wording) -> Unit,
) {
    var selected by remember { mutableStateOf(ReminderKind.CLOCK_IN) }
    val wording = settings.wordingFor(selected)
    // … puces de choix du type (FilterChip, onClick = { haptics.click(); selected = kind }) …
        OutlinedTextField(
            value = wording.title,
            onValueChange = { onChange(selected, wording.copy(title = it)) },
            …
        OutlinedTextField(
            value = wording.body,
            onValueChange = { onChange(selected, wording.copy(body = it)) },
```

Exemple à suivre dans le même fichier : `KeywordsField` (`SettingsScreen.kt:869-895`)
garde déjà son texte dans un état local (`var text by remember { … }`) — son KDoc
explique pourquoi. Il appelle néanmoins `onChange` à chaque frappe ; il bénéficiera du
regroupement de l'étape 1 sans autre changement.

Conventions : commentaires en français, qui disent le pourquoi. Constantes
`private const val` en bas de classe / de fichier (voir `RefreshWorker.kt:99-108`).

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Tests unitaires | `./gradlew testDebugUnitTest` | `BUILD SUCCESSFUL` |
| Compilation APK | `./gradlew assembleDebug` | `BUILD SUCCESSFUL` |

Prérequis : SDK Android avec `platforms;android-37.0`. Pas de test d'UI dans le dépôt.

## Scope

**In scope** :
- `app/src/main/java/fr/gaddiction/skellobadge/ui/AppViewModel.kt`
- `app/src/main/java/fr/gaddiction/skellobadge/ui/settings/SettingsScreen.kt` (uniquement `WordingEditor`)

**Out of scope** :
- `refresh()` et son appel depuis `MainActivity.onStart` : l'ouverture de l'appli doit
  continuer à resynchroniser tout de suite.
- `RefreshWorker`, `AlarmScheduler` (le bug « replanifier annule les relances » est un
  autre chantier).
- Requêtes conditionnelles HTTP (ETag) dans `IcsFetcher` : utile, mais hors de ce plan.
- `OnboardingScreen` : son bouton « Terminer » appelle `update` une seule fois, rien à changer.

## Git workflow

- Branche : celle indiquée par l'opérateur, sinon `advisor/002-reglages-par-frappe`.
- Commit en français, 3ᵉ personne du présent, p. ex. « Regroupe les modifications de
  réglages avant de replanifier ». Ne pas pousser sans instruction.

## Steps

### Step 1 : Regrouper les replanifications déclenchées par les réglages

Dans `AppViewModel`, ajouter un champ `private var settingsRefresh: Job? = null`
(import `kotlinx.coroutines.Job` et `kotlinx.coroutines.delay`) et remplacer `update` par :

```kotlin
    /**
     * Enregistre tout de suite, mais ne recharge le planning et ne replanifie qu'une fois
     * les modifications retombées : une série de frappes ou d'appuis sur « + » ne doit
     * coûter qu'un seul téléchargement et une seule pose d'alarmes.
     */
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = settingsRepository.update(transform)
            // Le son des rappels vit dans les canaux de notification, pas dans les alarmes
            // posées : il s'applique tout de suite, y compris aux rappels déjà programmés.
            Notifications.applyReminderSound(getApplication(), updated.reminderSound)
            settingsRefresh?.cancel()
            settingsRefresh = viewModelScope.launch {
                delay(SETTINGS_SETTLE_MILLIS)
                refresh()
            }
        }
    }
```

et une constante dans un `private companion object` de la classe (la créer s'il n'y en a
pas) : `const val SETTINGS_SETTLE_MILLIS = 800L`.

Points d'attention :
- L'écriture (`settingsRepository.update`) reste **immédiate** : seul le rechargement est
  différé. L'écran des réglages lit `viewModel.settings`, il se met donc à jour aussitôt.
- `refresh()` lui-même n'est pas modifié.
- `toggleWorkingDay` passe par `update` : le bouton « Je travaille ce jour » replanifiera
  après 0,8 s au lieu de tout de suite. C'est acceptable.
- Le bouton « Terminer » de l'installation guidée appelle `update` puis l'écran change :
  le ViewModel survit (il appartient à l'activité), la replanification part bien.

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Step 2 : Garder le texte des formulations dans un état local

Dans `WordingEditor`, remplacer la lecture directe par un état local réinitialisé
quand on change de type, et n'enregistrer qu'après une courte pause de frappe :

```kotlin
    var selected by remember { mutableStateOf(ReminderKind.CLOCK_IN) }
    val saved = settings.wordingFor(selected)

    // Le texte vit dans l'écran pendant la frappe : relu depuis les réglages, qui arrivent
    // en différé, il perdait des caractères et faisait sauter le curseur.
    var title by remember(selected) { mutableStateOf(saved.title) }
    var body by remember(selected) { mutableStateOf(saved.body) }

    // Enregistré une fois la frappe retombée, pas à chaque caractère.
    LaunchedEffect(selected, title, body) {
        if (title == saved.title && body == saved.body) return@LaunchedEffect
        delay(WORDING_SETTLE_MILLIS)
        onChange(selected, Wording(title, body))
    }
```

Puis brancher les deux `OutlinedTextField` sur `title` / `body`
(`value = title, onValueChange = { title = it }` et idem pour `body`).

Ne pas perdre la dernière frappe en changeant de type : dans le `onClick` des
`FilterChip`, **avant** `selected = kind`, enregistrer si besoin :

```kotlin
onClick = {
    haptics.click()
    if (title != saved.title || body != saved.body) onChange(selected, Wording(title, body))
    selected = kind
},
```

Le bouton « Rétablir les textes par défaut » (`onReset`) remplace les réglages : il faut
que les champs affichent ensuite les défauts. Ajouter `settings.wording` aux clés des deux
`remember` : `remember(selected, settings.wording == Wording.DEFAULTS)`. Ainsi l'état
local se réinitialise au passage aux défauts, mais pas à chaque enregistrement ordinaire.

Ajouter en bas du fichier `private const val WORDING_SETTLE_MILLIS = 600L`, et les imports
`androidx.compose.runtime.LaunchedEffect` et `kotlinx.coroutines.delay` s'ils manquent.

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL` sans nouvel avertissement
dans `SettingsScreen.kt` (`./gradlew assembleDebug 2>&1 | grep "SettingsScreen.kt" | grep "^w:"` → vide).

### Step 3 : Suite complète

**Verify** : `./gradlew testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`.

## Test plan

Pas de test automatisé : il n'existe aucune infrastructure de test Compose ou de
ViewModel dans le dépôt, et en créer une est hors de ce plan. Vérification manuelle à
consigner dans le rapport, sur téléphone ou émulateur :
1. Réglages → Texte des rappels → taper vite « Badge vite ton entrée » dans Titre : aucun
   caractère perdu, curseur stable.
2. Changer de type puis revenir : le texte saisi est conservé.
3. « Rétablir les textes par défaut » : les champs affichent les défauts.
4. `adb logcat -s RefreshWorker` pendant la frappe : **une** ligne « Replanification
   terminee » environ une seconde après la dernière frappe, pas une par caractère.

## Done criteria

- [ ] `./gradlew testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`
- [ ] `grep -n "onValueChange = { onChange(selected" app/src/main/java/fr/gaddiction/skellobadge/ui/settings/SettingsScreen.kt` → aucune ligne
- [ ] `grep -n "SETTINGS_SETTLE_MILLIS" app/src/main/java/fr/gaddiction/skellobadge/ui/AppViewModel.kt` → au moins 2 lignes
- [ ] `git status --short` ne montre que les 2 fichiers du scope (plus `plans/README.md`)
- [ ] Vérification manuelle faite, ou explicitement signalée comme non faite dans le rapport
- [ ] Ligne du plan 002 mise à jour dans `plans/README.md`

## STOP conditions

- Le plan 001 n'est pas fait (`SettingsRepository.update` contient encore
  `transform(current())`) : STOP. Sans lui, les écritures rapprochées de l'étape 2
  peuvent s'écraser entre elles.
- `AppViewModel.update` a changé de forme depuis l'extrait ci-dessus.
- Un autre écran que les réglages dépend d'une replanification **immédiate** après
  `update` (chercher `viewModel.update` et `.update {` dans `ui/`) — p. ex. un écran qui
  lit `planning` juste après : STOP et signaler.

## Maintenance notes

- Toute nouvelle action qui doit replanifier **sans délai** (un bouton « Replanifier
  maintenant ») doit appeler `refresh()` directement, pas `update`.
- Le délai de 800 ms ne doit pas descendre sous celui de l'éditeur (600 ms), sans quoi
  la dernière frappe déclenche deux replanifications.
- Suite naturelle, non incluse : requêtes conditionnelles (`If-None-Match`) dans
  `IcsFetcher`, et rechargement depuis le cache pour l'affichage après un réglage.
