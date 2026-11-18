# Plan 003 : Relire les autorisations au retour d'un écran système pendant l'installation guidée

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat e862b61..HEAD -- app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt app/src/main/java/fr/gaddiction/skellobadge/ui/Permissions.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: bug (UX)
- **Planned at**: commit `e862b61`, 2026-11-18

## Why this matters

**Bloquant** : à l'étape « Planning », si on choisit « Calendrier » et qu'on accorde
l'accès, la liste des calendriers ne se relit jamais. L'écran reste sur « Autoriser le
calendrier », et « Continuer » reste grisé (il exige au moins un calendrier coché). La
seule issue est de basculer sur ICS puis de revenir.

À l'étape « Autorisations », les boutons batterie et alarme plein écran lisent leur état
une seule fois, à l'affichage. Au retour de l'écran système, ils affichent toujours
« Exempter… » / « Autoriser… » : impossible de savoir si ça a marché. Le bouton
notifications ne dit jamais que c'est accordé.

Ce plan crée aussi `ui/Permissions.kt`, un petit fichier partagé que le plan 005
(alerte sur l'accueil) réutilise.

## Current state

- `app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt` — l'assistant en 3 étapes.

`OnboardingScreen.kt:244-255` (étape Planning, source calendrier) :

```kotlin
        PlanningSource.DEVICE_CALENDAR -> {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            val calendars = remember(draft.source) {
                fr.gaddiction.skellobadge.data.calendar.DeviceCalendarSource(context).calendars()
            }
            if (calendars.isEmpty()) {
                Text("Autorise l'accès au calendrier pour choisir celui du travail.")
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                ) { Text("Autoriser le calendrier") }
            } else {
```

`OnboardingScreen.kt:378-448` (`PermissionsStep`) : `notificationLauncher` avec callback
vide `{ }` (`:382-384`) ; bouton notifications (`:402-410`) toujours libellé « Autoriser
les notifications » ; bouton batterie (`:412-424`) qui appelle
`context.isIgnoringBatteryOptimizations()` pendant la composition ; bouton plein écran
(`:428-442`) avec `val allowed = context.canUseFullScreenIntent()`.

Fonctions privées en bas du fichier, `OnboardingScreen.kt:473-499` :

```kotlin
private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return power.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.requestIgnoreBatteryOptimizations() { … ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS … }

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun Context.canUseFullScreenIntent(): Boolean =
    getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun Context.requestFullScreenIntent() { … ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT … }
```

Autres éléments existants à réutiliser :
- `fr.gaddiction.skellobadge.notify.Notifications.canPost(context)` — vrai si les
  notifications sont autorisées (gère Android < 13).
- `DeviceCalendarSource(context).calendars()` — liste vide si la permission manque.
- Dépendance `androidx.lifecycle:lifecycle-runtime-compose` 2.11 déjà présente : elle
  fournit `androidx.lifecycle.compose.LifecycleResumeEffect`.

Conventions : commentaires en français, qui disent le pourquoi ; libellés au tutoiement.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compilation APK | `./gradlew assembleDebug` | `BUILD SUCCESSFUL` |
| Tests unitaires | `./gradlew testDebugUnitTest` | `BUILD SUCCESSFUL` |

Prérequis : SDK Android avec `platforms;android-37.0`.

## Scope

**In scope** :
- `app/src/main/java/fr/gaddiction/skellobadge/ui/Permissions.kt` (à créer)
- `app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt`

**Out of scope** :
- `HomeScreen.kt` : l'alerte sur l'accueil est le plan 005.
- Déplacer la lecture des calendriers dans le ViewModel ou sur un fil d'arrière-plan :
  souhaitable, mais autre chantier.
- Le texte des étapes et l'ordre des boutons.

## Git workflow

- Branche : celle indiquée par l'opérateur, sinon `advisor/003-onboarding-autorisations`.
- Commit en français, 3ᵉ personne du présent, p. ex. « Relit les autorisations au retour
  des écrans système ». Ne pas pousser sans instruction.

## Steps

### Step 1 : Créer `ui/Permissions.kt`

Créer `app/src/main/java/fr/gaddiction/skellobadge/ui/Permissions.kt`
(package `fr.gaddiction.skellobadge.ui`). Y **déplacer** depuis `OnboardingScreen.kt`
les quatre extensions `isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations`,
`canUseFullScreenIntent`, `requestFullScreenIntent`, en passant leur visibilité de
`private` à `internal` (corps inchangés, annotations `@RequiresApi` conservées).

Ajouter dans le même fichier :

```kotlin
/**
 * Compteur qui avance à chaque retour au premier plan.
 *
 * Les autorisations s'accordent sur des écrans système : rien ne prévient l'application
 * quand on en revient. Lire leur état en dépendant de ce compteur suffit à le rafraîchir.
 */
@Composable
internal fun rememberResumeCount(): Int {
    var count by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        count++
        onPauseOrDispose { }
    }
    return count
}
```

(imports : `androidx.compose.runtime.*` nécessaires, `androidx.lifecycle.compose.LifecycleResumeEffect`.)

Dans `OnboardingScreen.kt`, supprimer les quatre fonctions déplacées et ajouter les
imports `fr.gaddiction.skellobadge.ui.isIgnoringBatteryOptimizations`, etc. Retirer les
imports devenus inutiles (`PowerManager`, `NotificationManager`, `RequiresApi`, `Settings`,
`Uri`, `Intent` — **seulement** s'ils ne servent plus ailleurs dans le fichier ; vérifier par grep).

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL` ;
`grep -n "private fun Context\." app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt` → aucune ligne.

### Step 2 : Relire la liste des calendriers après l'autorisation

Dans la branche `PlanningSource.DEVICE_CALENDAR` :

```kotlin
            // Relue au retour de la demande d'autorisation, et à chaque retour au premier
            // plan : sans quoi l'accès accordé laissait l'écran bloqué sur « Autoriser ».
            var asked by remember { mutableIntStateOf(0) }
            val resumes = rememberResumeCount()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { asked++ }
            val calendars = remember(draft.source, asked, resumes) {
                fr.gaddiction.skellobadge.data.calendar.DeviceCalendarSource(context).calendars()
            }
```

Le reste de la branche est inchangé.

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Step 3 : Relire l'état des trois autorisations de l'étape finale

Dans `PermissionsStep` :
- `val resumes = rememberResumeCount()` et `var asked by remember { mutableIntStateOf(0) }` ;
  le callback de `notificationLauncher` devient `{ asked++ }`.
- Calculer les trois états dans des `remember(resumes, asked) { … }` :
  `notificationsAllowed = Notifications.canPost(context)`,
  `batteryExempt = context.isIgnoringBatteryOptimizations()`,
  `fullScreenAllowed = context.canUseFullScreenIntent()` (dans le bloc `if (SDK_INT >= UPSIDE_DOWN_CAKE)`).
- Bouton notifications : `enabled = !notificationsAllowed`, libellé
  « Notifications déjà autorisées » quand c'est le cas, « Autoriser les notifications » sinon
  — même forme que le bouton batterie existant.
- Boutons batterie et plein écran : utiliser ces valeurs au lieu des appels directs.

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL` ;
`grep -n "isIgnoringBatteryOptimizations()\|canUseFullScreenIntent()" app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt`
→ uniquement des lignes à l'intérieur d'un `remember(`.

### Step 4 : Suite complète

**Verify** : `./gradlew testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`.

## Test plan

Pas de test d'UI dans le dépôt ; vérification manuelle à consigner dans le rapport
(téléphone ou émulateur API 34+, appli fraîchement installée) :
1. Étape Planning → « Calendrier » → « Autoriser le calendrier » → accepter : la liste des
   calendriers apparaît sans changer de source ; en cocher un active « Continuer ».
2. Même chose en refusant, puis en accordant depuis les paramètres Android et en revenant :
   la liste apparaît au retour.
3. Étape Autorisations : accorder chacune ; au retour, chaque bouton passe à « … déjà … » et se grise.

## Done criteria

- [ ] `./gradlew testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`
- [ ] `test -f app/src/main/java/fr/gaddiction/skellobadge/ui/Permissions.kt` → succès, et il contient `internal fun rememberResumeCount`
- [ ] `grep -c "{ }" app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt` est inférieur de 2 à la valeur à `e862b61` (les deux callbacks vides ont disparu) — comparer avec `git show e862b61:app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt | grep -c "{ }"`
- [ ] `git status --short` ne montre que les 2 fichiers du scope (plus `plans/README.md`)
- [ ] Vérification manuelle faite, ou signalée comme non faite
- [ ] Ligne du plan 003 mise à jour dans `plans/README.md`

## STOP conditions

- `LifecycleResumeEffect` n'est pas résolu (version de `lifecycle-runtime-compose`
  différente de 2.11 dans `gradle/libs.versions.toml`) : STOP, ne pas changer de version.
- Une des fonctions déplacées est appelée ailleurs que dans `OnboardingScreen.kt` avec une
  signature différente.
- La branche `DEVICE_CALENDAR` ne correspond plus à l'extrait.

## Maintenance notes

- `ui/Permissions.kt` devient le seul endroit des vérifications d'autorisation de l'UI ;
  le plan 005 y ajoute la lecture des canaux de rappel.
- La lecture des calendriers reste synchrone sur le fil principal (comme avant) ; la
  passer en arrière-plan est une amélioration séparée.
