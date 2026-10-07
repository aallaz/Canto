# Boîte à Histoires (Canto) 🎧🧸

Une application Android conçue spécifiquement pour transformer un vieux smartphone (ex: Lenovo K6 sous LineageOS) en une boîte à histoires hors-ligne pour enfant.

L'application sert également de launcher pour verrouiller l'appareil et empêcher la navigation dans les paramètres Android.

## 🌟 Fonctionnalités
- Interface enfant avec une grille de grandes tuiles visuelles.
- Lecteur simplifié avec pochette et boutons de lecture.
- Mode kiosque : lancement en tant que page d'accueil système.
- Lecture hors ligne à partir de fichiers audio locaux.

## 🧱 État actuel du projet
La base du projet Android a été initialisée avec un squelette Jetpack Compose et une configuration minimale pour l'application.

### Structure de départ
- Projet Gradle multi-fichiers pour Android.
- Activity principale avec un écran Compose de base.
- Permissions de stockage ajoutées dans le manifest.
- Thème Material 3 adapté à un usage plein écran.

## 📂 Architecture des dossiers
L'application scanne un répertoire racine défini (par exemple /sdcard/Histoires/ ou /storage/emulated/0/Histoires/). Chaque sous-dossier représente une tuile.

Structure requise :
```text
/sdcard/Histoires/
  ├── 01_Boucle_d_or/
  │   ├── cover.jpg
  │   ├── piste_01.mp3
  │   └── piste_02.mp3
  ├── 02_Musiques/
  │   ├── cover.jpg
  │   ├── chanson1.m4a
  │   └── chanson2.m4a
```

## ▶️ Prochaines étapes
1. Implémenter le scan des dossiers audio et l'affichage des tuiles.
2. Ajouter un lecteur audio fonctionnel avec navigation entre pistes.
3. Finaliser le comportement de kiosque et l'expérience enfant.
