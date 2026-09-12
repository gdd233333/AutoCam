import numpy as np

from ml.datasets.synthetic import make_batch
from ml.teachers.saliency import box_from_saliency, saliency_map


def test_saliency_finds_bright_blob():
    rgb = np.zeros((64, 64, 3), dtype=np.float32)
    rgb[16:40, 20:44] = 1.0
    sal = saliency_map(rgb)
    box, area = box_from_saliency(sal)
    assert area > 0.015
    cx = (box[0] + box[2]) / 2
    cy = (box[1] + box[3]) / 2
    assert 20 < cx < 44
    assert 16 < cy < 40


def test_synthetic_batch_keys():
    b = make_batch(4)
    assert b["rgb"].shape[0] == 4
    assert b["box"].shape == (4, 4)
