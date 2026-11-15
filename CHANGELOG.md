# Nouveautés

Chaque version publiée reprend sa section ci-dessous, mot pour mot : elle devient la
description de la release GitHub, et s'affiche dans l'application sous « Nouveautés ».

Écrite pour qui utilise l'application, pas pour qui la développe : ce qui change au
quotidien, en une phrase par point. Le détail technique reste dans l'historique Git.

<!--
  La première section donne la version publiée. Sur main, elle décrit la prochaine
  version au fil du développement ; publier, c'est pousser main sur prod :

    git push origin main:prod

  La publication lit « ## X.Y.Z », crée l'étiquette vX.Y.Z et la release avec ces notes.
  Elle échoue d'emblée, avant toute compilation, si la version est déjà publiée ou plus
  ancienne que la dernière : il faut alors ouvrir une nouvelle section en tête.
-->

## 1.2.0

- **La pause lue dans la note** — « Pause cantine 12h-13h », « Repas 11h45 → 12h30 »
  ou le trou entre deux animations datées : l'heure écrite par le responsable remplace
  la pause fixe de 12h-13h.
- **La pause affichée sur le planning**, avec sa provenance : lue dans la note, entre
  deux activités, ou horaire par défaut. La notification de départ cite la phrase de la
  note qui l'a fixée.
- **La note dépliable** — elle tient sur une ligne sous chaque créneau, et s'affiche en
  entier au toucher.
- **Les mises à jour depuis l'application** — une nouvelle version est annoncée par une
  notification discrète, puis téléchargée, vérifiée et installée sans passer par le
  navigateur. La configuration est conservée.
- Les mots qui signalent la pause se règlent dans Réglages → Pause.

Cette version s'installe encore à la main, depuis le fichier joint ci-dessous. Les
suivantes se proposeront d'elles-mêmes dans l'application.

## 1.1.1

- **Alerte quand le planning n'est plus rafraîchi** depuis plus d'un jour : les rappels
  continuent sur la dernière version connue, mais une notification discrète le signale.
- La date de dernière synchronisation ne compte plus que les récupérations réussies.

## 1.1.0

- **Demande la veille** des journées à confirmer — « EG ou Off », « EG ou Bureau » — avec
  ouverture directe de la conversation avec le responsable. Une seule demande par
  déplacement, même sur plusieurs jours au même endroit.
- **Tout écart entre deux services est une pause à badger**, sortie puis entrée, même
  quand ils sont collés.
- L'alarme de retard ne sonne et ne vibre plus en double.
- Un même vocabulaire partout, aligné sur Skello : entrée, sortie.
- L'installation guidée peut être rouverte et quittée sans rien perdre.

## 1.0.0

- Première version : rappels de badgeage calés sur le planning Skello, relances chaque
  minute, alarme plein écran au bout de cinq minutes, jours off et journées de réserve
  reconnus d'eux-mêmes.
