#!/usr/bin/env bash
# Turns Gradle's "What went wrong" sections from a build log into workflow error annotations,
# so failures are visible in the checks UI and API without opening the raw log.
set -euo pipefail
log="${1:?usage: $0 <gradle-log>}"
[[ -f "$log" ]] || exit 0
awk '
  /^\* What went wrong:/ { grab = 1; msg = ""; next }
  grab && /^\* (Try|Exception is):/ { grab = 0; print msg; next }
  grab && NF { gsub(/%/, "%25"); msg = msg (msg ? "%0A" : "") $0 }
' "$log" | head -n 5 | while IFS= read -r m; do
  [[ -n "$m" ]] && echo "::error title=Gradle build failed::${m:0:4000}"
done
