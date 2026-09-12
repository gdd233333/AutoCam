import torch

from ml.students.losses import viewfinder_loss
from ml.students.spec import INPUT_SIZE
from ml.students.viewfinder import ViewfinderNet, count_parameters


def test_forward_shapes_and_param_band():
    model = ViewfinderNet().eval()
    n = count_parameters(model)
    assert 1_000_000 <= n <= 2_500_000
    x = torch.zeros(2, 3, INPUT_SIZE, INPUT_SIZE)
    with torch.no_grad():
        out = model(x)
    assert out["subject_box"].shape == (2, 4)
    assert out["subject_obj"].shape == (2, 1)
    assert out["composition_logits"].shape == (2, 8)
    assert out["subject_box"].min() >= 0
    assert out["subject_box"].max() <= 1


def test_loss_scalar():
    model = ViewfinderNet()
    x = torch.rand(4, 3, INPUT_SIZE, INPUT_SIZE)
    box = torch.rand(4, 4)
    label = torch.zeros(4, dtype=torch.long)
    pred = model(x)
    stats = viewfinder_loss(pred, box, label)
    assert stats["loss"].ndim == 0
    stats["loss"].backward()
