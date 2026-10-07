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

## Idées d'amélioration
- [ ] App en plus pour communiquer avec un autre smartphone via wifi
