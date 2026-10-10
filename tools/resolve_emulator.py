#!/usr/bin/env python3
"""Select an actually published Android emulator image; never claim an unavailable API test."""
import re
import sys
from pathlib import Path

text = Path(sys.argv[1]).read_text()
requested = sys.argv[2]
packages = set(re.findall(r"system-images;android-(\d+);(default|google_apis);x86_64", text))
if requested == "latest":
    candidates = [(int(api), target) for api, target in packages if 26 <= int(api) <= 37 and target == "google_apis"]
    if not candidates:
        raise SystemExit("No published modern Google APIs x86_64 image found")
    api, target = max(candidates)
else:
    api = int(requested)
    target = "default"
    if (str(api), target) not in packages:
        raise SystemExit(f"Required minimum-API image {api}/{target} unavailable")
with Path(sys.argv[3]).open("a") as output:
    output.write(f"api={api}\ntarget={target}\n")
print(f"Device test: Android API {api}, {target}, x86_64. Compile/target API remains 37.")
