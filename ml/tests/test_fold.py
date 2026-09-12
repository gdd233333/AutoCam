from ml.datasets.fold import fold_label, primary_cadb_class


def test_fold_picd():
    assert fold_label("P-RoT") == "thirds"
    assert fold_label("center") == "center"
    assert fold_label("vanishing") == "leading_line"
    assert fold_label("unknown-xyz") == "none"
    assert fold_label("rule_of_thirds") == "thirds"
    assert fold_label("fill_the_frame") == "fill_frame"
    assert fold_label("vanishing_point") == "leading_line"


def test_cadb_primary():
    assert primary_cadb_class({"vertical": [[0, 0, 10, 10]], "rule_of_thirds": [[1, 1, 20, 20]]}) == "thirds"
