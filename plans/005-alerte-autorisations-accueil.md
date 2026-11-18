# Plan 005 : Signaler sur l'accueil une autorisation retirée qui empêcherait les rappels

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat e862b61..HEAD -- app/src/main/java/fr/gaddiction/skellobadge/ui/home/HomeScreen.kt app/src/main/java/fr/gaddiction/skellobadge/notify/Notifications.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition. (`ui/Permissions.kt` **doit** exister : il est créé par le plan 003.)

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/003-onboarding-relit-les-autorisations.md (crée `ui/Permissions.kt` et `rememberResumeCount`)
- **Category**: bug (UX)
- **Planned at**: commit `e862b61`, 2026-11-18

## Why this matters

L'accueil se présente comme l'écran qui sert à « vérifier d'un coup d'œil que les rappels
sont bien armés » (KDoc de `HomeScreen`). Pourtant, si les notifications sont bloquées,
si un type de rappel est coupé dans les paramètres Android, si l'alarme plein écran est
refusée (Android 14+ peut la retirer), ou si un nettoyeur constructeur a remis
l'économie de batterie, l'accueil affiche toujours « Prochain rappel : dans 2 h ». Rien
ne sonnera. `ReminderNotification.post` abandonne même en silence quand les notifications
sont bloquées. Ces autorisations ne sont vérifiées qu'une fois, pendant l'installation guidée.

C'est exactement la panne que l'application promet d'éviter : une absence de rappel
qu'on ne remarque pas.

## Current state

- `app/src/main/java/fr/gaddiction/skellobadge/ui/home/HomeScreen.kt` — accueil.
- `app/src/main/java/fr/gaddiction/skellobadge/notify/Notifications.kt` — canaux de notification.
- `app/src/main/java/fr/gaddiction/skellobadge/ui/Permissions.kt` — créé par le plan 003 ;
  contient `internal fun Context.isIgnoringBatteryOptimizations()`,
  `requestIgnoreBatteryOptimizations()`, `canUseFullScreenIntent()` (API 34+),
  `requestFullScreenIntent()` (API 34+) et `@Composable internal fun rememberResumeCount(): Int`.

