#!/usr/bin/env bash
# Replace the Modrinth gallery with the screenshots docs/LISTING.md shows, in the order it shows them.
# Needs MODRINTH_TOKEN (a personal access token with the "Write projects" scope; publishing
# alone only needs "Create versions"). MODRINTH_ID defaults to the project slug. DRY_RUN=1 lists and changes nothing.
set -euo pipefail

project="${MODRINTH_ID:-create-engine-engineer}"
api="https://api.modrinth.com/v2"
listing=docs/LISTING.md
dry="${DRY_RUN:-0}"
auth=()
[ -n "${MODRINTH_TOKEN:-}" ] && auth=(-H "Authorization: $MODRINTH_TOKEN")
[ "$dry" = 1 ] || [ -n "${MODRINTH_TOKEN:-}" ] || { echo "MODRINTH_TOKEN is not set" >&2; exit 1; }

# "![alt](.../docs/images/file.png)" lines, first mention of each file wins, icon excluded
mapfile -t entries < <(sed -n 's|^!\[\(.*\)\](.*/docs/images/\([^/)]*\.png\))$|\2\t\1|p' "$listing" | awk -F'\t' '!seen[$1]++')
[ "${#entries[@]}" -gt 0 ] || { echo "no images found in $listing" >&2; exit 1; }

for e in "${entries[@]}"; do
  file="${e%%$'\t'*}"
  [ -f "docs/images/$file" ] || { echo "missing docs/images/$file" >&2; exit 1; }
done

echo "Gallery of $project: ${#entries[@]} images"
if [ "$dry" = 1 ]; then
  printf '  %s\n' "${entries[@]//$'\t'/  -  }"
  exit 0
fi

# Upload the new set first and delete the old one after, so a failure never leaves the gallery empty.
old=$(curl -fsS "${auth[@]}" "$api/project/$project" | jq -r '.gallery[].url')

i=0
for e in "${entries[@]}"; do
  file="${e%%$'\t'*}"; title="${e#*$'\t'}"
  featured=false; [ "$i" -eq 0 ] && featured=true
  echo "upload $file"
  curl -fsS -X POST "${auth[@]}" -H "Content-Type: image/png" --data-binary "@docs/images/$file" \
    --get "$api/project/$project/gallery" \
    --data-urlencode "ext=png" --data-urlencode "featured=$featured" \
    --data-urlencode "title=$title" --data-urlencode "ordering=$i" >/dev/null
  i=$((i + 1))
done

while IFS= read -r url; do
  [ -n "$url" ] || continue
  echo "delete $url"
  curl -fsS -X DELETE "${auth[@]}" --get "$api/project/$project/gallery" --data-urlencode "url=$url" >/dev/null
done <<< "$old"
