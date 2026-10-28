#!/usr/bin/env python3
"""Summarizes an Android lint XML report as workflow annotations: one per issue type, with a
count and the first location, so warnings stay visible even when lint's result comes from cache."""
import collections
import os
import sys
import xml.etree.ElementTree as ET

report = sys.argv[1] if len(sys.argv) > 1 else "android/app/build/reports/lint-results-debug.xml"
if not os.path.exists(report):
    sys.exit(0)

workspace = os.environ.get("GITHUB_WORKSPACE", "")
issues = collections.OrderedDict()
for issue in ET.parse(report).getroot().iter("issue"):
    key = (issue.get("severity"), issue.get("id"))
    issues.setdefault(key, []).append(issue)


def esc(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


for (severity, issue_id), found in sorted(issues.items(), key=lambda kv: -len(kv[1]))[:10]:
    first = found[0]
    loc = first.find("location")
    path = os.path.relpath(loc.get("file"), workspace) if loc is not None and workspace else ""
    line = loc.get("line", "1") if loc is not None else "1"
    level = "error" if severity in ("Error", "Fatal") else "warning"
    print(f"::{level} file={path},line={line},title=Lint {issue_id} ({len(found)})::{esc(first.get('message', ''))}")
