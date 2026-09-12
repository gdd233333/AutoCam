#!/usr/bin/env python3
"""Train viewfinder student on synthetic data or teacher npz. AMP, 12 GB safe batch 16."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import torch
from torch.utils.data import DataLoader, Dataset, TensorDataset

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.datasets.synthetic import make_batch
from ml.students.losses import viewfinder_loss
from ml.students.viewfinder import ViewfinderNet, count_parameters


class NpzDataset(Dataset):
    def __init__(self, path: Path) -> None:
        data = np.load(path)
        rgb = data["rgb"]
        if rgb.ndim != 4:
            raise ValueError("rgb must be NCHW or NHWC")
        if rgb.shape[-1] == 3:
            rgb = np.transpose(rgb, (0, 3, 1, 2))
        self.rgb = torch.from_numpy(rgb.astype(np.float32))
        self.box = torch.from_numpy(np.asarray(data["box"], dtype=np.float32))
        if "label" in data:
            self.label = torch.from_numpy(np.asarray(data["label"], dtype=np.int64))
        else:
            logits = np.asarray(data["logits"])
            self.label = torch.from_numpy(np.argmax(logits, axis=-1).astype(np.int64))
        if "obj" in data:
            self.obj = torch.from_numpy(np.asarray(data["obj"], dtype=np.float32).reshape(-1, 1))
        else:
            self.obj = torch.ones(len(self.rgb), 1)

    def __len__(self) -> int:
        return self.rgb.shape[0]

    def __getitem__(self, i: int):
        return self.rgb[i], self.box[i], self.label[i], self.obj[i]


def synthetic_loader(n: int, batch: int, seed: int) -> DataLoader:
    data = make_batch(n, np.random.default_rng(seed))
    ds = TensorDataset(
        torch.from_numpy(data["rgb"]),
        torch.from_numpy(data["box"]),
        torch.from_numpy(data["label"]),
        torch.from_numpy(data["obj"]),
    )
    return DataLoader(ds, batch_size=batch, shuffle=True, drop_last=False)


def train(args: argparse.Namespace) -> None:
    device = torch.device("cuda" if torch.cuda.is_available() and not args.cpu else "cpu")
    print(f"device={device} cuda={torch.cuda.is_available()}")
    model = ViewfinderNet().to(device)
    print(f"params={count_parameters(model)}")
    if args.teachers:
        ds = NpzDataset(Path(args.teachers))
        loader = DataLoader(ds, batch_size=args.batch, shuffle=True)
    else:
        loader = synthetic_loader(args.synthetic_n, args.batch, args.seed)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    steps = max(1, args.epochs * len(loader))
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=steps)
    use_amp = device.type == "cuda" and args.amp
    scaler = torch.cuda.amp.GradScaler(enabled=use_amp)
    accum = max(1, args.accum)
    history: list[float] = []
    model.train()
    step = 0
    opt.zero_grad(set_to_none=True)
    for epoch in range(args.epochs):
        losses = []
        for i, (rgb, box, label, obj) in enumerate(loader):
            rgb = rgb.to(device, non_blocking=True)
            box = box.to(device, non_blocking=True)
            label = label.to(device, non_blocking=True)
            obj = obj.to(device, non_blocking=True)
            with torch.cuda.amp.autocast(enabled=use_amp):
                pred = model(rgb)
                stats = viewfinder_loss(pred, box, label, obj)
                loss = stats["loss"] / accum
            scaler.scale(loss).backward()
            if (i + 1) % accum == 0:
                scaler.step(opt)
                scaler.update()
                opt.zero_grad(set_to_none=True)
                sched.step()
                step += 1
            losses.append(float(stats["loss"].detach().cpu()))
        mean = float(np.mean(losses)) if losses else 0.0
        history.append(mean)
        print(f"epoch {epoch+1}/{args.epochs} loss={mean:.4f}")
    args.out.mkdir(parents=True, exist_ok=True)
    ckpt = args.out / "viewfinder_last.pt"
    torch.save({"model": model.state_dict(), "history": history}, ckpt)
    (args.out / "history.json").write_text(json.dumps(history), encoding="utf-8")
    print(f"wrote {ckpt}")
    if args.expect_drop and len(history) >= 2 and history[-1] >= history[0]:
        raise SystemExit(f"loss did not drop: {history[0]:.4f} -> {history[-1]:.4f}")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--teachers", type=str, default="", help="teacher npz path")
    p.add_argument("--synthetic-n", type=int, default=256)
    p.add_argument("--epochs", type=int, default=8)
    p.add_argument("--batch", type=int, default=16)
    p.add_argument("--accum", type=int, default=4, help="grad accum to 64 with batch 16")
    p.add_argument("--lr", type=float, default=3e-4)
    p.add_argument("--amp", action="store_true", default=True)
    p.add_argument("--no-amp", action="store_true")
    p.add_argument("--cpu", action="store_true")
    p.add_argument("--seed", type=int, default=0)
    p.add_argument("--out", type=Path, default=Path("ml/models/checkpoints"))
    p.add_argument("--expect-drop", action="store_true")
    args = p.parse_args()
    if args.no_amp:
        args.amp = False
    train(args)


if __name__ == "__main__":
    main()
