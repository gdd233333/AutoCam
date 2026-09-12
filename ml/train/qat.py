#!/usr/bin/env python3
"""Short fine-tune then INT8 tflite (PTQ representative set). Full QAT is optional."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.export.to_tflite import export_tflite
from ml.train.train_student import main as train_main


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--epochs", type=int, default=5)
    p.add_argument("--out-dir", type=Path, default=Path("ml/models"))
    args, rest = p.parse_known_args()
    sys.argv = ["qat.py", "--epochs", str(args.epochs), "--out", str(args.out_dir / "checkpoints"), *rest]
    train_main()
    fp16, int8 = export_tflite(args.out_dir)
    print(fp16)
    print(int8)


if __name__ == "__main__":
    main()
