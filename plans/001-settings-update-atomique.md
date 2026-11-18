# Plan 001 : Rendre l'écriture des réglages atomique et tester leur encodage

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat e862b61..HEAD -- app/src/main/java/fr/gaddiction/skellobadge/data/SettingsRepository.kt app/src/main/java/fr/gaddiction/skellobadge/data/Settings.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `e862b61`, 2026-11-18

## Why this matters

`SettingsRepository.update` lit les réglages, applique la transformation **hors**
de la transaction DataStore, puis réécrit les ~40 clés. Deux écritures
concurrentes (le `RefreshWorker` en arrière-plan qui met à jour `lastSyncEpochMillis`
/ `knownShiftTypes` / `workingStandbyDates`, et l'utilisateur qui change un réglage
ou touche « Je travaille ce jour ») se marchent dessus : la dernière écrit une copie
périmée et **annule silencieusement** la modification de l'autre. Pour une journée de
réserve rebasculée en « travaillée », cela veut dire un badgeage sans rappel.

Second risque : l'encodage (≈ ligne 34‑78) et le décodage (≈ ligne 83‑144) sont deux
listes tenues à la main. Un champ ajouté à `AppSettings` mais oublié d'un côté
retombe sans bruit sur sa valeur par défaut. Aucun test ne le détecte aujourd'hui.

## Current state

- `app/src/main/java/fr/gaddiction/skellobadge/data/SettingsRepository.kt` — dépôt
  DataStore Preferences ; seul point d'écriture des réglages.
- `app/src/main/java/fr/gaddiction/skellobadge/data/Settings.kt` — `data class AppSettings`
  (tous les champs ont une valeur par défaut), `Sound`, `Tone`, `Wording`, enums.

Extrait actuel, `SettingsRepository.kt:24-35` :

```kotlin
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map(::decode)

    suspend fun current(): AppSettings = settings.first()

    /** Renvoie l'état obtenu, pour éviter une relecture juste après une écriture. */
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val updated = transform(current())
        context.dataStore.edit { prefs ->
            prefs[Keys.CONFIGURED] = updated.configured
            prefs[Keys.SOURCE] = updated.source.name
            // … toutes les autres clés …
```

puis `:80` `return updated`, `:83` `private fun decode(prefs: Preferences): AppSettings {`,
et en fin de fichier `private object Keys { … }` avec les noms de clés
(`"configured"`, `"source"`, `"ics_url"`, … `"reminder_sound"`, `"alarm_sound"`, …)
ainsi que `wordingTitleKey(kind)` = `"wording_title_" + kind.name` et
`wordingBodyKey(kind)` = `"wording_body_" + kind.name`.

Appelants de `update` (aucun ne doit changer) : `ui/AppViewModel.kt` (plusieurs),
`schedule/RefreshWorker.kt:66` et `:87`. Leurs lambdas sont de simples `copy(...)`.

Conventions du dépôt :
- Commentaires et KDoc **en français**, qui expliquent le *pourquoi* (voir n'importe quel
  fichier, p. ex. `domain/SyncHealth.kt`).
- Tests JUnit 4 sur JVM, noms entre backticks en anglais, KDoc de classe en français.
  Modèle : `app/src/test/java/fr/gaddiction/skellobadge/SoundTest.kt`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Tests unitaires | `./gradlew testDebugUnitTest` | `BUILD SUCCESSFUL` |
| Un seul test | `./gradlew testDebugUnitTest --tests '*SettingsCodecTest*'` | `BUILD SUCCESSFUL` |
| Compilation APK | `./gradlew assembleDebug` | `BUILD SUCCESSFUL` |

Prérequis : un SDK Android (`ANDROID_HOME` ou `sdk.dir` dans `local.properties`) avec
`platforms;android-37.0`. Le build a été vérifié vert à `e862b61`.

## Scope

**In scope** :
- `app/src/main/java/fr/gaddiction/skellobadge/data/SettingsRepository.kt`
- `app/src/main/java/fr/gaddiction/skellobadge/data/SettingsCodec.kt` (à créer)
- `app/src/test/java/fr/gaddiction/skellobadge/SettingsCodecTest.kt` (à créer)

