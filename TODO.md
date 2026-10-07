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
- [ ] Slider volume dans settings
- [ ] limiter luminosité
- [ ] couleurs plus sombres et style pour économiser de la luminosité
- [ ] cadre autour des tuiles (ou image derrière)
- [ ] marge en bas car dernière tuile touche le bord
- [ ] bouton éteindre
- [ ] mieux intégrer settings
- [ ] définition du mot de passe settings lors de la première utilisation
- [ ] améliorer la détection d'une sd card et si pas trouvé utilise local
- [ ] possibilité de transfert bluetooth ou wifi
- [ ] tester l'utilisation sans les boutons du smartphone. (une boîte cache le bouton home, back, menu) donc plus accès

## Idées d'amélioration
- [ ] App en plus pour communiquer avec un autre smartphone via wifi
