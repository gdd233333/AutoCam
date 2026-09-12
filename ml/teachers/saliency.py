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
    """Largest blob above mean+1σ. Returns xyxy and area fraction. Empty → obj=0."""
    h, w = sal.shape
    thr = float(sal.mean() + sal.std())
    mask = sal >= thr
    if mask.sum() < 0.015 * h * w:
        return np.array([0.0, 0.0, 0.0, 0.0], dtype=np.float32), 0.0
    visited = np.zeros_like(mask, dtype=bool)
    best = None
    best_n = 0
    ys, xs = np.where(mask)
    for y0, x0 in zip(ys.tolist(), xs.tolist()):
        if visited[y0, x0]:
            continue
        stack = [(y0, x0)]
        visited[y0, x0] = True
        cells = []
        while stack:
            y, x = stack.pop()
            cells.append((y, x))
            for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not visited[ny, nx]:
                    visited[ny, nx] = True
                    stack.append((ny, nx))
        if len(cells) > best_n:
            best_n = len(cells)
            ys_c = [c[0] for c in cells]
            xs_c = [c[1] for c in cells]
            best = (min(xs_c), min(ys_c), max(xs_c) + 1, max(ys_c) + 1)
    if best is None or best_n < 0.015 * h * w:
        return np.array([0.0, 0.0, 0.0, 0.0], dtype=np.float32), 0.0
    area = best_n / float(h * w)
    return np.array(best, dtype=np.float32), float(area)
