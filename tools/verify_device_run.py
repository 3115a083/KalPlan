#!/usr/bin/env python3
"""Require actual execution, not a successful Gradle invocation with zero tests."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

path = Path(sys.argv[1])
text = path.read_text()
success = re.search(r"OK\s*\(5\s+tests?\)", text) is not None and not any(
    failure in text for failure in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed", "shortMsg="))
suite = ET.Element("testsuite", name="KalPlanDeviceSmoke", tests="5", failures="0" if success else "5")
for name in ("syntheticDetailSafeConfirmationAndModernSettings", "guidedProfileKeepsBothReplyTemplatesAfterSaving", "inputEditsKeepCaretAndCommitOnlyOnApply", "encryptedBackupRestoreKeepsCredentialsAndSendFences", "swipeDetailBackReturnsToSameSwipeAndMonthCanJumpYears"):
    case = ET.SubElement(suite, "testcase", classname="cc.stkmn.kalplan.AppSmokeTest", name=name)
    if not success:
        ET.SubElement(case, "failure", message="Instrumented UI validation incomplete or failed").text = text
ET.ElementTree(suite).write(path.with_name("TEST-KalPlanDeviceSmoke.xml"), encoding="utf-8", xml_declaration=True)
if not success:
    raise SystemExit("Device smoke failed or did not execute exactly five tests. Inspect instrumentation.txt.")
print("Device smoke: 5 executed, 0 failures; safe confirmation/settings and saved profile reply templates.")
