#!/usr/bin/env python3
"""Distill from offline teacher npz. GPU loads student only."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.train.train_student import main as train_main


if __name__ == "__main__":
    if "--teachers" not in sys.argv:
        sys.argv += ["--teachers", "ml/teachers/viewfinder.npz"]
    train_main()
