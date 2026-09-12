import numpy as np

from ml.students.letterbox import box_xyxy_to_cxcywh_letterbox, letterbox_rgb
from ml.students.spec import INPUT_SIZE


def test_letterbox_keeps_aspect_and_pad():
    rgb = np.zeros((100, 200, 3), dtype=np.float32)
    rgb[:, :, 0] = 1.0
    canvas, meta = letterbox_rgb(rgb)
    assert canvas.shape == (INPUT_SIZE, INPUT_SIZE, 3)
    assert meta["resized_w"] == INPUT_SIZE
    assert meta["resized_h"] < INPUT_SIZE
    assert meta["pad_y"] > 0


def test_box_maps_into_unit_square():
    rgb = np.zeros((50, 50, 3), dtype=np.float32)
    _, meta = letterbox_rgb(rgb)
    box = box_xyxy_to_cxcywh_letterbox(np.array([0, 0, 50, 50]), meta)
    assert box.shape == (4,)
    assert 0.4 < box[0] < 0.6
    assert 0.4 < box[1] < 0.6
