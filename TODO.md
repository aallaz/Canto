# Plan de Développement 🚀

## ✅ Étape 1 : Fondation du projet
- [x] Initialiser un projet Android de base avec Kotlin et Jetpack Compose.
- [x] Ajouter les permissions de stockage nécessaires dans le manifest.
- [x] Préparer un thème plein écran adapté à un usage kiosque.

## 🔧 Étape 2 : Logique de base de l'application
- [x] Scanner un dossier racine comme /sdcard/Histoires/.
- [x] Lister les sous-dossiers et repérer les fichiers audio et cover.jpg.
- [x] Afficher une grille de tuiles avec un état vide si aucun contenu n'est trouvé.

## 🎵 Étape 3 : Lecteur audio
- [x] Créer une vue lecteur avec pochette et boutons de navigation.
- [x] Charger automatiquement la première piste d'un dossier.
- [x] Gérer lecture, pause, précédent et suivant.

## 🔒 Étape 4 : Mode kiosque
- [x] Vérifier le comportement en tant que launcher système.
- [x] Empêcher la sortie de l'application via le bouton retour.

## 🧹 Étape 5 : Finitions
- [x] Filtrer les fichiers audio valides et ignorer les fichiers non lisibles.
- [x] Gérer le keep-awake pendant la lecture.
- [x] Tester l'expérience sur un appareil Android réel.

