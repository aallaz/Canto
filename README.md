# Canto, boîte à histoires

Une application Android conçue spécifiquement pour transformer un vieux smartphone (ex: Lenovo K6 sous LineageOS) en une boîte à histoires hors-ligne pour enfant.

L'application sert également de launcher pour verrouiller l'appareil et empêcher la navigation dans les paramètres Android.

## Fonctionnalités
- Navigation en deux niveaux : un menu principal (tuiles Histoires, Musique, Réglages), puis les tuiles de la rubrique choisie. On passe de l'un à l'autre en touchant les tuiles ou en glissant le doigt (vers la gauche : menu principal > rubrique > écran noir ; vers la droite : retour), avec une transition animée.
- Barre d'état sur le menu principal et le lecteur : batterie (pourcentage au toucher), soleil/lune pour passer du mode clair au mode sombre, Wi-Fi (coloré pendant un transfert), enceinte Bluetooth, barre de volume centrée, ampoule pour l'écran noir, mise à jour disponible.
- Mode clair multicolore ou mode sombre uni bleu-vert foncé, où les pochettes s'affichent en bichromie bleu et blanc. Le fond reste toujours sombre.
- Écran noir : l'icône ampoule, ou automatiquement après un délai sans toucher (2 min par défaut, de 10 s à 10 min dans les réglages), assombrit l'écran en fondu et met le rétroéclairage au minimum ; la lecture continue et un toucher n'importe où rallume l'écran.
- Interface enfant avec une grille de grandes tuiles encadrées, thème sombre pour économiser l'écran.
- Lecteur simplifié avec pochette, piste en cours et boutons précédent / lecture / suivant.
- Mode kiosque : lancement en tant que page d'accueil système, bouton retour désactivé.
- Lecture hors ligne à partir de fichiers audio locaux (mp3, m4a, wav, aac, ogg).
- Réglages parent (tuile Réglages du menu principal), protégés par un code à 4 chiffres choisi à la première ouverture :
  - volume maximal (la barre de volume et les boutons du téléphone ne le dépassent pas ; pendant le réglage, le son passe à ce maximum pour l'entendre, puis revient 2 s après à sa position dans la barre du haut), luminosité (plafonnée à 60 %), délai de l'écran noir ;
  - dossiers Histoires détectés et nouvelle recherche ;
  - transfert Wi-Fi ;
  - enceinte Bluetooth (recherche, appairage, connexion) ;
  - mise à jour de l'application ;
  - changement du code, extinction du téléphone, sortie de l'application.

## Carte SD et stockage interne
Canto cherche les dossiers `Histoires` et `Musique` à la racine de chaque carte SD montée, puis dans le stockage interne, et affiche le contenu de tous les dossiers trouvés (carte SD en premier). Un sous-dossier de `Musique` est un album, comme un sous-dossier de `Histoires` est une histoire. L'insertion ou le retrait d'une carte relance la recherche automatiquement.

Sur Android 11 et plus, il faut accorder « Accès à tous les fichiers » (bouton dans les réglages) pour lire la carte SD, les fichiers `.nfo` et recevoir les transferts.

## Interface web (bibliothèque et transfert)
1. Réglages → « Démarrer le transfert ».
2. Sur un ordinateur ou un téléphone connecté au même Wi-Fi, ouvrir l'adresse affichée (ex. `http://192.168.1.20:8080`).
3. Saisir le code parent. L'onglet **Bibliothèque** montre les histoires de la boîte en tuiles (pochette, titre, pistes) ; l'écoute n'est pas possible depuis le navigateur. Un clic sur une tuile affiche ses pistes et permet de **télécharger l'album** (fichier .zip) ou de **le supprimer** de la boîte.
4. Onglet **Ajouter une histoire** : glisser un dossier d'histoire (ou plusieurs, ou tout le dossier `Histoires`) ou utiliser « Choisir un dossier… ».
5. La page vérifie chaque histoire avant l'envoi :
   - fichiers audio présents (sinon l'histoire est décochée) et ordre de lecture ;
   - image `cover`/`folder` (.jpg, .jpeg, .png) ; à défaut, une autre image du dossier est envoyée comme `cover` ;
   - fichiers ignorés (types non pris en charge), dossier déjà présent sur la boîte.
6. « Envoyer » : les fichiers déjà présents (même nom, même taille) ne sont pas renvoyés. Les sous-dossiers (CD1/, CD2/…) sont aplatis en `CD1_piste.mp3`.

Les fichiers sont écrits dans le premier dossier Histoires réellement accessible en écriture (carte SD, sinon stockage interne) ; le dossier choisi est affiché en haut de l'onglet. Si aucun ne l'est, la page indique la cause, en général l'accès aux fichiers non accordé à Canto :

```
adb shell appops set --uid com.example.canto MANAGE_EXTERNAL_STORAGE allow                 # Android 11+
adb shell pm grant com.example.canto android.permission.WRITE_EXTERNAL_STORAGE          # Android 10 et moins
```

Ces autorisations sont perdues à chaque désinstallation.

## Mises à jour
GitHub compile l'APK à chaque push et le publie dans la release `apk-latest` avec un fichier `version.json`. Canto vérifie cette release au démarrage puis toutes les 6 h ; quand une version plus récente existe, « MAJ » s'affiche dans la barre d'état et le bouton **Installer** apparaît dans les réglages.

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

## Enceinte Bluetooth
Réglages → *Enceinte Bluetooth* : activer le Bluetooth, mettre l'enceinte en mode appairage, **Rechercher**, puis **Appairer**. Une enceinte déjà appairée se connecte avec **Connecter**. Avant Android 12, Android exige l'autorisation « Position » pour rechercher des appareils Bluetooth : elle n'est demandée qu'au moment d'appuyer sur **Rechercher**, et Canto n'utilise pas la position. Les enceintes déjà appairées se connectent sans elle. Si la connexion échoue depuis Canto, **Réglages Android** ouvre l'écran Bluetooth du système.

## Extinction
Le bouton « Éteindre » des réglages éteint le téléphone via `su` si l'appareil est rooté. Sinon, il ouvre le menu d'extinction du système grâce au service d'accessibilité Canto : à activer une fois dans *Réglages Android → Accessibilité → Canto*.

> À faire avant de fermer la boîte qui cache les boutons du téléphone : accorder l'accès aux fichiers et activer le service d'accessibilité, car ces écrans système nécessitent le bouton retour.

## Organisation des dossiers
L'application scanne un répertoire racine défini (par exemple /sdcard/Histoires/ ou /storage/emulated/0/Histoires/). Chaque sous-dossier représente une tuile.

Structure requise :
```text
/sdcard/Histoires/
  ├── 01_Boucle_d_or/
  │   ├── cover.jpg
  │   ├── piste_01.mp3
  │   └── piste_02.mp3
/sdcard/Musique/
  ├── Comptines/
  │   ├── cover.jpg
  │   ├── chanson1.m4a
  │   └── chanson2.m4a
```

## Vérification avant envoi sur GitHub
Le script `scripts/check-secrets.sh` refuse les clés de signature, fichiers de configuration locale (`local.properties`…), APK, fichiers audio, fichiers de plus de 5 Mo et tout contenu ressemblant à un mot de passe ou un jeton. Il tourne :

- avant chaque `git push`, via le hook `.githooks/pre-push`, à activer une fois par clone :
  ```
  git config core.hooksPath .githooks
  ```
- au début de chaque compilation GitHub, en filet de sécurité.

Un faux positif se marque en ajoutant `secret-scan: ignore` sur la ligne concernée.

## Prochaines étapes
Voir [TODO.md](TODO.md).
