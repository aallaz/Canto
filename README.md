# Boîte à Histoires (Canto) 🎧🧸

Une application Android conçue spécifiquement pour transformer un vieux smartphone (ex: Lenovo K6 sous LineageOS) en une boîte à histoires hors-ligne pour enfant.

L'application sert également de launcher pour verrouiller l'appareil et empêcher la navigation dans les paramètres Android.

## 🌟 Fonctionnalités
- Interface enfant avec une grille de grandes tuiles encadrées, thème sombre pour économiser l'écran.
- Lecteur simplifié avec pochette, piste en cours et boutons précédent / lecture / suivant.
- Mode kiosque : lancement en tant que page d'accueil système, bouton retour désactivé.
- Lecture hors ligne à partir de fichiers audio locaux (mp3, m4a, wav, aac, ogg).
- Réglages parent (⚙ dans la galerie et le lecteur), protégés par un code à 4 chiffres choisi à la première ouverture :
  - volume et luminosité (plafonnée à 60 %, mémorisée) ;
  - dossiers Histoires détectés et nouvelle recherche ;
  - transfert Wi-Fi ;
  - changement du code, extinction du téléphone, sortie de l'application.

## 💾 Carte SD et stockage interne
Canto cherche un dossier `Histoires` à la racine de chaque carte SD montée, puis dans le stockage interne, et affiche les histoires de tous les dossiers trouvés (carte SD en premier). L'insertion ou le retrait d'une carte relance la recherche automatiquement.

Sur Android 11 et plus, il faut accorder « Accès à tous les fichiers » (bouton dans les réglages) pour lire la carte SD, les fichiers `.nfo` et recevoir les transferts.

## 📶 Transfert Wi-Fi
1. Réglages ⚙ → « Démarrer le transfert ».
2. Sur un ordinateur ou un téléphone connecté au même Wi-Fi, ouvrir l'adresse affichée (ex. `http://192.168.1.20:8080`).
3. Saisir le code parent, choisir un nom de dossier (une tuile), sélectionner les fichiers audio et `cover.jpg`, puis « Envoyer ».

Les fichiers sont écrits dans le premier dossier Histoires accessible en écriture (sinon dans le stockage interne).

## ⏻ Extinction
Le bouton « Éteindre » des réglages éteint le téléphone via `su` si l'appareil est rooté. Sinon, il ouvre le menu d'extinction du système grâce au service d'accessibilité Canto : à activer une fois dans *Réglages Android → Accessibilité → Canto*.

> ⚠️ À faire avant de fermer la boîte qui cache les boutons du téléphone : accorder l'accès aux fichiers et activer le service d'accessibilité, car ces écrans système nécessitent le bouton retour.

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
Voir [TODO.md](TODO.md).