## Étape Suivante
- [x] Slider volume dans settings
- [x] limiter luminosité (plafonnée à 60 %, réglage mémorisé)
- [x] couleurs plus sombres et style pour économiser de la luminosité
- [x] cadre autour des tuiles (ou image derrière)
- [x] marge en bas car dernière tuile touche le bord
- [x] bouton éteindre (root si dispo, sinon menu d'extinction via le service d'accessibilité)
- [x] mieux intégrer settings (accessibles depuis la galerie et le lecteur, panneau plein écran)
- [x] définition du mot de passe settings lors de la première utilisation
- [x] améliorer la détection d'une sd card et si pas trouvé utilise local
- [x] possibilité de transfert bluetooth ou wifi (Wi-Fi : page web d'envoi, Bluetooth non fait)
- [ ] tester l'utilisation sans les boutons du smartphone. (une boîte cache le bouton home, back, menu) donc plus accès
  - Tout est maintenant accessible dans l'app (⚙ galerie/lecteur, ⌂, éteindre, quitter) : reste à valider sur l'appareil.

## Étape Suivante 2
- [x] mise à jour automatique via release github (vérification toutes les 6 h, installation depuis les réglages ; nécessite la clé de signature dans les secrets GitHub)
- [x] barre horizontale en haut fixe (galerie + lecteur) : % batterie, % luminosité, wifi, volume
- [x] interface web : bibliothèque en tuiles (affichage seulement) + ajout d'histoire (façon Luniistore)
- [x] connexion enceintes bluetooth dans les réglages (à valider sur l'appareil)

## Étape Suivante 3
- [x] logo cochon (icône de l'app et page web)
- [x] interface :8080 : supprimer un album ou le télécharger sur l'ordinateur (.zip)
- [x] état de la batterie qui ne s'affichait pas
- [x] réglages : simple icône au lieu d'un bouton
- [x] barre du haut qui défile avec les tuiles
- [x] erreurs d'envoi (ERR_CONTENT_LENGTH_MISMATCH) : dossier accessible en écriture détecté, message d'erreur clair
- [x] vérification des secrets et fichiers compromettants avant chaque push
- [x] README sans icônes

## Étape Suivante 4
- [x] autorisation de position demandée seulement pour la recherche Bluetooth (exigence d'Android < 12)
- [x] suppression impossible : message clair dans la page (comme pour l'envoi), plus de fenêtre du navigateur
- [x] fenêtre de confirmation avant l'envoi, retour à la bibliothèque une fois l'envoi réussi
- [x] message « Terminé » qui restait affiché
- [x] icône réglages de la même taille que les autres
- [x] barre de volume dans la barre du haut (0 → volume max défini dans les réglages)
- [x] icône écran noir dans la barre du haut (toucher pour rallumer)

## Étape Suivante 5
- [x] test de mise à jour depuis l'app (clé de signature fixe)
- [x] luminosité retirée de la barre du haut (reste dans les réglages)
- [x] écran noir automatique après 2 min sans toucher, délai réglable de 10 s à 10 min
- [x] extinction et rallumage de l'écran en fondu

## Étape Suivante 6
- [x] barre de volume centrée dans la barre du haut
- [x] autorisation d'installer les mises à jour demandée au premier lancement
- [x] après l'autorisation, l'installation de la mise à jour reprend toute seule
- [ ] « page release » qui s'ouvre pendant la mise à jour : à préciser (écran de confirmation d'Android ?)

## Étape Suivante 7
- [x] styles : « Couleurs » (actuel) et « Jaune » uni en mode clair, mode sombre bleu-vert foncé
- [x] barre du haut : batterie sans % (au toucher), soleil/lune pour le mode, ampoule pour l'écran noir
- [x] lecteur : grandes icônes dessinées
- [x] code parent : clavier 3 colonnes × 4 lignes, panneau étroit
- [x] volume max : aperçu au maximum pendant le réglage, retour 2 s après à la même proportion
- [x] réglages compactés (curseurs sur une ligne, boutons transfert / enceinte / mise à jour sur une ligne, sans icônes)

## Mode propriétaire (kiosque)
- [x] Canto propriétaire de l'appareil (activé une fois par ADB) : épinglage sans message, verrouillage Android
      désactivé, Canto écran d'accueil permanent, menu marche/arrêt conservé
- [x] hors mode propriétaire : l'épinglage n'est plus relancé s'il est déjà actif
- [x] réglages : « Retirer kiosque » (avec confirmation) pour redevenir une app désinstallable
- [x] « Quitter vers Android » ouvre les réglages Android (le bouton accueil ramène à Canto)
- [x] mises à jour silencieuses en mode propriétaire (vérifié sur l'appareil)
- [x] plantage au lancement après une mise à jour en mode kiosque : règles du propriétaire appliquées une seule fois
- [x] menu principal : tuiles aux mêmes proportions que les histoires et albums
- [x] rubriques : tuile de retour retirée (glissement ou menu principal)
- [x] titres des tuiles sur 2 lignes, coupés au-delà

## Navigation en deux niveaux
- [x] 1. menu principal avec des tuiles : Histoires, Musique, Réglages (plus tard : communication et autres évolutions)
- [x] 2. menu secondaire : les tuiles du dossier Histoires ou Musique (dossiers séparés), réglages en panneau
- [x] passer de l'un à l'autre en touchant les tuiles ou par un glissement : menu principal > menu secondaire > écran noir
- [x] transition animée (défilement de droite à gauche) entre les niveaux
- [x] barre du haut seulement dans le menu principal (et le lecteur)
- [x] icône réglages retirée de la barre du haut (remplacée par la tuile Réglages)
- [x] style Jaune retiré (Couleurs + mode sombre), barre de volume en blanc comme le reste de la barre

## Styles
- [x] mode sombre : pochettes (cover) affichées en bleu et blanc (bichromie, calculée par le code à l'affichage)
- [x] mode sombre : pochettes plus sombres ; lumières = bleu-vert du cadre / des titres, ombres = couleur du fond de l'app

## Page web
- [x] onglets « Musique » / « Histoires » / « Ajouter + » au lieu de « Bibliothèque »
- [x] mise en page des tuiles et tuile « Ajouter » (déjà bien)
- [x] fiche d'un album : colonne à droite sur toute la hauteur, avec cadre jaune (élargir la page)
- [x] boutons « Télécharger » et « Supprimer » de même hauteur (comme « Télécharger »)
- [x] « Ajouter » selon la rubrique en cours : « Ajouter une histoire » ou « Ajouter un album »,
      avec les boutons [Changer pour Histoires/Musique] [Choisir un dossier] [Choisir des fichiers]

## Bugs
- [x] l'écran se verrouille pendant la lecture (délai Android, 30 min au maximum) : empêcher le verrouillage
      tant que le lecteur joue (FLAG_KEEP_SCREEN_ON est posé, mais l'« écran noir » met le rétroéclairage à 0 :
      à vérifier ; lecture dans un service de premier plan pour qu'elle continue même écran éteint)
- [x] bouton physique marche/arrêt : après un verrouillage puis un déverrouillage, Android affiche à chaque fois
      « L'application est épinglée… Non merci / OK ». Cause : Canto relance l'épinglage d'écran (startLockTask)
      à chaque retour, et sans être propriétaire de l'appareil, Android demande confirmation.
      Piste retenue : Canto « propriétaire de l'appareil » (device owner, via ADB) → vrai mode kiosque sans
      message, verrouillage Android désactivable, mises à jour silencieuses ; avec un bouton dans les réglages
      pour en sortir (sinon l'app devient impossible à désinstaller).

## Réglages et lecteur
- [x] bouton pour activer / désactiver le code parent des réglages
- [x] lecteur : boutons trop hauts, réduire légèrement leur hauteur ; garder des icônes grandes
      (la demande d'origine était d'agrandir les icônes, pas les boutons)

## À faire ensuite
- [x] lecteur : boutons précédent / suivant en bleu au lieu de vert (même bleu-vert que la tuile Musique)
- [x] réglages : réorganiser en boutons et sous-menus plutôt qu'un seul grand panneau
      (son et écran, transfert Wi-Fi, enceinte, mise à jour, dossiers, code et kiosque ; à valider sur l'appareil)

- [x] barre du haut : toucher la batterie affiche le % À LA PLACE de l'icône (même taille, mêmes marges)
      pour ne plus décaler les autres icônes

## Idées d'amélioration (à réfléchir : oui ou non)
- [ ] App en plus pour communiquer avec un autre smartphone via wifi
- [ ] interface web : bouton pour déplacer un dossier de Histoires vers Musique (et inversement)
- [ ] histoires au format Lunii compatibles
- [ ] refaire le logo
