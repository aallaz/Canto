# Canto : notes pour le développement

App Android (Kotlin, Jetpack Compose) qui transforme un vieux smartphone (Lenovo K6, LineageOS) en boîte
à histoires pour enfant, en mode kiosque. L'utilisateur échange en français : réponses, commits, README et
TODO en français, sans icônes ni emojis dans le README.

## Où en est le projet
- Fonctionnalités : README.md. Ce qui reste à faire : TODO.md (cases non cochées).
- Code : `app/src/main/java/com/example/canto/` (MainActivity = logique, CantoUi/StatusBar = interface,
  Theme = palettes, Kiosk = mode propriétaire, WifiTransferServer + `assets/upload.html` = page web :8080).
- Branche de travail : `claude/quirky-hopper-rmgu3f`.

## Compiler et vérifier
- Le SDK Android n'est pas téléchargeable dans l'environnement cloud (dl.google.com bloqué) : la vraie
  compilation se fait sur GitHub Actions (`.github/workflows/build-apk.yml`) à chaque push.
- Chaque push publie l'APK et `version.json` dans la release `apk-latest` ; versionCode = numéro du build + 1.
  L'app se met à jour depuis cette release (sans confirmation en mode propriétaire).
- Mettre `[skip ci]` dans le message de commit pour pousser sans compiler (ex. modification du TODO seul).
- APK signé avec la clé de l'utilisateur via les secrets GitHub `CANTO_KEYSTORE_*` : ne jamais créer ni
  committer de clé.
- Avant chaque push : `git config core.hooksPath .githooks` (hook `pre-push` → `scripts/check-secrets.sh`).

## Points d'attention
- Canto est propriétaire de l'appareil (device owner) : ne jamais modifier la liste des apps autorisées en
  mode verrouillé pendant que l'app est verrouillée (cause d'un plantage au lancement, corrigé en 0.1.14).
- Diagnostic d'un plantage sur l'appareil : `adb logcat -d -b crash`.
