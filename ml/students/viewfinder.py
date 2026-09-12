"""MobileNetV4-Conv-S + ReLU, box / obj / composition_8 heads."""

from __future__ import annotations

import math

import torch
import torch.nn as nn
import torch.nn.functional as F

from ml.students.spec import FEATURE_CHANNELS, MNV4_CONV_S, NUM_CLASSES


def make_divisible(value: float, divisor: int = 8) -> int:
    min_value = divisor
    new_value = max(min_value, int(value + divisor / 2) // divisor * divisor)
    if new_value < 0.9 * value:
        new_value += divisor
    return int(new_value)


class ConvBNReLU(nn.Module):
    def __init__(self, in_ch: int, out_ch: int, kernel: int, stride: int = 1) -> None:
        super().__init__()
        padding = (kernel - 1) // 2
        self.conv = nn.Conv2d(in_ch, out_ch, kernel, stride, padding, bias=False)
        self.bn = nn.BatchNorm2d(out_ch)
        self.act = nn.ReLU(inplace=True)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.act(self.bn(self.conv(x)))


class UniversalInvertedBottleneck(nn.Module):
    def __init__(
        self,
        in_ch: int,
        out_ch: int,
        expand_ratio: float,
        start_dw_k: int,
        middle_dw_k: int,
        stride: int,
        middle_dw_downsample: bool = True,
    ) -> None:
        super().__init__()
        self.start_dw_k = start_dw_k
        self.middle_dw_k = middle_dw_k
        if start_dw_k:
            s = stride if not middle_dw_downsample else 1
            self.start_dw = nn.Conv2d(
                in_ch, in_ch, start_dw_k, s, (start_dw_k - 1) // 2, groups=in_ch, bias=False
            )
            self.start_dw_bn = nn.BatchNorm2d(in_ch)
        else:
            self.start_dw = None
            self.start_dw_bn = None
        hidden = make_divisible(in_ch * expand_ratio)
        self.expand = nn.Conv2d(in_ch, hidden, 1, bias=False)
        self.expand_bn = nn.BatchNorm2d(hidden)
        if middle_dw_k:
            s = stride if middle_dw_downsample else 1
            self.middle_dw = nn.Conv2d(
                hidden, hidden, middle_dw_k, s, (middle_dw_k - 1) // 2, groups=hidden, bias=False
            )
            self.middle_dw_bn = nn.BatchNorm2d(hidden)
        else:
            self.middle_dw = None
            self.middle_dw_bn = None
        self.proj = nn.Conv2d(hidden, out_ch, 1, bias=False)
        self.proj_bn = nn.BatchNorm2d(out_ch)
        self.identity = stride == 1 and in_ch == out_ch

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        shortcut = x
        if self.start_dw is not None:
            x = self.start_dw_bn(self.start_dw(x))
        x = F.relu(self.expand_bn(self.expand(x)), inplace=True)
        if self.middle_dw is not None:
            x = F.relu(self.middle_dw_bn(self.middle_dw(x)), inplace=True)
        x = self.proj_bn(self.proj(x))
        if self.identity:
            x = x + shortcut
        return x


def build_backbone() -> nn.Sequential:
    layers: list[nn.Module] = []
    c = 3
    for kind, *cfg in MNV4_CONV_S:
        if kind == "conv_bn":
            k, s, f = cfg
            layers.append(ConvBNReLU(c, f, k, s))
            c = f
        elif kind == "uib":
            start_k, mid_k, s, f, e = cfg
            layers.append(UniversalInvertedBottleneck(c, f, e, start_k, mid_k, s))
            c = f
        else:
            raise ValueError(kind)
    return nn.Sequential(*layers)


class ViewfinderNet(nn.Module):
    """Outputs NCHW-trained; export path converts to NHWC tflite."""

    def __init__(self, dropout: float = 0.3) -> None:
        super().__init__()
        self.backbone = build_backbone()
        self.pool = nn.AdaptiveAvgPool2d(1)
        self.drop = nn.Dropout(dropout)
        self.box = nn.Linear(FEATURE_CHANNELS, 4)
        self.obj = nn.Linear(FEATURE_CHANNELS, 1)
        self.cls = nn.Linear(FEATURE_CHANNELS, NUM_CLASSES)
        self._init()

    def _init(self) -> None:
        for m in self.modules():
            if isinstance(m, nn.Conv2d):
                n = m.kernel_size[0] * m.kernel_size[1] * m.out_channels
                m.weight.data.normal_(0, math.sqrt(2.0 / n))
            elif isinstance(m, nn.BatchNorm2d):
                m.weight.data.fill_(1.0)
                m.bias.data.zero_()
            elif isinstance(m, nn.Linear):
                m.weight.data.normal_(0, 0.01)
                m.bias.data.zero_()

    def forward(self, x: torch.Tensor) -> dict[str, torch.Tensor]:
        feat = self.drop(self.pool(self.backbone(x)).flatten(1))
        box = torch.sigmoid(self.box(feat))
        obj_logits = self.obj(feat)
        obj = torch.sigmoid(obj_logits)
        logits = self.cls(feat)
        return {
            "subject_box": box,
            "subject_obj": obj,
            "subject_obj_logits": obj_logits,
            "composition_logits": logits,
        }


def count_parameters(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters())
