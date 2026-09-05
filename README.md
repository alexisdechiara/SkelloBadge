# Rappel Badgeuse Skello

Rappels de badgeage Android, calés sur un planning Skello. On la configure une fois,
puis on l'oublie : elle lit le planning toute seule, pose ses alarmes, et la
notification ouvre directement **Skello : la badgeuse** (`com.skellopunchclock`).

## Installer

**[Télécharger la dernière version](https://github.com/alexisdechiara/SkelloBadge/releases/latest)**

Récupérer le fichier `.apk`, l'ouvrir sur le téléphone, et autoriser l'installation depuis
cette source quand Android le demande. Ce lien pointe toujours vers la version la plus
récente ; les mises à jour s'installent par-dessus, sans perdre la configuration.

Android 8.0 ou plus récent.

## Ce qu'elle fait

À chaque échéance du planning, une notification tombe :

| Moment | Rappel |
| --- | --- |
| Prise de poste | 1 min avant le début du premier service du jour |
| Départ en coupure | à l'heure de fin du service, quand une vraie coupure suit |
| Retour de coupure | 1 min avant la reprise |
| Changement de poste | à l'instant de la bascule, quand deux services s'enchaînent sans écart |
| Fin de poste | à l'heure de fin du dernier service |

Toucher la notification ouvre la badgeuse. Deux boutons : **J'ai badgé**, qui coupe toute
la chaîne de relances, et **Dans 5 min**, qui reporte.

## L'insistance, par paliers

Le timing étant contrôlé de près, un rappel ignoré ne s'efface pas :

1. **Relance chaque minute** tant que le badgeage n'est pas confirmé.
2. **Au bout de 5 minutes**, l'application passe à l'**alarme plein écran** : elle rallume
   l'écran, s'affiche par-dessus le verrouillage, **sonne en boucle** sur le flux audio des
   alarmes et **vibre** sans interruption, avec un bouton unique vers la badgeuse. Une
   notification se balaie sans y penser ; pas cet écran.

   La vibration part dans tous les cas, y compris en mode vibreur ou silencieux où elle est
   le seul signal perceptible. Le son, lui, respecte le profil sonore du téléphone. Le
   signal s'arrête de lui-même au bout de cinq minutes pour ne pas vider la batterie ;
   l'écran, lui, reste affiché.
3. Un plafond de 30 relances évite l'emballement si le téléphone reste inaccessible.

Les trois valeurs sont réglables, et l'alarme se désactive si elle est jugée excessive.

## Comment les jours off sont reconnus

Skello n'utilise pas d'événements « journée entière » au sens iCalendar : un repos ou une
absence est un créneau qui va de **minuit à minuit**. La détection porte donc sur la forme
(départ à minuit local, durée d'au moins 23 h) et non sur le libellé.

Conséquence utile : les congés, arrêts maladie et jours fériés à venir sont couverts
automatiquement, sans liste de mots-clés à maintenir. Le seuil de 23 h — et non 24 —
absorbe les journées de changement d'heure, qui durent 23 h ou 25 h.

Le fuseau retenu est celui porté par l'événement, c'est-à-dire celui de l'établissement,
jamais celui du téléphone : minuit reste minuit même en déplacement.

## Les journées de réserve

Un créneau dont le libellé contient **« ou off »** — par exemple `EG ou Off` — désigne une
journée où la présence n'est requise qu'en renfort de dernière minute. Il reste affiché au
planning avec ses horaires, mais ne déclenche aucun rappel.

Au-delà de cette règle, l'écran **Services concernés** présente les types rencontrés sous
forme de puces à sélection multiple. La liste se construit toute seule à partir du planning
et **mémorise tout ce qu'elle a déjà vu** : un service saisonnier ne disparaît pas des
réglages parce qu'il est sorti de la fenêtre de trois semaines.

Sur une journée mixte, seuls les créneaux encore actifs comptent : les bornes se
recalculent dessus, si bien qu'un créneau muet ne laisse pas de rappel orphelin.

## Comment la coupure est déduite

La coupure est d'abord lue dans le planning, comme l'écart entre deux services d'une même
journée.

- Écart supérieur à 5 minutes → vraie coupure : deux rappels, sortie puis retour.
- Écart inférieur ou nul → enchaînement de postes : **un seul** rappel de bascule, parce
  qu'il faut badger la sortie et l'entrée dans la foulée sans recevoir deux notifications
  simultanées.

Les services ne sont jamais fusionnés : `10h00→19h00` suivi de `19h00→21h00` reste bien
deux services, avec un badgeage à 19h00.

Sur les journées longues d'un seul tenant, dont le planning ne prévoit aucune coupure, un
repli ajoute un rappel à 12h et 13h. Il est actif par défaut.

## Textes personnalisables

Le titre et le texte de chaque type de rappel se modifient dans les réglages, en sections
dépliables. Deux marqueurs sont remplacés à l'affichage : `{heure}` par l'heure de
l'action, `{poste}` par le nom du service.

Les réglages sont eux-mêmes organisés en six sections repliées, chacune affichant son état
dans son en-tête. Les valeurs en minutes se règlent par pas de un : un curseur occuperait
toute la largeur et le doigt qui fait défiler la page en changerait la valeur au passage,
ce qui, sur un écran consulté rarement, passerait inaperçu.

## Ce qui la fait tourner sans intervention

Une replanification complète est déclenchée par :

- un travail périodique toutes les 2 h (le flux Skello annonce un rafraîchissement horaire) ;
- le démarrage du téléphone, un changement d'heure ou de fuseau, une mise à jour de l'app ;
- chaque ouverture de l'application.

Les alarmes sont posées 7 jours à l'avance, et le dernier planning récupéré est conservé
sur le disque : sans réseau, les rappels continuent de tomber.

## Configuration au premier lancement

1. **Planning** — soit l'adresse du flux ICS Skello, soit un calendrier de l'appareil.
   En mode ICS, aucune permission calendrier n'est demandée. Le bouton **Vérifier le
   lien** récupère et interprète le flux sur-le-champ, et annonce combien de créneaux et
   de jours non travaillés il a reconnus : une adresse erronée ne peut pas passer
   inaperçue, alors qu'elle se traduirait autrement par une simple absence de rappels.
2. **Badgeuse** — pré-réglée sur `com.skellopunchclock`, modifiable dans la liste des
   applications installées ou remplaçable par une adresse web.
3. **Autorisations** — notifications, exemption d'économie de batterie, et sur Android 14+
   l'autorisation d'affichage plein écran.

Ensuite, plus rien à faire.

## Vérifier que ça marche sur un téléphone donné

Les réglages contiennent deux boutons de contrôle, à utiliser une fois sur chaque
téléphone avant d'en dépendre :

- **Envoyer un rappel de test dans 10 s** — emprunte le chemin complet d'un vrai rappel.
- **Tester l'alarme plein écran** — déclenche directement l'escalade. Verrouiller l'écran
  pendant les dix secondes qui suivent : l'alarme doit s'afficher par-dessus.

## Compiler

Il faut un JDK 21 et le SDK Android, compilation contre l'API 37. Le chemin du SDK se
déclare dans `local.properties`, non versionné :

```
sdk.dir=/chemin/vers/android/sdk
```

Puis :

```bash
./gradlew assembleDebug
```

Pour l'APK distribuable :

```bash
./gradlew assembleRelease
```

La signature de distribution est décrite par `keystore.properties`, lui aussi hors dépôt.
**Le magasin de clés est irremplaçable** : le perdre interdit toute mise à jour d'une
installation existante, qui devrait alors être désinstallée puis réinstallée. À sauvegarder
ailleurs que sur cette machine.

En l'absence de ces deux fichiers — sur un poste qui vient de cloner — la variante release
retombe sur la clé de debug et le build fonctionne quand même, mais l'APK produit ne peut
pas mettre à jour une installation signée avec la vraie clé.

## Publier une version

Une étiquette suffit :

```bash
git tag v1.1.0 && git push origin v1.1.0
```

Le workflow `Publication` compile, vérifie la signature et crée la release GitHub avec
l'APK en pièce jointe. La clé de signature n'est pas lue depuis le disque mais depuis
quatre secrets de dépôt — `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` — reconstituée dans un fichier temporaire puis
effacée en fin de travail. Seules les étiquettes déclenchent ce workflow : les
constructions ordinaires n'approchent jamais la clé.

Le nom de version vient de l'étiquette, le code de version du numéro de run, qui croît
strictement — Android refuse une mise à jour dont le code de version n'augmente pas.

**Sur un dépôt privé, les pièces jointes d'une release exigent un compte GitHub ayant accès
au dépôt.** Pour un lien de téléchargement réellement ouvert, il faut rendre le dépôt
public :

```bash
gh repo edit --visibility public --accept-visibility-change-consequences
```

Tests du moteur de planification :

```bash
./gradlew testDebugUnitTest
```

## Limites connues

- **Récurrences ICS non gérées.** Le flux Skello n'en contient aucune. Si une `RRULE`
  apparaissait, l'événement serait ignoré et le compte remonté plutôt que passé sous
  silence.
- **Android uniquement.** Sur iPhone, une distribution hors store impose TestFlight
  (99 €/an, builds expirant tous les 90 jours), incompatible avec une application qu'on
  installe et qu'on oublie.
- **Économie d'énergie des constructeurs.** Xiaomi, Samsung, Oppo et consorts suspendent
  parfois les alarmes en veille prolongée malgré l'exemption. D'où les deux boutons de test.
- **Material 3 Expressive en version alpha.** L'API n'est accessible qu'à partir de
  `material3` 1.5.0-alpha : en 1.4.0 stable, elle est intégralement `internal`, y compris
  l'annotation qui l'active.
