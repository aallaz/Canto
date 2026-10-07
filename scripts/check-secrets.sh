#!/usr/bin/env bash
# Vérifie qu'aucun fichier secret ou compromettant n'est envoyé sur GitHub.
#
#   scripts/check-secrets.sh            fichiers suivis par git (état actuel)
#   scripts/check-secrets.sh <a>..<b>   lignes ajoutées par ces commits + fichiers présents en <b>
#
# Utilisé par le hook .githooks/pre-push et par la compilation GitHub.
# Un faux positif se marque en ajoutant « secret-scan: ignore » sur la ligne concernée.
set -euo pipefail

range="${1:-}"
problems=0

report() {
  echo "  ✘ $1" >&2
  problems=$((problems + 1))
}

# 1. Fichiers interdits (clés, configuration locale, audio, gros fichiers).
if [ -n "$range" ]; then
  tip="${range##*..}"
  files=$(git ls-tree -r --name-only "$tip")
else
  tip=""
  files=$(git ls-files)
fi

forbidden_names='(^|/)(local\.properties|keystore\.properties|google-services\.json|\.env(\..*)?|id_(rsa|ed25519|ecdsa)(\.pub)?)$'
forbidden_ext='\.(jks|keystore|p12|pfx|pem|key|apk|aab|mp3|m4a|wav|aac|ogg|flac)$'

while IFS= read -r file; do
  [ -z "$file" ] && continue
  if echo "$file" | grep -Eiq "$forbidden_names"; then
    report "fichier de configuration locale ou de clé : $file"
  elif echo "$file" | grep -Eiq "$forbidden_ext"; then
    report "fichier qui ne doit pas être versionné (clé, APK ou audio) : $file"
  fi
  if [ -n "$tip" ]; then
    size=$(git cat-file -s "$tip:$file" 2>/dev/null || echo 0)
  else
    size=$(stat -c %s "$file" 2>/dev/null || stat -f %z "$file" 2>/dev/null || echo 0)
  fi
  if [ "$size" -gt 5000000 ]; then
    report "fichier de plus de 5 Mo : $file"
  fi
done <<< "$files"

# 2. Contenu ressemblant à un secret.
patterns=(
  '-----BEGIN ([A-Z]+ )?PRIVATE KEY-----'
  'gh[pousr]_[A-Za-z0-9]{36,}'
  'github_pat_[A-Za-z0-9_]{40,}'
  'AKIA[0-9A-Z]{16}'
  'AIza[0-9A-Za-z_-]{35}'
  'xox[abprs]-[A-Za-z0-9-]{10,}'
  'sk-[A-Za-z0-9_-]{20,}'
  '(password|passwd|secret|token|api_?key)[A-Za-z_]*["'"'"']?[[:space:]]*[:=][[:space:]]*["'"'"'][^"'"'"'$[:space:]]{6,}["'"'"']'
)
regex=$(IFS='|'; echo "${patterns[*]}")

if [ -n "$range" ]; then
  content=$(git diff --no-color --unified=0 "$range" -- . ':(exclude)scripts/check-secrets.sh' | grep -E '^\+' | grep -v '^+++' || true)
else
  content=$(git grep -I -n -E -i -e "$regex" -- . ':(exclude)scripts/check-secrets.sh' || true)
fi

matches=$(echo "$content" | grep -E -i -e "$regex" | grep -v 'secret-scan: ignore' || true)
if [ -n "$matches" ]; then
  while IFS= read -r line; do
    report "contenu suspect : ${line:0:160}"
  done <<< "$matches"
fi

if [ "$problems" -gt 0 ]; then
  echo "Vérification des secrets : $problems problème(s). Rien n'a été envoyé." >&2
  exit 1
fi
echo "Vérification des secrets : OK"
