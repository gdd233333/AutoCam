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


def scan_picd(root: Path) -> list[dict]:
    """PICD: {images/, labels.json or annotations.json} with composition field."""
    rows = []
    for cand in (root / "labels.json", root / "annotations.json", root / "picd.json"):
        if not cand.exists():
            continue
        payload = json.loads(cand.read_text(encoding="utf-8"))
        items = payload if isinstance(payload, list) else payload.get("images") or payload.get("items") or []
        if isinstance(payload, dict) and not items:
            items = [{"id": k, **v} if isinstance(v, dict) else {"id": k, "label": v} for k, v in payload.items()]
        for item in items:
            if not isinstance(item, dict):
                continue
            name = item.get("file") or item.get("image") or item.get("id") or item.get("filename")
            if not name:
                continue
            path = Path(name) if Path(name).is_absolute() else root / "images" / Path(name).name
            if not path.exists():
                path = root / Path(name).name
            if not path.exists():
                continue
            raw = str(item.get("composition") or item.get("label") or item.get("category") or "none")
            from ml.datasets.fold import fold_label

            label = fold_label(raw)
            rows.append(
                {
                    "path": str(path.resolve()),
                    "source": "picd",
                    "label": label,
                    "label_i": CLASS_NAMES.index(label),
                    "box_xyxy": item.get("bbox") or item.get("box_xyxy"),
                }
            )
        break
    return rows


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
