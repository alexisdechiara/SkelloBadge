# Plan 004 : Faire revenir le geste retour à l'écran précédent

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat e862b61..HEAD -- app/src/main/java/fr/gaddiction/skellobadge/MainActivity.kt app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (si le plan 003 a été fait avant, `OnboardingScreen.kt` aura changé ailleurs : c'est normal, seules les lignes citées ici comptent)
- **Category**: bug (UX)
- **Planned at**: commit `e862b61`, 2026-11-18

## Why this matters

La navigation tient dans deux booléens (`showSettings`, `reconfiguring`), sans
bibliothèque de navigation ni `BackHandler`. Le geste retour d'Android (le geste le
plus courant en navigation plein écran) **quitte l'application** depuis les Réglages
au lieu de revenir à l'accueil. Dans l'installation guidée, il quitte aussi au lieu de
revenir à l'étape précédente, ou d'annuler une reconfiguration.

## Current state

- `app/src/main/java/fr/gaddiction/skellobadge/MainActivity.kt` — `AppRoot()` choisit l'écran.
- `app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt` — l'assistant.

`MainActivity.kt:60-101` :

```kotlin
@Composable
private fun AppRoot() {
    val viewModel: AppViewModel = viewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    // …
    var reconfiguring by remember { mutableStateOf(false) }

    val current = settings
    when {
        current == null -> Box(…) { CircularProgressIndicator() }

        !current.configured || reconfiguring -> OnboardingScreen(
            viewModel = viewModel,
            settings = current,
            onCancel = if (current.configured) {
                { reconfiguring = false }
            } else {
                null
            },
            onDone = { reconfiguring = false },
        )

        showSettings -> SettingsScreen(
            …,
            onBack = { showSettings = false },
        )

        else -> HomeScreen(viewModel, current) { showSettings = true }
    }
}
```

`OnboardingScreen.kt:71-80` : `fun OnboardingScreen(viewModel, settings, onCancel: (() -> Unit)?, onDone)`,
avec `var step by remember { mutableStateOf(0) }`. Le bouton « Retour » du bas fait
`step--` (`:128-131`), activé si `step > 0`. « Annuler » en haut appelle `onCancel`
(n'existe qu'en reconfiguration).

`AndroidManifest.xml` déclare `android:enableOnBackInvokedCallback="true"` : le retour
prédictif est actif, `BackHandler` d'`androidx.activity.compose` (dépendance
`activity-compose` 1.13 déjà présente) le gère correctement.

`grep -rn BackHandler app/src/main` → aucun résultat aujourd'hui.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compilation APK | `./gradlew assembleDebug` | `BUILD SUCCESSFUL` |
| Tests unitaires | `./gradlew testDebugUnitTest` | `BUILD SUCCESSFUL` |

## Scope

**In scope** :
- `app/src/main/java/fr/gaddiction/skellobadge/MainActivity.kt`
- `app/src/main/java/fr/gaddiction/skellobadge/ui/onboarding/OnboardingScreen.kt`

**Out of scope** :
- Introduire Navigation Compose ou changer la structure des écrans.
- Le comportement sur l'accueil : le retour y quitte l'appli, c'est voulu.
- `FullScreenAlarmActivity` : l'écran d'alarme exige une action explicite, ne pas y
  ajouter de retour.

## Git workflow

- Branche : celle indiquée par l'opérateur, sinon `advisor/004-geste-retour`.
- Commit en français, 3ᵉ personne du présent, p. ex. « Fait revenir le geste retour à
  l'écran précédent ». Ne pas pousser sans instruction.

## Steps

### Step 1 : Retour depuis les Réglages

Dans `AppRoot`, juste avant le `when`, ajouter (import `androidx.activity.compose.BackHandler`) :

```kotlin
    // Sans cela, le geste retour quittait l'application depuis les réglages.
    BackHandler(enabled = showSettings && !reconfiguring) { showSettings = false }
```

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Step 2 : Retour dans l'installation guidée

Dans `OnboardingScreen`, juste après `var draft by remember { … }`, ajouter :

```kotlin
    // Le geste retour suit le bouton « Retour » ; à la première étape d'une
    // reconfiguration, il annule comme « Annuler ». À la toute première installation,
    // il n'y a rien où revenir : il quitte, comme partout ailleurs.
    BackHandler(enabled = step > 0) { step-- }
    BackHandler(enabled = step == 0 && onCancel != null) { onCancel?.invoke() }
```

Le dernier `BackHandler` déclaré a la priorité quand plusieurs sont actifs ; ici leurs
conditions s'excluent, l'ordre n'a pas d'importance.

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Step 3 : Suite complète

**Verify** : `./gradlew testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`.

## Test plan

Pas de test d'UI dans le dépôt ; vérification manuelle à consigner (geste retour ou
bouton retour système) :
1. Accueil → Réglages → retour : revient à l'accueil.
2. Réglages → « Changer de planning ou de badgeuse » → étape 2 → retour : étape 1 ;
   retour encore : revient à l'accueil sans rien modifier.
3. Première installation (données effacées) → étape 2 → retour : étape 1 ; retour encore : quitte l'appli.
4. Accueil → retour : quitte l'appli.

## Done criteria

- [ ] `./gradlew testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`
- [ ] `grep -rn "BackHandler(" app/src/main/java` → 3 lignes (1 dans `MainActivity.kt`, 2 dans `OnboardingScreen.kt`)
- [ ] `git status --short` ne montre que les 2 fichiers du scope (plus `plans/README.md`)
- [ ] Vérification manuelle faite, ou signalée comme non faite
- [ ] Ligne du plan 004 mise à jour dans `plans/README.md`

## STOP conditions

- `AppRoot` ou la signature d'`OnboardingScreen` ne correspondent plus aux extraits.
- `BackHandler` n'est pas résolu avec la version d'`activity-compose` du dépôt.
- Le dépôt a entre-temps adopté une bibliothèque de navigation (chercher
  `navigation-compose` dans `gradle/libs.versions.toml`) : ce plan est alors caduc.

## Maintenance notes

- Tout nouvel écran ajouté par booléen dans `AppRoot` a besoin de son propre `BackHandler`.
  Au troisième écran, passer à Navigation Compose devient plus simple que d'empiler des booléens.
