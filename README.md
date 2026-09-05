# Badgeuse

Rappels de badgeage Android, calés sur un planning Skello. On la configure une fois,
puis on l'oublie : elle lit le planning toute seule, pose ses alarmes, et la
notification ouvre directement la badgeuse.

## Ce qu'elle fait

À chaque échéance du planning, une notification tombe :

| Moment | Rappel |
| --- | --- |
| Prise de poste | 5 min avant le début du premier service du jour |
| Départ en coupure | à l'heure de fin du service, quand une vraie coupure suit |
| Retour de coupure | 5 min avant la reprise |
| Changement de poste | à l'instant de la bascule, quand deux services s'enchaînent sans écart |
| Fin de poste | à l'heure de fin du dernier service |

Toucher la notification ouvre la badgeuse. Deux boutons : **J'ai badgé**, qui coupe la
relance, et **Dans 5 min**, qui reporte. Sans réponse, un second rappel arrive au bout de
cinq minutes — c'est ce qui rattrape les oublis réels.

## Comment les jours off sont reconnus

Skello n'utilise pas d'événements « journée entière » au sens iCalendar : un repos ou une
absence est un créneau qui va de **minuit à minuit**. La détection porte donc sur la forme
(départ à minuit local, durée d'au moins 23 h) et non sur le libellé.

Conséquence utile : les congés, arrêts maladie et jours fériés à venir sont couverts
automatiquement, sans liste de mots-clés à maintenir. Le seuil de 23 h — et non 24 —
absorbe les journées de changement d'heure, qui durent 23 h ou 25 h.

## Comment la coupure est déduite

La coupure n'est jamais supposée : elle est lue dans le planning, comme l'écart entre deux
services d'une même journée.

- Écart supérieur à 5 minutes → vraie coupure : deux rappels, sortie puis retour.
- Écart inférieur ou nul → enchaînement de postes : **un seul** rappel de bascule, parce
  qu'il faut badger la sortie et l'entrée dans la foulée sans recevoir deux notifications
  simultanées.

Les services ne sont jamais fusionnés : `10h00→19h00` suivi de `19h00→21h00` reste bien
deux services, avec un badgeage à 19h00.

Un repli facultatif ajoute une coupure à 12h/13h sur les journées longues qui n'en
prévoient aucune. Il est **désactivé par défaut** : sur un planning réel, la plupart des
journées longues sont d'un seul tenant, et l'activer produirait surtout du bruit.

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
2. **Badgeuse** — soit une application installée, choisie dans la liste, soit une adresse
   web. Aucun nom de paquet n'est codé en dur.
3. **Autorisations** — notifications, et exemption d'économie de batterie.

Ensuite, plus rien à faire.

## Compiler

La chaîne d'outils est installée hors du dépôt, dans `D:\Users\Alexis\Programmation\_android` :

```bash
JAVA_HOME=D:/Users/Alexis/Programmation/_android/jdk ./gradlew assembleDebug
```

Le SDK est référencé par `local.properties`, qui n'est pas versionné. L'APK sort dans
`app/build/outputs/apk/`.

Pour lancer les tests du moteur de planification :

```bash
JAVA_HOME=D:/Users/Alexis/Programmation/_android/jdk ./gradlew test
```

## Limites connues

- **Récurrences ICS non gérées.** Le flux Skello n'en contient aucune. Si une `RRULE`
  apparaissait, l'événement serait ignoré et le compte remonté plutôt que passé sous
  silence.
- **Android uniquement.** Sur iPhone, une distribution hors store impose TestFlight
  (99 €/an, builds expirant tous les 90 jours), ce qui est incompatible avec une
  application qu'on installe et qu'on oublie.
- **Économie d'énergie des constructeurs.** Xiaomi, Samsung, Oppo et consorts suspendent
  parfois les alarmes en veille prolongée malgré l'exemption. Les réglages contiennent un
  bouton **Envoyer un rappel de test dans 10 s**, qui emprunte exactement le même chemin
  qu'un vrai rappel : c'est le moyen de le vérifier sur chaque téléphone avant d'en
  dépendre.
- **Material 3 Expressive en version alpha.** L'API n'est accessible qu'à partir de
  `material3` 1.5.0-alpha : en 1.4.0 stable, elle est intégralement `internal`, y compris
  l'annotation qui l'active. C'est le prix à payer pour le langage visuel demandé.
