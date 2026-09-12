"""Teacher npz shards. RGB stored as uint8 NCHW; converted to float in __getitem__."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import torch
from torch.utils.data import Dataset


def list_npz(path: Path) -> list[Path]:
    if path.is_dir():
        files = sorted(path.glob("*.npz"))
        if not files:
            raise FileNotFoundError(f"no .npz in {path}")
        return files
    if path.is_file():
        return [path]
    raise FileNotFoundError(path)


class NpzDataset(Dataset):
    def __init__(self, path: Path) -> None:
        self.files = list_npz(path)
        self.lengths: list[int] = []
        for f in self.files:
            with np.load(f) as data:
                n = int(data["label"].shape[0]) if "label" in data.files else int(data["rgb"].shape[0])
            self.lengths.append(n)
        self.offsets = np.cumsum([0] + self.lengths)
        self._cache_i: int | None = None
        self._cache: dict | None = None

    def __len__(self) -> int:
        return int(self.offsets[-1])

    def _shard_index(self, i: int) -> tuple[int, int]:
        si = int(np.searchsorted(self.offsets, i, side="right") - 1)
        return si, i - int(self.offsets[si])

    def _load_shard(self, si: int) -> dict:
        if self._cache_i == si and self._cache is not None:
            return self._cache
        data = np.load(self.files[si])
        rgb = data["rgb"]
        box = np.asarray(data["box"], dtype=np.float32)
        if "label" in data.files:
            label = np.asarray(data["label"], dtype=np.int64)
        else:
            label = np.argmax(np.asarray(data["logits"]), axis=-1).astype(np.int64)
        if "obj" in data.files:
            obj = np.asarray(data["obj"], dtype=np.float32).reshape(-1, 1)
        else:
            obj = np.ones((len(label), 1), dtype=np.float32)
        packed = {"rgb": rgb, "box": box, "label": label, "obj": obj}
        self._cache_i = si
        self._cache = packed
        return packed

    def __getitem__(self, i: int):
        si, j = self._shard_index(i)
        shard = self._load_shard(si)
        rgb = np.asarray(shard["rgb"][j])
        if rgb.ndim == 3 and rgb.shape[-1] == 3:
            rgb = np.transpose(rgb, (2, 0, 1))
        if rgb.dtype == np.uint8:
            rgb = rgb.astype(np.float32) / 255.0
        else:
            rgb = rgb.astype(np.float32)
        box = torch.from_numpy(np.asarray(shard["box"][j], dtype=np.float32))
        label = torch.tensor(int(shard["label"][j]), dtype=torch.long)
        obj = torch.from_numpy(np.asarray(shard["obj"][j], dtype=np.float32).reshape(1))
        return torch.from_numpy(np.ascontiguousarray(rgb)), box, label, obj
