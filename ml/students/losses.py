from __future__ import annotations

import torch
import torch.nn.functional as F

from ml.students.spec import LAMBDA_BOX, LAMBDA_CE, NUM_CLASSES


def viewfinder_loss(
    pred: dict[str, torch.Tensor],
    box: torch.Tensor,
    label: torch.Tensor,
    obj: torch.Tensor | None = None,
    class_weight: torch.Tensor | None = None,
    lambda_box: float = LAMBDA_BOX,
    lambda_ce: float = LAMBDA_CE,
) -> dict[str, torch.Tensor]:
    l_box = F.smooth_l1_loss(pred["subject_box"], box)
    l_ce = F.cross_entropy(pred["composition_logits"], label, weight=class_weight)
    total = lambda_box * l_box + lambda_ce * l_ce
    if obj is not None:
        obj_t = obj.float().reshape_as(pred["subject_obj_logits"])
        l_obj = F.binary_cross_entropy_with_logits(pred["subject_obj_logits"], obj_t)
        total = total + l_obj
    else:
        l_obj = pred["subject_obj"].new_zeros(())
    return {"loss": total, "l_box": l_box.detach(), "l_ce": l_ce.detach(), "l_obj": l_obj.detach()}


def class_weights_from_counts(counts: torch.Tensor) -> torch.Tensor:
    counts = counts.float().clamp(min=1.0)
    inv = counts.sum() / (NUM_CLASSES * counts)
    return inv
