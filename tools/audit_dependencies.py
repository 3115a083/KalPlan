#!/usr/bin/env python3
"""Query OSV for the exact Maven runtime versions resolved by Gradle. No credentials."""
import json
from pathlib import Path
import sys
import urllib.request


def audit(inventory: Path, output: Path) -> int:
    packages = []
    for line in inventory.read_text().splitlines():
        name, version = line.split("\t", 1)
        packages.append({"package": {"name": name, "ecosystem": "Maven"}, "version": version})
    findings = []
    for offset in range(0, len(packages), 100):
        batch = packages[offset:offset + 100]
        request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
            data=json.dumps({"queries": batch}).encode(), headers={"Content-Type": "application/json", "User-Agent": "KalPlan-CI"})
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read(5_000_001)
            if len(raw) > 5_000_000:
                raise RuntimeError("Advisory response exceeds limit")
            results = json.loads(raw)["results"]
        if len(results) != len(batch):
            raise RuntimeError("Incomplete advisory response")
        for package, result in zip(batch, results):
            for vulnerability in result.get("vulns", []):
                findings.append({"package": package["package"]["name"], "version": package["version"], "id": vulnerability["id"]})
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"packages_checked": len(packages), "findings": findings}, indent=2) + "\n")
    print(f"OSV: {len(packages)} Maven components checked, {len(findings)} advisory matches.")
    for finding in findings:
        print(f"{finding['id']}: {finding['package']} {finding['version']}")
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(audit(Path(sys.argv[1]), Path(sys.argv[2])))
