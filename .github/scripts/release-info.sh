#!/usr/bin/env bash
#
# Prépare la publication à partir du CHANGELOG, seule source de la version.
#
#   release-info.sh <CHANGELOG.md> <dernière étiquette publiée, ou vide> <fichier de notes>
#
# Imprime la version à publier (X.Y.Z) et écrit ses notes dans le fichier indiqué.
# Échoue, avec une annotation GitHub qui dit quoi faire, quand :
# - la première section du CHANGELOG n'est pas intitulée « ## X.Y.Z » ;
# - cette section est vide ;
# - la version est déjà publiée, ou plus ancienne que la dernière publiée.
#
# C'est ce dernier contrôle qui fait de chaque push sur prod une décision explicite :
# sans nouvelle section en tête du CHANGELOG, rien ne part.
set -euo pipefail

changelog=$1
latest=${2#v}
notes=$3

version=$(awk '/^## / { print $2; exit }' "$changelog")

if [[ ! $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::error file=$changelog::La première section du CHANGELOG doit s'intituler « ## X.Y.Z » (trouvé : « ${version:-aucune section} »)." >&2
  exit 1
fi

awk -v v="$version" '
  /^## / { if (found) exit; found = ($2 == v); next }
  found { print }
' "$changelog" > "$notes"

if ! grep -q '[^[:space:]]' "$notes"; then
  echo "::error file=$changelog::La section « ## $version » est vide : décris ce qui change pour qui utilise l'application." >&2
  exit 1
fi

if [[ -n $latest ]]; then
  if [[ $version == "$latest" ]]; then
    echo "::error file=$changelog::La version $version est déjà publiée. Ajoute en tête du CHANGELOG une section pour la suivante (par exemple « ## ${version%.*}.$(( ${version##*.} + 1 )) »)." >&2
    exit 1
  fi
  highest=$(printf '%s\n%s\n' "$latest" "$version" | sort -V | tail -1)
  if [[ $highest != "$version" ]]; then
    echo "::error file=$changelog::La version $version est plus ancienne que la dernière publiée ($latest)." >&2
    exit 1
  fi
fi

echo "$version"
