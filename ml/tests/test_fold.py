from ml.datasets.fold_picd_to_8 import fold_label


def test_fold_picd():
    assert fold_label("P-RoT") == "thirds"
    assert fold_label("center") == "center"
    assert fold_label("vanishing") == "leading_line"
    assert fold_label("unknown-xyz") == "none"
