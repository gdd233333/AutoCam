"""Synthetic colored-rectangle subjects. No PICD download."""

from __future__ import annotations

import numpy as np

from ml.students.spec import CLASS_NAMES, INPUT_SIZE, NUM_CLASSES, PAD_VALUE


def class_from_center(cx: float, cy: float) -> int:
    thirds = ((0.33, 0.33), (0.67, 0.33), (0.33, 0.67), (0.67, 0.67))
    d_third = min((cx - tx) ** 2 + (cy - ty) ** 2 for tx, ty in thirds)
    d_center = (cx - 0.5) ** 2 + (cy - 0.5) ** 2
    if d_center < 0.01:
        return CLASS_NAMES.index("center")
    if d_third < 0.02:
        return CLASS_NAMES.index("thirds")
    if abs(cx - cy) < 0.08:
        return CLASS_NAMES.index("diagonal")
    return CLASS_NAMES.index("none")


def make_batch(n: int, rng: np.random.Generator | None = None) -> dict[str, np.ndarray]:
    rng = rng or np.random.default_rng(0)
    rgb = np.full((n, 3, INPUT_SIZE, INPUT_SIZE), PAD_VALUE, dtype=np.float32)
    box = np.zeros((n, 4), dtype=np.float32)
    label = np.zeros((n,), dtype=np.int64)
    obj = np.ones((n, 1), dtype=np.float32)
    for i in range(n):
        cx = float(rng.uniform(0.25, 0.75))
        cy = float(rng.uniform(0.25, 0.75))
        w = float(rng.uniform(0.12, 0.35))
        h = float(rng.uniform(0.12, 0.35))
        x0 = int((cx - w / 2) * INPUT_SIZE)
        y0 = int((cy - h / 2) * INPUT_SIZE)
        x1 = int((cx + w / 2) * INPUT_SIZE)
        y1 = int((cy + h / 2) * INPUT_SIZE)
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, INPUT_SIZE), min(y1, INPUT_SIZE)
        color = rng.uniform(0.2, 1.0, size=3).astype(np.float32)
        rgb[i, :, y0:y1, x0:x1] = color[:, None, None]
        box[i] = (cx, cy, w, h)
        label[i] = class_from_center(cx, cy)
    return {"rgb": rgb, "box": box, "label": label, "obj": obj, "num_classes": NUM_CLASSES}
