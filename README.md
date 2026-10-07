# Boîte à Histoires (Canto) 🎧🧸

Une application Android conçue spécifiquement pour transformer un vieux smartphone (ex: Lenovo K6 sous LineageOS) en une boîte à histoires hors-ligne pour enfant.

L'application sert également de launcher pour verrouiller l'appareil et empêcher la navigation dans les paramètres Android.

## 🌟 Fonctionnalités
- Barre d'état fixe en haut : batterie, luminosité, volume, Wi-Fi, transfert en cours, enceinte Bluetooth, mise à jour disponible.
- Interface enfant avec une grille de grandes tuiles encadrées, thème sombre pour économiser l'écran.
- Lecteur simplifié avec pochette, piste en cours et boutons précédent / lecture / suivant.
- Mode kiosque : lancement en tant que page d'accueil système, bouton retour désactivé.
- Lecture hors ligne à partir de fichiers audio locaux (mp3, m4a, wav, aac, ogg).
- Réglages parent (⚙ dans la galerie et le lecteur), protégés par un code à 4 chiffres choisi à la première ouverture :
  - volume et luminosité (plafonnée à 60 %, mémorisée) ;
  - dossiers Histoires détectés et nouvelle recherche ;
  - transfert Wi-Fi ;
  - enceinte Bluetooth (recherche, appairage, connexion) ;
  - mise à jour de l'application ;
  - changement du code, extinction du téléphone, sortie de l'application.

## 💾 Carte SD et stockage interne
Canto cherche un dossier `Histoires` à la racine de chaque carte SD montée, puis dans le stockage interne, et affiche les histoires de tous les dossiers trouvés (carte SD en premier). L'insertion ou le retrait d'une carte relance la recherche automatiquement.

Sur Android 11 et plus, il faut accorder « Accès à tous les fichiers » (bouton dans les réglages) pour lire la carte SD, les fichiers `.nfo` et recevoir les transferts.

## 📶 Interface web (bibliothèque et transfert)
1. Réglages ⚙ → « Démarrer le transfert ».
2. Sur un ordinateur ou un téléphone connecté au même Wi-Fi, ouvrir l'adresse affichée (ex. `http://192.168.1.20:8080`).
3. Saisir le code parent. L'onglet **Bibliothèque** montre les histoires de la boîte en tuiles (pochette, titre, pistes) ; l'écoute n'est pas possible depuis le navigateur.
4. Onglet **Ajouter une histoire** : glisser un dossier d'histoire (ou plusieurs, ou tout le dossier `Histoires`) ou utiliser « Choisir un dossier… ».
5. La page vérifie chaque histoire avant l'envoi :
   - fichiers audio présents (sinon l'histoire est décochée) et ordre de lecture ;
   - image `cover`/`folder` (.jpg, .jpeg, .png) ; à défaut, une autre image du dossier est envoyée comme `cover` ;
   - fichiers ignorés (types non pris en charge), dossier déjà présent sur la boîte.
6. « Envoyer » : les fichiers déjà présents (même nom, même taille) ne sont pas renvoyés. Les sous-dossiers (CD1/, CD2/…) sont aplatis en `CD1_piste.mp3`.

Les fichiers sont écrits dans le premier dossier Histoires accessible en écriture (sinon dans le stockage interne).

## ⬆ Mises à jour
GitHub compile l'APK à chaque push et le publie dans la release `apk-latest` avec un fichier `version.json`. Canto vérifie cette release au démarrage puis toutes les 6 h ; quand une version plus récente existe, « ⬆ MAJ » s'affiche dans la barre d'état et le bouton **Installer** apparaît dans les réglages.

Pour qu'Android accepte la mise à jour, chaque APK doit être signé avec **la même clé**. À faire une seule fois :

1. Créer la clé (Java fournit `keytool`) et la garder précieusement, hors du dépôt :
   ```
   keytool -genkeypair -v -keystore canto.keystore -alias canto -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 canto.keystore > canto.keystore.b64      # macOS : base64 -i canto.keystore -o canto.keystore.b64
   ```
2. Sur GitHub : *Settings → Secrets and variables → Actions → New repository secret* :
   - `CANTO_KEYSTORE_B64` : contenu de `canto.keystore.b64`
   - `CANTO_KEYSTORE_PASSWORD` : mot de passe du keystore
   - `CANTO_KEY_ALIAS` : `canto`
   - `CANTO_KEY_PASSWORD` : mot de passe de la clé (si différent)
3. Relancer la compilation, désinstaller une dernière fois la version installée, installer le nouvel APK. Les suivantes s'installeront par-dessus.

Canto doit aussi être autorisé à installer des applications (proposé au premier essai, ou `adb shell appops set com.example.canto REQUEST_INSTALL_PACKAGES allow`). Android demande une confirmation à chaque installation, sauf à partir d'Android 12 une fois que Canto s'est mis à jour lui-même une première fois.

## 🔈 Enceinte Bluetooth
Réglages ⚙ → *Enceinte Bluetooth* : activer le Bluetooth, mettre l'enceinte en mode appairage, **Rechercher**, puis **Appairer**. Une enceinte déjà appairée se connecte avec **Connecter**. Avant Android 12, la recherche demande l'autorisation de localisation (et parfois que la localisation soit activée). Si la connexion échoue depuis Canto, **Réglages Android** ouvre l'écran Bluetooth du système.

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
