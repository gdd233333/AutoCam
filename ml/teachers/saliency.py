"""v0 center-surround + Sobel saliency box. Optional U2-Net if a .pth is passed."""

from __future__ import annotations

import numpy as np


def _sobel_energy(gray: np.ndarray) -> np.ndarray:
    kx = np.array([[1, 0, -1], [2, 0, -2], [1, 0, -1]], dtype=np.float32)
    ky = kx.T
    pad = np.pad(gray, 1, mode="edge")
    gx = (
        kx[0, 0] * pad[:-2, :-2]
        + kx[0, 1] * pad[:-2, 1:-1]
        + kx[0, 2] * pad[:-2, 2:]
        + kx[1, 0] * pad[1:-1, :-2]
        + kx[1, 1] * pad[1:-1, 1:-1]
        + kx[1, 2] * pad[1:-1, 2:]
        + kx[2, 0] * pad[2:, :-2]
        + kx[2, 1] * pad[2:, 1:-1]
        + kx[2, 2] * pad[2:, 2:]
    )
    gy = (
        ky[0, 0] * pad[:-2, :-2]
        + ky[0, 1] * pad[:-2, 1:-1]
        + ky[0, 2] * pad[:-2, 2:]
        + ky[1, 0] * pad[1:-1, :-2]
        + ky[1, 1] * pad[1:-1, 1:-1]
        + ky[1, 2] * pad[1:-1, 2:]
        + ky[2, 0] * pad[2:, :-2]
        + ky[2, 1] * pad[2:, 1:-1]
        + ky[2, 2] * pad[2:, 2:]
    )
    return np.hypot(gx, gy)


def _box_mean(gray: np.ndarray, radius: int) -> np.ndarray:
    h, w = gray.shape
    k = 2 * radius + 1
    pad = np.pad(gray, radius, mode="edge")
    ii = np.pad(pad, ((1, 0), (1, 0))).cumsum(0).cumsum(1)
    tot = ii[k : k + h, k : k + w] - ii[0:h, k : k + w] - ii[k : k + h, 0:w] + ii[0:h, 0:w]
    return tot / float(k * k)


def saliency_map(rgb: np.ndarray) -> np.ndarray:
    """rgb HxWx3 float 0-1 → saliency map same HxW."""
    gray = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    h, w = gray.shape
    inner = max(2, min(h, w) // 8)
    outer = max(inner + 1, min(h, w) // 3)
    inn = _box_mean(gray, inner)
    out = _box_mean(gray, outer)
    cs = np.abs(inn - out)
    energy = _sobel_energy(gray)
    s = cs / (cs.max() + 1e-6) + 0.5 * energy / (energy.max() + 1e-6)
    return s.astype(np.float32)


def box_from_saliency(sal: np.ndarray) -> tuple[np.ndarray, float]:
    """BBox of pixels above mean+1σ. Returns xyxy and area fraction."""
    h, w = sal.shape
    thr = float(sal.mean() + sal.std())
    ys, xs = np.nonzero(sal >= thr)
    if ys.size < 0.015 * h * w:
        return np.array([0.0, 0.0, 0.0, 0.0], dtype=np.float32), 0.0
    x0, x1 = int(xs.min()), int(xs.max()) + 1
    y0, y1 = int(ys.min()), int(ys.max()) + 1
    area = float(ys.size) / float(h * w)
    return np.array([x0, y0, x1, y1], dtype=np.float32), area


def fast_subject_box(rgb_u8: np.ndarray) -> tuple[np.ndarray, float]:
    """Downsample to ~64px, saliency bbox, scale back to full resolution."""
    h, w = rgb_u8.shape[:2]
    step = max(1, min(h, w) // 64)
    small = rgb_u8[::step, ::step]
    gray = small.astype(np.float32)
    if gray.ndim == 3:
        gray = 0.299 * gray[:, :, 0] + 0.587 * gray[:, :, 1] + 0.114 * gray[:, :, 2]
    sal = saliency_map(np.stack([gray, gray, gray], axis=-1) / 255.0)
    xyxy, area = box_from_saliency(sal)
    if area < 0.015:
        return xyxy, area
    xyxy = xyxy * float(step)
    xyxy[0] = min(xyxy[0], w - 1)
    xyxy[2] = min(max(xyxy[2], xyxy[0] + 1), w)
    xyxy[1] = min(xyxy[1], h - 1)
    xyxy[3] = min(max(xyxy[3], xyxy[1] + 1), h)
    return xyxy.astype(np.float32), area