**Out of scope** :
- **Les noms de clés** : n'en renommer aucun. Un nom changé = réglage perdu chez tous
  les utilisateurs à la mise à jour.
- `Settings.kt` (le modèle ne change pas), les appelants de `update`.
- La séparation « persister » / « replanifier » dans `AppViewModel` : c'est le plan 002.

## Git workflow

- Branche : celle indiquée par l'opérateur, sinon `advisor/001-settings-atomique`.
- Messages de commit en français, verbe à la 3ᵉ personne du présent, comme
  `git log` : « Rend l'écriture des réglages atomique », avec un corps qui explique le pourquoi.
- Ne pas pousser ni ouvrir de PR sans instruction de l'opérateur.

## Steps

### Step 1 : Extraire l'encodage et le décodage dans `SettingsCodec`

Créer `data/SettingsCodec.kt` contenant un `internal object SettingsCodec` avec :
- `fun encode(settings: AppSettings, prefs: MutablePreferences)` — le corps actuel du
  bloc `edit { prefs -> … }` (toutes les affectations `prefs[Keys.X] = …`, y compris la
  boucle sur `ReminderKind.entries` pour les formulations) ;
- `fun decode(prefs: Preferences): AppSettings` — le corps actuel de `decode` ;
- l'objet `Keys` et les fonctions `wordingTitleKey` / `wordingBodyKey`, déplacés tels
  quels (**mêmes chaînes**), en `private`.

Copier-coller, pas réécrire : les lignes doivent rester identiques à l'existant.

**Verify** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL` (le dépôt n'utilise pas encore le codec).

### Step 2 : Faire la transformation dans la transaction

Dans `SettingsRepository.kt`, remplacer `update` et `settings` par :

```kotlin
val settings: Flow<AppSettings> = context.dataStore.data.map(SettingsCodec::decode)

/**
 * La transformation s'applique à l'état lu dans la transaction même : deux écritures
 * concurrentes — le travail de fond et un réglage changé à l'écran — se succèdent au
 * lieu de s'écraser, chacune partant de ce que l'autre vient d'écrire.
 */
suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
    val written = context.dataStore.edit { prefs ->
        SettingsCodec.encode(transform(SettingsCodec.decode(prefs)), prefs)
    }
    return SettingsCodec.decode(written)
}
```

Supprimer de `SettingsRepository.kt` l'ancien `decode`, `Keys`, `wordingTitleKey`,
`wordingBodyKey` et les imports devenus inutiles. Garder `current()` et la déclaration
`private val Context.dataStore … by preferencesDataStore(name = "settings")` (nom inchangé).

**Verify** : `grep -n "private object Keys" app/src/main/java/fr/gaddiction/skellobadge/data/*.kt`
→ une seule ligne, dans `SettingsCodec.kt`. Puis `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Step 3 : Test d'aller-retour

Créer `app/src/test/java/fr/gaddiction/skellobadge/SettingsCodecTest.kt`
(package `fr.gaddiction.skellobadge`, il faudra donc que le test accède à un objet
`internal` du même module : c'est permis). Utiliser
`androidx.datastore.preferences.core.mutablePreferencesOf` et
`androidx.datastore.preferences.core.stringPreferencesKey`.

Cas à écrire :
1. **`every field survives a round trip`** — construire un `AppSettings` dont **chaque
   champ diffère de sa valeur par défaut** (p. ex. `configured = true`,
   `source = PlanningSource.DEVICE_CALENDAR`, `icsUrl = "https://exemple.test/a.ics"`,
   `calendarIds = setOf(3L, 7L)`, chaque minute décalée, chaque booléen inversé,
   `reminderSound = Sound.Bundled(Tone.BIPS)`, `alarmSound = Sound.Picked("content://x/1")`,
   ensembles non vides, `wording = Wording.DEFAULTS + (ReminderKind.CLOCK_IN to Wording("T", "B"))`,
   etc.). `encode` dans `mutablePreferencesOf()`, `decode`, puis `assertEquals(original, decoded)`.
2. **`the fixture differs from defaults on every field`** — garde-fou du test 1 : vérifier
   par réflexion Kotlin simple que la fixture est différente du défaut champ par champ.
   Implémentation suggérée sans dépendance : comparer `fixture.toString()` découpé n'est
   pas fiable ; utiliser plutôt `AppSettings::class.java.declaredFields` (champs Java du
   data class) **filtrés sur `!java.lang.reflect.Modifier.isStatic(it.modifiers)`** — le
   companion object expose `Companion`, `DEFAULT_REMINDER_SOUND` et des constantes comme
   champs statiques, à ignorer —, `isAccessible = true`, et `assertNotEquals(field.name, f.get(defaults), f.get(fixture))`
   pour chacun. Exclure explicitement par nom les champs qui n'ont pas de sens à varier,
   s'il y en a, en le commentant.
3. **`empty preferences decode to defaults`** — `decode(mutablePreferencesOf())` vaut `AppSettings()`.
4. **`stored key names are stable`** — remplir à la main `mutablePreferencesOf()` avec
   `stringPreferencesKey("ics_url")` = `"https://x.test/p.ics"` et
   `stringPreferencesKey("reminder_sound")` = `"tone:MARIMBA"`, décoder, et vérifier
   `icsUrl` et `reminderSound`. Ce test casse si quelqu'un renomme une clé.

**Verify** : `./gradlew testDebugUnitTest --tests '*SettingsCodecTest*'` → `BUILD SUCCESSFUL`, 4 tests.

### Step 4 : Suite complète

**Verify** : `./gradlew testDebugUnitTest assembleDebug` → `BUILD SUCCESSFUL`.

## Test plan

Les 4 tests de l'étape 3, dans `SettingsCodecTest.kt`, sur le modèle de `SoundTest.kt`.
La concurrence elle-même n'est pas testée (il faudrait un vrai DataStore sur disque) : la
garantie vient de `DataStore.edit`, qui sérialise les transactions.

## Done criteria

- [ ] `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL`, `SettingsCodecTest` présent avec 4 tests verts
- [ ] `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] `grep -n "transform(current())" app/src/main/java/fr/gaddiction/skellobadge/data/SettingsRepository.kt` → aucune ligne
- [ ] Aucune clé perdue : `git show e862b61:app/src/main/java/fr/gaddiction/skellobadge/data/SettingsRepository.kt | grep -c 'PreferencesKey("'` et `grep -c 'PreferencesKey("' app/src/main/java/fr/gaddiction/skellobadge/data/SettingsCodec.kt` donnent le même nombre
- [ ] `git status --short` ne montre que les 3 fichiers du scope (plus `plans/README.md`)
- [ ] Ligne du plan 001 mise à jour dans `plans/README.md`

## STOP conditions

- `mutablePreferencesOf` n'est pas résolu dans les tests JVM (la dépendance
  `datastore-preferences-core` n'est pas sur le classpath de test) : STOP, ne pas ajouter
  Robolectric ni de dépendance sans accord.
- Le test 2 révèle un champ d'`AppSettings` que l'encodage ne couvre pas : **c'est un vrai
  bug** — le signaler dans le rapport, puis l'ajouter à `encode`/`decode` avec un nom de clé
  neuf en snake_case, sans toucher aux clés existantes.
- Un champ d'`AppSettings` ne peut pas différer de son défaut de façon sensée : l'exclure du
  test 2 en le nommant dans un commentaire, et le signaler.

## Maintenance notes

- Tout nouveau champ d'`AppSettings` doit être ajouté dans `SettingsCodec.encode` **et**
  `decode`, et à la fixture du test 1 ; le test 2 échoue tant que la fixture ne le fait pas varier.
- Les transformations passées à `update` s'exécutent maintenant dans la transaction
  DataStore : elles doivent rester pures et rapides (pas d'I/O, pas d'appel à `update` imbriqué,
  qui provoquerait un interblocage).
- Le plan 002 s'appuie sur ce `update` corrigé.
