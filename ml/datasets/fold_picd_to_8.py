#!/usr/bin/env python3
"""Fold PICD / CADB composition labels into the 8 runtime classes."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

FOLD = {
    "rule of thirds": "thirds",
    "golden ratio": "thirds",
    "p-rot": "thirds",
    "s-rot": "thirds",
    "center": "center",
    "p-cent": "center",
    "s-cent": "center",
    "diagonal": "diagonal",
    "p-dia": "diagonal",
    "triangle": "triangle",
    "horizontal": "leading_line",
    "vertical": "leading_line",
    "vanishing": "leading_line",
    "radial": "leading_line",
    "symmetric": "symmetric",
    "fill the frame": "fill_frame",
    "fill_frame": "fill_frame",
    "dense": "fill_frame",
    "none": "none",
    "pattern": "none",
    "scatter": "none",
}


def fold_label(raw: str) -> str:
    key = raw.strip().lower()
    if key in FOLD:
        return FOLD[key]
    for src, dst in FOLD.items():
        if src in key:
            return dst
    return "none"


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--in", dest="src", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--column", default="composition")
    args = p.parse_args()
    rows = []
    if args.src.suffix.lower() == ".json":
        payload = json.loads(args.src.read_text(encoding="utf-8"))
        items = payload if isinstance(payload, list) else payload.get("items", [])
        for item in items:
            raw = str(item.get(args.column, item.get("label", "none")))
            item["composition_8"] = fold_label(raw)
            rows.append(item)
        args.out.write_text(json.dumps(rows, indent=2), encoding="utf-8")
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
