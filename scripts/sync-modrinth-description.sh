#!/usr/bin/env bash
# Replace the Modrinth page text with the summary and description in docs/LISTING.md.
# Needs MODRINTH_TOKEN with the "Write projects" scope. MODRINTH_ID defaults to the project slug.
# DRY_RUN=1 prints what would be sent and changes nothing.
set -euo pipefail

project="${MODRINTH_ID:-create-engine-engineer}"
api="https://api.modrinth.com/v2"
listing=docs/LISTING.md
dry="${DRY_RUN:-0}"
[ "$dry" = 1 ] || [ -n "${MODRINTH_TOKEN:-}" ] || { echo "MODRINTH_TOKEN is not set" >&2; exit 1; }

# The summary is the "Short description" row's source row: "Summary (CurseForge, 250 max)"
summary=$(sed -n 's/^| Summary (CurseForge, 250 max) | \(.*\) |$/\1/p' "$listing")
# The description is the markdown fence directly under "## Description"
body=$(awk '/^## Description$/{d=1; next} d && /^```markdown$/{on=1; next} on && /^```$/{exit} on' "$listing")
[ -n "$summary" ] || { echo "no summary found in $listing" >&2; exit 1; }
[ -n "$body" ] || { echo "no description found in $listing" >&2; exit 1; }
[ "${#summary}" -le 256 ] || { echo "summary is over Modrinth's 256 characters" >&2; exit 1; }

echo "Page of $project: summary ${#summary} chars, description ${#body} chars"
if [ "$dry" = 1 ]; then
  echo "$summary"; echo; echo "$body"
  exit 0
fi

jq -n --arg d "$summary" --arg b "$body" '{description: $d, body: $b}' |
  curl -fsS -X PATCH -H "Authorization: $MODRINTH_TOKEN" -H "Content-Type: application/json" \
    --data-binary @- "$api/project/$project" >/dev/null
echo "updated"
