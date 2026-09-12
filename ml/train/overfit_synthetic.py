#!/usr/bin/env python3
"""1-epoch (default 20 steps worth) synthetic overfit smoke. Proves AMP path."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.train.train_student import main as train_main


if __name__ == "__main__":
    extra = sys.argv[1:]
    sys.argv = [
        "overfit_synthetic.py",
        "--synthetic-n",
        "64",
        "--epochs",
        "5",
        "--batch",
        "16",
        "--accum",
        "1",
        "--val-frac",
        "0",
        "--dropout",
        "0",
        "--label-smoothing",
        "0",
        "--class-weight",
        "none",
        "--no-hflip",
        "--expect-drop",
        *extra,
    ]
    train_main()
