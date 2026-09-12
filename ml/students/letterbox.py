"""Letterbox + pad to 256, pad value 114/255. Boxes follow the same transform."""

from __future__ import annotations

import numpy as np

from ml.students.spec import INPUT_SIZE, PAD_VALUE


def letterbox_rgb(rgb: np.ndarray, size: int = INPUT_SIZE, pad_value: float = PAD_VALUE) -> tuple[np.ndarray, dict]:
    """rgb: HxWx3 float32 0-1 or uint8. Returns size x size x 3 float32 0-1 and meta."""
    if rgb.dtype != np.float32:
        rgb = rgb.astype(np.float32) / 255.0
    h, w = rgb.shape[:2]
    scale = size / max(h, w)
    nh, nw = int(round(h * scale)), int(round(w * scale))
    # nearest/bilinear via numpy: simple resize with zoom
    ys = (np.arange(nh) + 0.5) * h / nh - 0.5
    xs = (np.arange(nw) + 0.5) * w / nw - 0.5
    ys = np.clip(ys, 0, h - 1)
    xs = np.clip(xs, 0, w - 1)
    y0 = np.floor(ys).astype(np.int32)
    x0 = np.floor(xs).astype(np.int32)
    y1 = np.clip(y0 + 1, 0, h - 1)
    x1 = np.clip(x0 + 1, 0, w - 1)
    wy = ys - y0
    wx = xs - x0
    top = rgb[y0][:, x0] * (1 - wx)[None, :, None] + rgb[y0][:, x1] * wx[None, :, None]
    bot = rgb[y1][:, x0] * (1 - wx)[None, :, None] + rgb[y1][:, x1] * wx[None, :, None]
    resized = top * (1 - wy)[:, None, None] + bot * wy[:, None, None]
    canvas = np.full((size, size, 3), pad_value, dtype=np.float32)
    top_pad = (size - nh) // 2
    left_pad = (size - nw) // 2
    canvas[top_pad : top_pad + nh, left_pad : left_pad + nw] = resized
    meta = {
        "scale": scale,
        "pad_x": left_pad,
        "pad_y": top_pad,
        "src_w": w,
        "src_h": h,
        "resized_w": nw,
        "resized_h": nh,
    }
    return canvas, meta


def box_xyxy_to_cxcywh_letterbox(xyxy: np.ndarray, meta: dict, size: int = INPUT_SIZE) -> np.ndarray:
    """xyxy in source pixels → cx,cy,w,h relative to letterbox canvas."""
    x0, y0, x1, y1 = [float(v) for v in xyxy]
    scale = meta["scale"]
    px, py = meta["pad_x"], meta["pad_y"]
    x0 = x0 * scale + px
    x1 = x1 * scale + px
    y0 = y0 * scale + py
    y1 = y1 * scale + py
    cx = ((x0 + x1) / 2.0) / size
    cy = ((y0 + y1) / 2.0) / size
    w = (x1 - x0) / size
    h = (y1 - y0) / size
    return np.array([cx, cy, w, h], dtype=np.float32)
