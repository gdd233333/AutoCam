"""Fold PICD / CADB labels into the 8 runtime classes."""

from __future__ import annotations

from ml.students.spec import CLASS_NAMES

# Longer keys first so "rule of thirds" wins over "thirds".
FOLD: list[tuple[str, str]] = [
    ("rule_of_thirds", "thirds"),
    ("rule of thirds", "thirds"),
    ("golden_ratio", "thirds"),
    ("golden ratio", "thirds"),
    ("p-rot", "thirds"),
    ("s-rot", "thirds"),
    ("p-cent", "center"),
    ("s-cent", "center"),
    ("vanishing_point", "leading_line"),
    ("vanishing point", "leading_line"),
    ("fill_the_frame", "fill_frame"),
    ("fill the frame", "fill_frame"),
    ("p-dia", "diagonal"),
    ("center", "center"),
    ("diagonal", "diagonal"),
    ("triangle", "triangle"),
    ("horizontal", "leading_line"),
    ("vertical", "leading_line"),
    ("vanishing", "leading_line"),
    ("radial", "leading_line"),
    ("curved", "leading_line"),
    ("symmetric", "symmetric"),
    ("fill_frame", "fill_frame"),
    ("dense", "fill_frame"),
    ("pattern", "none"),
    ("scatter", "none"),
    ("thirds", "thirds"),
    ("none", "none"),
]


def _norm(s: str) -> str:
    return s.strip().lower().replace("-", "_").replace(" ", "_")


def fold_label(raw: str) -> str:
    key = _norm(raw)
    for src, dst in FOLD:
        if key == _norm(src):
            return dst
    for src, dst in FOLD:
        nsrc = _norm(src)
        if nsrc in key:
            return dst
    return "none"


def fold_to_index(raw: str) -> int:
    return CLASS_NAMES.index(fold_label(raw))


def primary_cadb_class(classes: dict) -> str:
    """CADB is multi-label; pick the first non-none by runtime priority."""
    order = (
        "rule_of_thirds",
        "golden_ratio",
        "center",
        "diagonal",
        "triangle",
        "symmetric",
        "fill_the_frame",
        "horizontal",
        "vertical",
        "vanishing_point",
        "radial",
        "curved",
        "pattern",
        "none",
    )
    keys = {k.lower().replace(" ", "_"): k for k in classes}
    for name in order:
        if name in keys:
            return fold_label(keys[name])
    if classes:
        return fold_label(next(iter(classes)))
    return "none"
