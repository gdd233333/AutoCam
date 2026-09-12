#!/usr/bin/env python3
"""Index jsonl + images → teacher npz. CLIP (if installed) + v0 saliency box.

Teachers run offline. Training GPU then loads only the student.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.students.letterbox import box_xyxy_to_cxcywh_letterbox, letterbox_rgb
from ml.students.spec import CLASS_NAMES, INPUT_SIZE
from ml.teachers.clip_teacher import ClipTeacher
from ml.teachers.saliency import box_from_saliency, saliency_map


def _load_rgb(path: Path) -> np.ndarray:
    try:
        from PIL import Image

        img = Image.open(path).convert("RGB")
        return np.asarray(img, dtype=np.float32) / 255.0
    except ImportError:
        import torchvision.io as tvio

        t = tvio.read_image(str(path))
        return t.permute(1, 2, 0).numpy().astype(np.float32) / 255.0


def _pil(path: Path):
    from PIL import Image

    return Image.open(path).convert("RGB")


def _out_dir(out_path: Path) -> Path:
    if out_path.suffix == ".npz":
        return out_path.with_suffix("")
    return out_path


def _flush_shard(out_dir: Path, shard_i: int, rgbs, boxes, labels, objs, logits_all) -> None:
    if not rgbs:
        return
    path = out_dir / f"shard_{shard_i:04d}.npz"
    np.savez(
        path,
        rgb=np.stack(rgbs).astype(np.uint8),
        box=np.stack(boxes).astype(np.float32),
        label=np.array(labels, dtype=np.int64),
        obj=np.array(objs, dtype=np.float32),
        logits=np.stack(logits_all).astype(np.float32),
        classes=np.array(CLASS_NAMES),
    )
    print(f"wrote {path} n={len(rgbs)}")


def run(
    index_path: Path,
    out_path: Path,
    limit: int,
    device: str,
    use_index_label: bool,
    shard_size: int,
) -> None:
    records = []
    with index_path.open(encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    if limit > 0:
        records = records[:limit]
    clip = ClipTeacher(device=device)
    out_dir = _out_dir(out_path)
    out_dir.mkdir(parents=True, exist_ok=True)
    print(f"clip teacher={clip.kind} n={len(records)} shards -> {out_dir} size={shard_size}")
    rgbs, boxes, labels, objs, logits_all = [], [], [], [], []
    shard_i = 0
    kept = 0
    for i, rec in enumerate(records):
        path = Path(rec["path"])
        if not path.exists():
            print(f"skip missing {path}")
            continue
        rgb = _load_rgb(path)
        canvas, meta = letterbox_rgb(rgb)
        if rec.get("box_xyxy"):
            box = box_xyxy_to_cxcywh_letterbox(np.array(rec["box_xyxy"], dtype=np.float32), meta)
            area = float(box[2] * box[3])
            obj = 1.0 if area >= 0.015 else 0.0
        else:
            sal = saliency_map(canvas)
            xyxy, area = box_from_saliency(sal)
            if area < 0.015:
                box = np.array([0.5, 0.5, 0.2, 0.2], dtype=np.float32)
                obj = 0.0
            else:
                box = np.array(
                    [
                        ((xyxy[0] + xyxy[2]) / 2) / INPUT_SIZE,
                        ((xyxy[1] + xyxy[3]) / 2) / INPUT_SIZE,
                        (xyxy[2] - xyxy[0]) / INPUT_SIZE,
                        (xyxy[3] - xyxy[1]) / INPUT_SIZE,
                    ],
                    dtype=np.float32,
                )
                obj = 1.0
        if use_index_label and "label_i" in rec:
            label_i = int(rec["label_i"])
            logit = np.full((8,), -4.0, dtype=np.float32)
            logit[label_i] = 4.0
        elif clip.kind != "heuristic":
            label_i, logit_list = clip.infer_pil(_pil(path))
            logit = np.array(logit_list, dtype=np.float32)
        else:
            from ml.datasets.synthetic import class_from_center

            label_i = class_from_center(float(box[0]), float(box[1]))
            if obj < 0.5:
                label_i = CLASS_NAMES.index("none")
            logit = np.full((8,), -4.0, dtype=np.float32)
            logit[label_i] = 4.0
        rgbs.append((np.transpose(canvas, (2, 0, 1)) * 255.0).round().clip(0, 255).astype(np.uint8))
        boxes.append(box)
        labels.append(label_i)
        objs.append([obj])
        logits_all.append(logit)
        kept += 1
        if (i + 1) % 50 == 0:
            print(f"  {i+1}/{len(records)} kept={kept}")
        if len(rgbs) >= shard_size:
            _flush_shard(out_dir, shard_i, rgbs, boxes, labels, objs, logits_all)
            shard_i += 1
            rgbs, boxes, labels, objs, logits_all = [], [], [], [], []
    _flush_shard(out_dir, shard_i, rgbs, boxes, labels, objs, logits_all)
    (out_dir / "manifest.json").write_text(
        json.dumps({"n": kept, "shard_size": shard_size, "classes": list(CLASS_NAMES)}, indent=2),
        encoding="utf-8",
    )
    print(f"done n={kept} dir={out_dir}")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--index", type=Path, default=ROOT / "ml" / "datasets" / "index.jsonl")
    p.add_argument("--out", type=Path, default=ROOT / "ml" / "teachers" / "viewfinder")
    p.add_argument("--limit", type=int, default=0)
    p.add_argument("--shard-size", type=int, default=1024)
    p.add_argument("--device", default="cpu")
    p.add_argument("--use-index-label", action="store_true", help="prefer CADB/PICD labels over CLIP")
    args = p.parse_args()
    if not args.index.exists():
        raise SystemExit(f"missing {args.index}; run ml/datasets/prepare.py")
    run(args.index, args.out, args.limit, args.device, args.use_index_label, args.shard_size)


if __name__ == "__main__":
    main()
