#!/usr/bin/env bash
# Summarizes Android lint warnings from a build log: one annotation per issue type with a count
# and the first location, so warnings stay visible without opening the report.
set -euo pipefail
log="${1:?usage: $0 <gradle-log>}"
[[ -f "$log" ]] || exit 0
{ grep -E ': Warning: .*\[[A-Za-z0-9]+\]$' "$log" || true; } | sort -u |
  sed -E "s#^${GITHUB_WORKSPACE:-}/?##" |
  awk '{ id = $NF; gsub(/[\[\]]/, "", id); n[id]++; if (!(id in first)) first[id] = $0 }
       END { for (id in n) print n[id] "\t" id "\t" first[id] }' |
  sort -rn | head -n 10 | while IFS=$'\t' read -r count id example; do
    echo "::warning title=Lint ${id} (${count})::${example//%/%25}"
  done
