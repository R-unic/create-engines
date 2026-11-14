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

# Modrinth names an uploaded image after the SHA-1 of its bytes and answers 400 to a second copy of an image the page
# already has, so a sync run again must not upload what is there. Images already on the page are kept and only given
# their title, featured flag and place; the rest are uploaded; whatever is left over is deleted last, so a failure
# never leaves the gallery empty.
gallery() { curl -fsS "${auth[@]}" "$api/project/$project" | jq -c '.gallery'; }
current=$(gallery)
wanted=()
unmatched=0

i=0
for e in "${entries[@]}"; do
  file="${e%%$'\t'*}"; title="${e#*$'\t'}"
  featured=false; [ "$i" -eq 0 ] && featured=true
  sha=$(sha1sum "docs/images/$file" | cut -d' ' -f1)
  wanted+=("$sha")
  url=$(jq -r --arg sha "$sha" '[.[] | select((.raw_url // "") | contains($sha)) | .url][0] // empty' <<< "$current")
  if [ -n "$url" ]; then
    echo "keep   $file"
    curl -fsS -X PATCH "${auth[@]}" --get "$api/project/$project/gallery" --data-urlencode "url=$url" \
      --data-urlencode "featured=$featured" --data-urlencode "title=$title" --data-urlencode "ordering=$i" >/dev/null
  else
    echo "upload $file"
    # --get would turn the image body into query text, so the query string is built by hand
    q=$(jq -rn --arg t "$title" '$t | @uri')
    body=$(mktemp)
    code=$(curl -sS -o "$body" -w '%{http_code}' -X POST "${auth[@]}" -H "Content-Type: image/png" --data-binary "@docs/images/$file" \
      "$api/project/$project/gallery?ext=png&featured=$featured&title=$q&ordering=$i")
    if [ "$code" -ge 200 ] && [ "$code" -lt 300 ]; then
      :
    elif [ "$code" = 400 ] && grep -qi 'duplicate\|already' "$body"; then
      # Already on the page under a name that is not the file's SHA-1: leave it, and do not delete anything at the end
      echo "  $file is already on the page ($(cat "$body")), leaving it where it is"
      unmatched=$((unmatched + 1))
    else
      echo "upload of $file failed with HTTP $code: $(cat "$body")" >&2
      rm -f "$body"
      exit 1
    fi
    rm -f "$body"
  fi
  i=$((i + 1))
done

if [ "$unmatched" -gt 0 ]; then
  echo "$unmatched image(s) could not be matched by hash, so the old gallery images are left alone" >&2
  exit 0
fi

# Everything that is not one of the wanted images (an older version of a screenshot, a stray duplicate) goes now
wanted_json=$(printf '%s\n' "${wanted[@]}" | jq -R . | jq -sc .)
stale=$(jq -r --argjson wanted "$wanted_json" \
  '.[] | select((.raw_url // "") as $r | $wanted | any(. as $s | $r | contains($s)) | not) | .url' <<< "$current")
while IFS= read -r url; do
  [ -n "$url" ] || continue
  echo "delete $url"
  curl -fsS -X DELETE "${auth[@]}" --get "$api/project/$project/gallery" --data-urlencode "url=$url" >/dev/null
done <<< "$stale"
