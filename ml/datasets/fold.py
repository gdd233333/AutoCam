"""Fold PICD / CADB labels into the 8 runtime classes."""

from __future__ import annotations

from ml.students.spec import CLASS_NAMES

# Longer keys first so "rule of thirds" wins over "thirds".
FOLD: list[tuple[str, str]] = [
    ("point_1_rot", "thirds"),
    ("shape_verti_oneside", "thirds"),
    ("p_rot", "thirds"),
    ("s_rot", "thirds"),
    ("ls_tri", "triangle"),
    ("special_triangle", "triangle"),
    ("point_multi_tri", "triangle"),
    ("p_tri", "triangle"),
    ("point_shape_cent", "center"),
    ("shape_verti_mid", "center"),
    ("p_cent", "center"),
    ("s_cent", "center"),
    ("point_multi_dia", "diagonal"),
    ("ls_dia", "diagonal"),
    ("p_dia", "diagonal"),
    ("line_verti_many", "leading_line"),
    ("line_verti3", "leading_line"),
    ("line_verti2", "leading_line"),
    ("l_vermul", "leading_line"),
    ("l_ver3", "leading_line"),
    ("l_ver2", "leading_line"),
    ("point_multi_hori", "leading_line"),
    ("point_multi_verti", "leading_line"),
    ("shape_verti_average", "leading_line"),
    ("ls_hori3", "leading_line"),
    ("ls_hori2", "leading_line"),
    ("ls_c_cur", "leading_line"),
    ("ls_o_cur", "leading_line"),
    ("ls_s_cur", "leading_line"),
    ("special_c", "leading_line"),
    ("special_o", "leading_line"),
    ("special_s", "leading_line"),
    ("perspective", "leading_line"),
    ("s_per", "leading_line"),
    ("s_hori", "leading_line"),
    ("p_hori", "leading_line"),
    ("p_ver", "leading_line"),
    ("hori3", "leading_line"),
    ("hori2", "leading_line"),
    ("rule_of_thirds", "thirds"),
    ("rule of thirds", "thirds"),
    ("golden_ratio", "thirds"),
    ("golden ratio", "thirds"),
    ("vanishing_point", "leading_line"),
    ("vanishing point", "leading_line"),
    ("fill_the_frame", "fill_frame"),
    ("fill the frame", "fill_frame"),
    ("pl_den", "fill_frame"),
    ("dense", "fill_frame"),
    ("pl_pat", "none"),
    ("ls_dif", "none"),
    ("p_scat", "none"),
    ("scatter", "none"),
    ("pattern", "none"),
    ("diffuse", "none"),
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
    ("thirds", "thirds"),
    ("none", "none"),
]


def _norm(s: str) -> str:
    return s.strip().lower().replace("-", "_").replace(" ", "_")


def fold_label(raw: str) -> str:
    # PICD multi-label uses commas; take the first tag.
    first = raw.split(",")[0].strip() if raw else "none"
    key = _norm(first)
    for src, dst in FOLD:
        if key == _norm(src):
            return dst
    for src, dst in FOLD:
        nsrc = _norm(src)
        if nsrc and nsrc in key:
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