`HomeScreen.kt:80-120` (début de l'écran) :

```kotlin
fun HomeScreen(viewModel: AppViewModel, settings: AppSettings, onOpenSettings: () -> Unit) {
    val planning by viewModel.planning.collectAsStateWithLifecycle()
    …
    val context = LocalContext.current
    …
            LazyColumn(…) {
                item { NextReminderCard(upcoming.firstOrNull(), now) }

                planning?.error?.takeIf(String::isNotBlank)?.let { error ->
                    item { SyncWarningCard(error, planning?.fromCache == true) }
                }
```

Carte d'alerte existante à imiter pour le style, `HomeScreen.kt:345-383` :

```kotlin
@Composable
private fun SyncWarningCard(error: String, fromCache: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(…, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Text(…, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer)
```

`Notifications.kt` (état à `e862b61`) : `fun canPost(context): Boolean` (public) ;
`fun channelFor(context, kind)` (public) ; privés : `REMINDER_CHANNELS` (liste de
`ReminderChannel(base, nameRes, descriptionRes, importance)`), `reminderChannelId(base, generation)`,
`generation(context)`. Les canaux de rappel ont des identifiants versionnés `"<base>_v<N>"`.

Logique pure testable : le dépôt met ce genre de règle dans `domain/` (p. ex.
`domain/SyncHealth.kt`, testé par `app/src/test/java/fr/gaddiction/skellobadge/SyncHealthTest.kt` —
modèle à suivre). Commentaires en français ; libellés au tutoiement.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Un seul test | `./gradlew testDebugUnitTest --tests '*ReminderHealthTest*'` | `BUILD SUCCESSFUL` |
| Tests unitaires | `./gradlew testDebugUnitTest` | `BUILD SUCCESSFUL` |
| Compilation APK | `./gradlew assembleDebug` | `BUILD SUCCESSFUL` |

## Scope

**In scope** :
- `app/src/main/java/fr/gaddiction/skellobadge/domain/ReminderHealth.kt` (à créer)
- `app/src/test/java/fr/gaddiction/skellobadge/ReminderHealthTest.kt` (à créer)
- `app/src/main/java/fr/gaddiction/skellobadge/notify/Notifications.kt` (ajout d'une fonction publique, rien d'autre)
- `app/src/main/java/fr/gaddiction/skellobadge/ui/Permissions.kt` (ajout d'une fonction)
- `app/src/main/java/fr/gaddiction/skellobadge/ui/home/HomeScreen.kt`

**Out of scope** :
- Toute notification système ou travail de fond qui préviendrait hors de l'appli (idée
  ultérieure : la faire porter par le `RefreshWorker`).
- L'installation guidée (plan 003), les Réglages.
- La logique de création des canaux dans `Notifications.kt`.

## Git workflow

- Branche : celle indiquée par l'opérateur, sinon `advisor/005-alerte-autorisations`.
- Commit en français, 3ᵉ personne du présent, p. ex. « Signale sur l'accueil une
  autorisation qui empêcherait les rappels ». Ne pas pousser sans instruction.

## Steps

### Step 1 : Règle pure `ReminderHealth`

Créer `domain/ReminderHealth.kt` :

```kotlin
package fr.gaddiction.skellobadge.domain

/** Ce qui, côté téléphone, empêcherait un rappel de se faire remarquer. */
enum class HealthIssue {
    /** Notifications bloquées pour l'application : aucun rappel ne s'affiche. */
    NOTIFICATIONS_BLOCKED,

    /** Au moins un type de rappel coupé dans les paramètres de notification d'Android. */
    REMINDERS_MUTED,

    /** Alarme plein écran activée dans l'appli, mais refusée par Android. */
    FULL_SCREEN_DENIED,

    /** Économie de batterie active : les alarmes peuvent être retardées en veille. */
    BATTERY_RESTRICTED,
}

object ReminderHealth {
    /**
     * Du plus grave au moins grave. Notifications bloquées, les canaux coupés n'y
     * changent plus rien : on ne le signale pas en plus.
     */
    fun issues(
        notificationsAllowed: Boolean,
        reminderChannelsMuted: Boolean,
        fullScreenWanted: Boolean,
        fullScreenAllowed: Boolean,
        batteryExempt: Boolean,
    ): List<HealthIssue> = buildList {
        if (!notificationsAllowed) add(HealthIssue.NOTIFICATIONS_BLOCKED)
        else if (reminderChannelsMuted) add(HealthIssue.REMINDERS_MUTED)
        if (fullScreenWanted && !fullScreenAllowed) add(HealthIssue.FULL_SCREEN_DENIED)
        if (!batteryExempt) add(HealthIssue.BATTERY_RESTRICTED)
    }
}
```

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Step 2 : Tests

Créer `ReminderHealthTest.kt` sur le modèle de `SyncHealthTest.kt`. Cas :
1. tout accordé → liste vide ;
2. notifications bloquées **et** canaux coupés → uniquement `NOTIFICATIONS_BLOCKED` ;
3. canaux coupés seuls → `REMINDERS_MUTED` ;
4. plein écran refusé mais désactivé dans l'appli (`fullScreenWanted = false`) → rien ;
5. plein écran voulu et refusé → `FULL_SCREEN_DENIED` ;
6. tout refusé → `[NOTIFICATIONS_BLOCKED, FULL_SCREEN_DENIED, BATTERY_RESTRICTED]` dans cet ordre.

**Verify** : `./gradlew testDebugUnitTest --tests '*ReminderHealthTest*'` → `BUILD SUCCESSFUL`, 6 tests.

### Step 3 : Lire l'état des canaux de rappel

Dans `Notifications.kt`, ajouter après `canPost` :

```kotlin
    /** Vrai si l'un des canaux de rappel en vigueur a été coupé dans les paramètres Android. */
    fun reminderChannelsMuted(context: Context): Boolean {
        val manager = context.getSystemService<NotificationManager>() ?: return false
        val generation = generation(context)
        return REMINDER_CHANNELS.any { spec ->
            manager.getNotificationChannel(reminderChannelId(spec.base, generation))
                ?.importance == NotificationManager.IMPORTANCE_NONE
        }
    }
```

Dans `ui/Permissions.kt`, ajouter :

```kotlin
/** Page des notifications de l'application dans les paramètres Android. */
internal fun Context.openNotificationSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
```

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Step 4 : Carte sur l'accueil

Dans `HomeScreen` :

```kotlin
    // Relu à chaque retour dans l'application : une autorisation se retire hors d'elle.
    val resumes = rememberResumeCount()
    val issues = remember(resumes, settings.fullScreenAlarmEnabled) {
        ReminderHealth.issues(
            notificationsAllowed = Notifications.canPost(context),
            reminderChannelsMuted = Notifications.reminderChannelsMuted(context),
            fullScreenWanted = settings.fullScreenAlarmEnabled,
            fullScreenAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.canUseFullScreenIntent(),
            batteryExempt = context.isIgnoringBatteryOptimizations(),
        )
    }
```

Dans le `LazyColumn`, **avant** `item { NextReminderCard(…) }` : un rappel annoncé qui ne
sonnera pas doit se lire après l'alerte, pas avant.

```kotlin
                if (issues.isNotEmpty()) {
                    item(key = "health") { HealthCard(issues, haptics) }
                }
```

Ajouter un composable privé `HealthCard(issues: List<HealthIssue>, haptics: Haptics)` qui
reprend le style de `SyncWarningCard` (`Card` en `errorContainer`, `Column(padding 16.dp)`) :
- titre `titleSmall` : « Des rappels risquent de ne pas sonner » ;
- pour chaque problème, une ligne `Row` : à gauche un `Column(weight 1f)` avec un titre
  `bodyMedium` et une explication `bodySmall` ; à droite un `FilledTonalButton` (appeler
  `haptics.click()` puis l'action). Textes et actions :

| Problème | Titre | Explication | Bouton | Action |
|---|---|---|---|---|
| `NOTIFICATIONS_BLOCKED` | Notifications bloquées | Aucun rappel ne s'affichera. | Autoriser | `context.openNotificationSettings()` |
| `REMINDERS_MUTED` | Rappels coupés dans Android | Un type de rappel est désactivé dans les notifications. | Ouvrir | `context.openNotificationSettings()` |
| `FULL_SCREEN_DENIED` | Alarme plein écran refusée | Un badgeage oublié ne passera pas par-dessus l'écran verrouillé. | Autoriser | `context.requestFullScreenIntent()` (appel sous `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)`) |
| `BATTERY_RESTRICTED` | Économie de batterie active | Le téléphone peut retarder les rappels en veille. | Exempter | `context.requestIgnoreBatteryOptimizations()` |

Imports à ajouter selon besoin : `android.os.Build`, `fr.gaddiction.skellobadge.domain.HealthIssue`,
`fr.gaddiction.skellobadge.domain.ReminderHealth`, `fr.gaddiction.skellobadge.notify.Notifications`,
`fr.gaddiction.skellobadge.ui.rememberResumeCount`, `…ui.canUseFullScreenIntent`,
`…ui.isIgnoringBatteryOptimizations`, `…ui.requestFullScreenIntent`,
`…ui.requestIgnoreBatteryOptimizations`, `…ui.openNotificationSettings`,
`androidx.compose.material3.FilledTonalButton`, `fr.gaddiction.skellobadge.ui.Haptics`
(à `e862b61`, `HomeScreen.kt` n'importe ni `Build`, ni `FilledTonalButton`, ni `Haptics` ;
`rememberHaptics` est déjà importé et `val haptics = rememberHaptics()` existe dans `HomeScreen`).

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Step 5 : Suite complète

**Verify** : `./gradlew testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`.

## Test plan

- Automatisé : les 6 cas de `ReminderHealthTest`.
- Manuel, à consigner (émulateur API 34+) : couper les notifications de l'appli → revenir
  sur l'accueil : la carte apparaît avec « Notifications bloquées » ; toucher « Autoriser »,
  les réactiver, revenir : la carte disparaît. Même chose en coupant seulement la catégorie
  « Prise de poste », et avec l'alarme plein écran (Paramètres → Applis → accès spéciaux).

## Done criteria

- [ ] `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, `ReminderHealthTest` avec 6 tests verts
- [ ] `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] `grep -n "HealthCard(" app/src/main/java/fr/gaddiction/skellobadge/ui/home/HomeScreen.kt` → 2 lignes (appel et définition)
- [ ] `grep -n "fun reminderChannelsMuted" app/src/main/java/fr/gaddiction/skellobadge/notify/Notifications.kt` → 1 ligne
- [ ] `git status --short` ne montre que les 5 fichiers du scope (plus `plans/README.md`)
- [ ] Ligne du plan 005 mise à jour dans `plans/README.md`

## STOP conditions

- `ui/Permissions.kt` n'existe pas ou ne contient pas `rememberResumeCount` : le plan 003
  n'est pas fait — STOP.
- `Notifications.kt` n'a plus `REMINDER_CHANNELS` / `reminderChannelId` / `generation` (les
  canaux de rappel ne sont plus versionnés) : STOP, la lecture de l'étape 3 serait fausse.
- La carte apparaît en permanence sur un appareil où tout est accordé (faux positif) :
  STOP et rapporter quelle condition est vraie à tort.

## Maintenance notes

- Toute nouvelle dépendance système des rappels (p. ex. une permission d'alarme exacte
  révocable) s'ajoute comme une valeur de `HealthIssue`, avec son test.
- Suite possible : le `RefreshWorker` pourrait poster une notification discrète quand
  `ReminderHealth.issues` n'est pas vide, pour prévenir sans ouvrir l'appli (sauf
  `NOTIFICATIONS_BLOCKED`, par définition).
