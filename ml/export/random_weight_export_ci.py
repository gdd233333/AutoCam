#!/usr/bin/env python3
"""Build student → ONNX → dual tflite. Opcode ⊆ whitelist. No dataset download."""

from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

import numpy as np
import torch

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.export.opcodes import allowed_names, assert_subset, load_whitelist, onnx_opcodes, tflite_opcodes
from ml.export.to_onnx import export_onnx
from ml.students.spec import INPUT_SIZE
from ml.students.viewfinder import ViewfinderNet, count_parameters


def main() -> None:
    wl_path = ROOT / "ml" / "export" / "op_whitelist.yaml"
    wl = load_whitelist(wl_path)
    allowed = allowed_names(wl)

    model = ViewfinderNet().eval()
    n = count_parameters(model)
    print(f"viewfinder params={n}")
    if not (1_000_000 <= n <= 2_500_000):
        raise SystemExit(f"param count {n} outside 1.0M-2.5M (Conv-S backbone+heads, no ImageNet FC)")
    dummy = torch.zeros(1, 3, INPUT_SIZE, INPUT_SIZE)
    with torch.no_grad():
        out = model(dummy)
    for key, shape in (
        ("subject_box", (1, 4)),
        ("subject_obj", (1, 1)),
        ("composition_logits", (1, 8)),
    ):
        if tuple(out[key].shape) != shape:
            raise SystemExit(f"{key} shape {tuple(out[key].shape)} != {shape}")

    with tempfile.TemporaryDirectory() as tmp:
        tmp_path = Path(tmp)
        onnx_path = export_onnx(None, tmp_path / "guide_viewfinder.onnx")
        onnx_ops = onnx_opcodes(onnx_path)
        print("onnx ops:", sorted(set(onnx_ops)))
        fused = "BatchNormalization" not in set(onnx_ops)
        print(f"onnx bn_fused={fused}")
        assert_subset(onnx_ops, allowed, fused_bn_ok=True)

        try:
            from ml.export.to_tflite import export_tflite

            fp16, int8 = export_tflite(tmp_path)
        except ImportError as exc:
            if os.environ.get("CI"):
                raise SystemExit(f"tensorflow required in CI: {exc}") from exc
            print(f"skip tflite ({exc})")
            print("random_weight_export_ci ok (onnx only)")
            return
        for path, tag in ((fp16, "fp16"), (int8, "int8")):
            ops = tflite_opcodes(path)
            print(f"tflite {tag} ops:", sorted(set(ops)))
            print(f"tflite {tag} bytes={path.stat().st_size}")
            assert_subset(ops, allowed, fused_bn_ok=True)
            if tag == "int8" and "QUANTIZE" not in ops and "DEQUANTIZE" not in ops:
                print("warn: int8 graph has no QUANTIZE/DEQUANTIZE (converter may have skipped)")
        if fp16.stat().st_size > 12_000_000:
            raise SystemExit(f"fp16 too large: {fp16.stat().st_size}")
        if int8.stat().st_size > 8_000_000:
            raise SystemExit(f"int8 too large: {int8.stat().st_size}")
    print("random_weight_export_ci ok")


if __name__ == "__main__":
    main()
