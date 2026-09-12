from __future__ import annotations

import argparse
from pathlib import Path

import torch

from ml.students.spec import INPUT_SIZE
from ml.students.viewfinder import ViewfinderNet


def export_onnx(checkpoint: Path | None, out: Path) -> Path:
    model = ViewfinderNet()
    if checkpoint and checkpoint.exists():
        state = torch.load(checkpoint, map_location="cpu", weights_only=True)
        model.load_state_dict(state["model"] if isinstance(state, dict) and "model" in state else state)
    model.eval()

    class _TupleOut(torch.nn.Module):
        def __init__(self, inner: ViewfinderNet) -> None:
            super().__init__()
            self.inner = inner

        def forward(self, x: torch.Tensor):
            o = self.inner(x)
            return o["subject_box"], o["subject_obj"], o["composition_logits"]

    wrapped = _TupleOut(model).eval()
    dummy = torch.zeros(1, 3, INPUT_SIZE, INPUT_SIZE)
    out.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(
        wrapped,
        dummy,
        str(out),
        input_names=["preview_rgb"],
        output_names=["subject_box", "subject_obj", "composition_logits"],
        opset_version=17,
        dynamo=False,
    )
    return out


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--checkpoint", type=Path, default=None)
    p.add_argument("--out", type=Path, default=Path("ml/models/guide_viewfinder.onnx"))
    args = p.parse_args()
    path = export_onnx(args.checkpoint, args.out)
    print(f"wrote {path}")


if __name__ == "__main__":
    main()
