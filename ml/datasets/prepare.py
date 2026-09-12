#!/usr/bin/env python3
"""Scan CADB / PICD / a flat image folder → index.jsonl."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.datasets.fold import primary_cadb_class
from ml.students.spec import CLASS_NAMES

IMG_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def _as_box(vals) -> list[float] | None:
    if not isinstance(vals, (list, tuple)) or len(vals) < 4:
        return None
    x0, y0, x1, y1 = [float(v) for v in vals[:4]]
    w, h = abs(x1 - x0), abs(y1 - y0)
    if w < 8 or h < 8:
        return None
    return [min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)]


def _largest_box(elements: dict) -> list[float] | None:
    best = None
    best_area = 0.0
    for items in elements.values():
        if not isinstance(items, list):
            continue
        for item in items:
            box = _as_box(item)
            if box is None:
                continue
            area = (box[2] - box[0]) * (box[3] - box[1])
            if area > best_area:
                best_area = area
                best = box
    return best


def scan_cadb(root: Path) -> list[dict]:
    elements_path = root / "composition_elements.json"
    images = root / "images"
    if not elements_path.exists():
        return []
    payload = json.loads(elements_path.read_text(encoding="utf-8"))
    rows = []
    for name, classes in payload.items():
        path = images / name
        if not path.exists():
            alt = root / name
            path = alt if alt.exists() else path
        if not path.exists():
            continue
        if not isinstance(classes, dict):
            classes = {}
        label = primary_cadb_class(classes)
        rows.append(
            {
                "path": str(path.resolve()),
                "source": "cadb",
                "label": label,
                "label_i": CLASS_NAMES.index(label),
                "box_xyxy": _largest_box(classes),
            }
        )
    return rows


def _picd_find(root: Path, folder: str, name: str) -> Path | None:
    for base in (root / "single_labels" / folder / name, root / "multi_labels" / folder / name):
        if base.exists():
            return base
    return None


def scan_picd(root: Path) -> list[dict]:
    """Official PICD layout: labels_PICD.csv + single_labels/ + multi_labels/."""
    from ml.datasets.fold import fold_label

    csv_path = root / "labels_PICD.csv"
    if csv_path.exists():
        import csv

        rows = []
        with csv_path.open(encoding="utf-8") as f:
            for item in csv.DictReader(f):
                name = item.get("img_id") or ""
                folder = item.get("folder_name") or ""
                path = _picd_find(root, folder, name)
                if path is None:
                    continue
                raw = item.get("category_abbre") or item.get("folder_name") or "none"
                label = fold_label(raw)
                rows.append(
                    {
                        "path": str(path.resolve()),
                        "source": "picd",
                        "label": label,
                        "label_i": CLASS_NAMES.index(label),
                        "box_xyxy": None,
                    }
                )
        return rows
    return []


def scan_folder(root: Path, source: str = "folder") -> list[dict]:
    rows = []
    for path in sorted(root.rglob("*")):
        if path.suffix.lower() not in IMG_EXT:
            continue
        parent = path.parent.name.lower()
        label = parent if parent in CLASS_NAMES else "none"
        if label == "none":
            from ml.datasets.fold import fold_label

            label = fold_label(parent)
        rows.append(
            {
                "path": str(path.resolve()),
                "source": source,
                "label": label,
                "label_i": CLASS_NAMES.index(label) if label in CLASS_NAMES else CLASS_NAMES.index("none"),
                "box_xyxy": None,
            }
        )
    return rows


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--cadb", type=Path, default=None)
    p.add_argument("--picd", type=Path, default=None)
    p.add_argument("--folder", type=Path, default=None)
    p.add_argument("--out", type=Path, default=ROOT / "ml" / "datasets" / "index.jsonl")
    args = p.parse_args()
    rows: list[dict] = []
    if args.cadb:
        rows.extend(scan_cadb(args.cadb))
    if args.picd:
        rows.extend(scan_picd(args.picd))
    if args.folder:
        rows.extend(scan_folder(args.folder))
    raw = ROOT / "ml" / "datasets" / "raw"
    if not rows:
        if (raw / "cadb").exists():
            rows.extend(scan_cadb(raw / "cadb"))
        if (raw / "picd").exists():
            rows.extend(scan_picd(raw / "picd"))
        if (raw / "folder").exists():
            rows.extend(scan_folder(raw / "folder"))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    counts: dict[str, int] = {}
    for row in rows:
        counts[row["label"]] = counts.get(row["label"], 0) + 1
    print(f"wrote {len(rows)} records -> {args.out}")
    print("class counts:", counts)


if __name__ == "__main__":
    main()
