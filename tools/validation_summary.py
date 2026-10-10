#!/usr/bin/env python3
from pathlib import Path
from collections import Counter
import xml.etree.ElementTree as ET

reports = list(Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
counts = Counter()
for path in reports:
    suite = ET.parse(path).getroot()
    for key in ("tests", "failures", "errors", "skipped"):
        counts[key] += int(suite.get(key, "0"))
print(f"Unit tests: {counts['tests']}, failures={counts['failures']}, errors={counts['errors']}, skipped={counts['skipped']}.")
if not counts["tests"] or counts["failures"] or counts["errors"]:
    raise SystemExit("Unit validation incomplete or failed")
lint = Path("app/build/reports/lint-results-debug.xml")
if lint.exists():
    issues = Counter(item.get("severity", "?") for item in ET.parse(lint).getroot().iter("issue"))
    print("Android Lint: " + ", ".join(f"{severity}={count}" for severity, count in sorted(issues.items())))
