#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "Uso: bash scripts/release-notes.sh <version>" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHANGELOG="$ROOT_DIR/CHANGELOG.md"
FEATURES="$ROOT_DIR/FEATURES.md"
SECTION="$(awk -v heading="## [$VERSION]" '
  index($0, heading) == 1 { found=1; capture=1; next }
  capture && /^## \[/ { exit }
  capture { print }
  END { if (!found) exit 2 }
' "$CHANGELOG")" || {
  echo "Falta la sección ## [$VERSION] en CHANGELOG.md" >&2
  exit 1
}

if [[ -z "${SECTION//[[:space:]]/}" ]]; then
  echo "La sección de la versión $VERSION está vacía en CHANGELOG.md" >&2
  exit 1
fi

printf '# AnimeAV1 Android %s\n\n' "$VERSION"
printf '## Cambios de esta versión\n%s\n\n' "$SECTION"
printf '## Resumen de funcionalidades\n\n'
sed '1{/^# /d;}' "$FEATURES"
