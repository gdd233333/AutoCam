from pathlib import Path

import numpy as np
import torch

from ml.datasets.npz_set import NpzDataset


def test_sharded_uint8_roundtrip(tmp_path: Path):
    rgb = (np.random.rand(4, 3, 32, 32) * 255).astype(np.uint8)
    box = np.random.rand(4, 4).astype(np.float32)
    label = np.arange(4, dtype=np.int64)
    obj = np.ones((4, 1), dtype=np.float32)
    np.savez(tmp_path / "shard_0000.npz", rgb=rgb[:2], box=box[:2], label=label[:2], obj=obj[:2])
    np.savez(tmp_path / "shard_0001.npz", rgb=rgb[2:], box=box[2:], label=label[2:], obj=obj[2:])
    ds = NpzDataset(tmp_path)
    assert len(ds) == 4
    x, b, y, o = ds[3]
    assert x.shape == (3, 32, 32)
    assert x.dtype == torch.uint8
    assert int(x.max()) <= 255
    assert int(y) == 3
