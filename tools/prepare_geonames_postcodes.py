#!/usr/bin/env python3
"""Convert a GeoNames postal-code TXT/ZIP export into KalPlan's compact TSV.

Input columns follow the GeoNames postal-code dump format. This script performs
no network requests. Download and verify the chosen source snapshot separately.
"""

from __future__ import annotations

import argparse
import csv
import io
import pathlib
import zipfile


def lines_from(path: pathlib.Path):
    if path.suffix.lower() == ".zip":
        with zipfile.ZipFile(path) as archive:
            txt_names = [n for n in archive.namelist() if n.lower().endswith(".txt")]
            if len(txt_names) != 1:
                raise SystemExit(f"Expected exactly one .txt file in ZIP, found {txt_names}")
            with archive.open(txt_names[0]) as raw:
                yield from io.TextIOWrapper(raw, encoding="utf-8")
    else:
        with path.open("r", encoding="utf-8") as handle:
            yield from handle


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=pathlib.Path)
    parser.add_argument("output", type=pathlib.Path)
    parser.add_argument("--country", default="DE")
    args = parser.parse_args()

    rows = []
    reader = csv.reader(lines_from(args.input), delimiter="\t")
    for p in reader:
        if len(p) < 11 or p[0] != args.country:
            continue
        country, postal, place = p[0], p[1], p[2]
        admin1 = p[3]
        lat, lon = p[9], p[10]
        accuracy = p[11] if len(p) > 11 else ""
        if not postal or not place or not lat or not lon:
            continue
        rows.append((country, postal, place, admin1, lat, lon, accuracy))

    rows.sort(key=lambda r: (r[0], r[1], r[2], r[3]))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as out:
        out.write("# countryCode\tpostalCode\tplaceName\tregion\tlatitude\tlongitude\taccuracy\n")
        writer = csv.writer(out, delimiter="\t", lineterminator="\n")
        writer.writerows(rows)

    print(f"Wrote {len(rows)} records to {args.output}")


if __name__ == "__main__":
    main()
