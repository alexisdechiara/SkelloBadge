# Plans d'implémentation

Rédigés le 2026-11-18 par le skill *improve*, sur le commit `e862b61`. À exécuter dans
l'ordre ci-dessous, sauf dépendances contraires. Chaque exécutant lit le plan en entier
avant de commencer, respecte ses conditions d'arrêt (STOP) et met sa ligne à jour.

Groupes retenus par le mainteneur : **Réglages & perf** et **UX**.

## Ordre d'exécution et état

| Plan | Titre | Priorité | Effort | Dépend de | État |
|------|-------|----------|--------|-----------|------|
| 001 | [Rendre l'écriture des réglages atomique et tester leur encodage](001-settings-update-atomique.md) | P1 | S | — | TODO |
| 003 | [Relire les autorisations au retour d'un écran système (installation guidée)](003-onboarding-relit-les-autorisations.md) | P1 | S | — | TODO |
| 005 | [Signaler sur l'accueil une autorisation retirée](005-alerte-autorisations-accueil.md) | P1 | S | 003 | TODO |
| 002 | [Ne plus recharger ni replanifier à chaque frappe](002-reglages-sans-rafraichissement-par-frappe.md) | P2 | S | 001 | TODO |
| 004 | [Faire revenir le geste retour à l'écran précédent](004-geste-retour.md) | P2 | S | — | TODO |

États possibles : TODO | IN PROGRESS | DONE | BLOCKED (raison en une ligne) | REJECTED (raison en une ligne)

## Dépendances

- 002 exige 001 : le regroupement des écritures de l'éditeur de textes suppose que deux
  écritures rapprochées ne s'écrasent plus.
- 005 exige 003 : 003 crée `ui/Permissions.kt` (vérifications d'autorisation partagées et
  `rememberResumeCount`) que la carte de l'accueil réutilise.
- 003 et 004 touchent tous deux `OnboardingScreen.kt`, à des endroits différents : à faire
  l'un après l'autre, pas en parallèle.

## Constats relevés mais non planifiés (à la demande)

Le groupe « fiabilité des rappels » n'a pas été retenu pour cette série. Il reste, à mon
avis, le plus important :

- **Une replanification coupe les relances en cours** (`AlarmScheduler.replaceAll` →
  `cancelAll`, déclenché à chaque ouverture de l'appli via `MainActivity.onStart`, à chaque
  réglage, et toutes les 2 h). Les relances et l'alarme plein écran d'un rappel ignoré
  disparaissent. Effort M, avec tests de caractérisation d'abord.
- **Une rotation coupe l'alarme plein écran** (`FullScreenAlarmActivity.onDestroy` →
  `AlarmSignal.stop()` sans condition). Effort S.
- **Un délai d'alarme > 30 min ne déclenche jamais l'alarme** (plafond de relances fixe à
  30, réglage jusqu'à 60). Effort S.
- **La vérification du lien ICS peut valider une mauvaise adresse** en se rabattant sur
  un cache non lié à l'URL (`AppViewModel.verifyIcsLink`). Effort S.
- Verrou d'`AlarmScheduler` inopérant (une instance par appel) ; planning vide qui efface
  toutes les alarmes ; rappel perdu au redémarrage ; tests du lecteur ICS ; lint en CI ;
  README périmé sur les boutons de test.

## Constats écartés

- Tuile des réglages rapides : pas d'action naturelle, « J'ai badgé » est déjà sur la notification.
- Rappel par géolocalisation : le flux n'a pas de coordonnées, lieux variables, permission lourde.
- Plusieurs plannings : produit pour une personne et un flux ; le calendrier accepte déjà plusieurs agendas.
- Export des réglages : la sauvegarde Android couvre déjà DataStore.
- Détection automatique du badgeage (UsageStats / NotificationListener) : autorisations
  spéciales lourdes, « Skello ouvert » ne veut pas dire « pointage fait ».
- Identifiant de rappel (`minute*8 + type`) : pas de collision ni de débordement avant ~2480.
- Vérification d'intégrité des mises à jour : la vraie garantie est la signature Android ; acceptable.
