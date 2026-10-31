#!/usr/bin/env bash
# Turns a failed Gradle build log into workflow error annotations, so failures are visible in the
# checks UI and API without opening the raw log: Kotlin compiler errors first, then Gradle's
# "What went wrong" sections. GitHub keeps at most 10 error annotations per step.
set -euo pipefail
log="${1:?usage: $0 <gradle-log>}"
[[ -f "$log" ]] || exit 0

# Kotlin compiler errors: "e: file:///…/android/app/src/…/X.kt:12:5 message"
{ grep -E '^e: file://' "$log" || true; } | sort -u | head -n 8 | while IFS= read -r line; do
  rest="${line#e: file://}"
  rest="${rest#"$GITHUB_WORKSPACE/"}"
  if [[ "$rest" =~ ^(.+):([0-9]+):([0-9]+)\ (.*)$ ]]; then
    echo "::error file=${BASH_REMATCH[1]},line=${BASH_REMATCH[2]},col=${BASH_REMATCH[3]},title=Kotlin::${BASH_REMATCH[4]//%/%25}"
  fi
done

# Android lint errors: "/…/android/app/src/main/X.kt:12: Error: message [IssueId]"
{ grep -E ': Error: ' "$log" || true; } | sort -u | head -n 6 | while IFS= read -r line; do
  rest="${line#"$GITHUB_WORKSPACE/"}"
  if [[ "$rest" =~ ^([^:]+):([0-9]+):\ Error:\ (.*)$ ]]; then
    echo "::error file=${BASH_REMATCH[1]},line=${BASH_REMATCH[2]},title=Lint::${BASH_REMATCH[3]//%/%25}"
  elif [[ "$rest" =~ ^([^:]+):\ Error:\ (.*)$ ]]; then
    echo "::error file=${BASH_REMATCH[1]},title=Lint::${BASH_REMATCH[2]//%/%25}"
  fi
done

# R8 (release shrinking) errors, and the keep rules it suggests for missing classes.
{ grep -E '^ERROR: ' "$log" || true; } | sort -u | head -n 3 | while IFS= read -r line; do
  echo "::error title=R8::${line//%/%25}"
done
for rules in app/build/outputs/mapping/*/missing_rules.txt; do
  [[ -f "$rules" ]] || continue
  body=$(head -c 3500 "$rules" | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')
  echo "::error title=R8 missing rules (${rules})::${body}"
done

awk '
  /^\* What went wrong:/ { grab = 1; msg = ""; next }
  grab && /^\* (Try|Exception is):/ { grab = 0; print msg; next }
  grab && NF { gsub(/%/, "%25"); msg = msg (msg ? "%0A" : "") $0 }
' "$log" | head -n 2 | while IFS= read -r m; do
  if [[ -n "$m" ]]; then echo "::error title=Gradle build failed::${m:0:4000}"; fi
done
exit 0
