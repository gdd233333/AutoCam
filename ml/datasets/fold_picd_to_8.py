#!/usr/bin/env python3
"""CLI for folding PICD / CADB labels. Logic lives in fold.py."""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.datasets.fold import fold_label


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--in", dest="src", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--column", default="composition")
    args = p.parse_args()
    if args.src.suffix.lower() == ".json":
        payload = json.loads(args.src.read_text(encoding="utf-8"))
        items = payload if isinstance(payload, list) else payload.get("items", [])
        for item in items:
            raw = str(item.get(args.column, item.get("label", "none")))
            item["composition_8"] = fold_label(raw)
        args.out.write_text(json.dumps(items, indent=2), encoding="utf-8")
        return
    with args.src.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        fieldnames = list(reader.fieldnames or []) + ["composition_8"]
        out_rows = []
        for row in reader:
            row["composition_8"] = fold_label(row.get(args.column, "none"))
            out_rows.append(row)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(out_rows)


if __name__ == "__main__":
    main()
