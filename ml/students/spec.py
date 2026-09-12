"""MobileNetV4-Conv-S block list (Qin et al. 2024). Activation is ReLU."""

from __future__ import annotations

INPUT_SIZE = 256
NUM_CLASSES = 8
BOX_DIM = 4
PAD_VALUE = 114.0 / 255.0

CLASS_NAMES = (
    "thirds",
    "center",
    "diagonal",
    "triangle",
    "leading_line",
    "symmetric",
    "fill_frame",
    "none",
)

# (kind, *cfg)
# conv_bn: kernel, stride, out_channels
# uib: start_dw_k, middle_dw_k, stride, out_channels, expand_ratio
MNV4_CONV_S: list[tuple] = [
    ("conv_bn", 3, 2, 32),
    ("conv_bn", 3, 2, 32),
    ("conv_bn", 1, 1, 32),
    ("conv_bn", 3, 2, 96),
    ("conv_bn", 1, 1, 64),
    ("uib", 5, 5, 2, 96, 3.0),
    ("uib", 0, 3, 1, 96, 2.0),
    ("uib", 0, 3, 1, 96, 2.0),
    ("uib", 0, 3, 1, 96, 2.0),
    ("uib", 0, 3, 1, 96, 2.0),
    ("uib", 3, 0, 1, 96, 4.0),
    ("uib", 3, 3, 2, 128, 6.0),
    ("uib", 5, 5, 1, 128, 4.0),
    ("uib", 0, 5, 1, 128, 4.0),
    ("uib", 0, 5, 1, 128, 3.0),
    ("uib", 0, 3, 1, 128, 4.0),
    ("uib", 0, 3, 1, 128, 4.0),
    ("conv_bn", 1, 1, 960),
]

FEATURE_CHANNELS = 960
LAMBDA_BOX = 2.0
LAMBDA_CE = 1.0
