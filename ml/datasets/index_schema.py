"""One jsonl record per image after prepare.py."""

from __future__ import annotations

from typing import Any, TypedDict


class IndexRecord(TypedDict, total=False):
    path: str
    source: str
    label: str
    label_i: int
    box_xyxy: list[float]
    width: int
    height: int
