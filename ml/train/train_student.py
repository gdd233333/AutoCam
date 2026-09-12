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
    if args.resume and Path(args.resume).exists():
        state = torch.load(args.resume, map_location=device, weights_only=True)
        model.load_state_dict(state["model"] if isinstance(state, dict) and "model" in state else state)
        print(f"resumed {args.resume}")
    parts: list[Dataset] = []
    npz_path = args.teachers or args.npz
    if npz_path:
        parts.append(NpzDataset(Path(npz_path)))
        print(f"npz n={len(parts[-1])}")
    if args.synthetic_n > 0:
        data = make_batch(args.synthetic_n, np.random.default_rng(args.seed))
        parts.append(
            TensorDataset(
                torch.from_numpy(data["rgb"]),
                torch.from_numpy(data["box"]),
                torch.from_numpy(data["label"]),
                torch.from_numpy(data["obj"]),
            )
        )
        print(f"synthetic n={args.synthetic_n}")
    if not parts:
        raise SystemExit("need --npz/--teachers and/or --synthetic-n > 0")
    ds: Dataset = torch.utils.data.ConcatDataset(parts) if len(parts) > 1 else parts[0]
    n_val = int(len(ds) * args.val_frac)
    if n_val > 0:
        n_train = len(ds) - n_val
        train_ds, val_ds = torch.utils.data.random_split(
            ds, [n_train, n_val], generator=torch.Generator().manual_seed(args.seed)
        )
    else:
        train_ds, val_ds = ds, None
    loader = DataLoader(train_ds, batch_size=args.batch, shuffle=True, drop_last=False)
    val_loader = DataLoader(val_ds, batch_size=args.batch) if val_ds is not None else None
    counts = torch.zeros(8)
    for _, _, label, _ in DataLoader(train_ds, batch_size=256):
        for v in label.view(-1):
            if 0 <= int(v) < 8:
                counts[int(v)] += 1
    class_weight = (counts.sum() / (8.0 * counts.clamp(min=1.0))).to(device)
    print("class counts", counts.tolist(), "weights", [round(float(x), 3) for x in class_weight])
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    steps = max(1, args.epochs * len(loader))
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=steps)
    use_amp = device.type == "cuda" and args.amp
    amp_device = "cuda" if device.type == "cuda" else "cpu"
    scaler = torch.amp.GradScaler(amp_device, enabled=use_amp)
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
            with torch.amp.autocast(amp_device, enabled=use_amp):
                pred = model(rgb)
                stats = viewfinder_loss(pred, box, label, obj, class_weight=class_weight)
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
        msg = f"epoch {epoch+1}/{args.epochs} loss={mean:.4f}"
        if val_loader is not None:
            model.eval()
            vlosses = []
            with torch.no_grad():
                for rgb, box, label, obj in val_loader:
                    rgb = rgb.to(device)
                    box = box.to(device)
                    label = label.to(device)
                    obj = obj.to(device)
                    pred = model(rgb)
                    vlosses.append(float(viewfinder_loss(pred, box, label, obj, class_weight=class_weight)["loss"].cpu()))
            model.train()
            msg += f" val={float(np.mean(vlosses)):.4f}"
        print(msg)
    args.out.mkdir(parents=True, exist_ok=True)
    ckpt = args.out / "viewfinder_last.pt"
    torch.save({"model": model.state_dict(), "history": history}, ckpt)
    (args.out / "history.json").write_text(json.dumps(history), encoding="utf-8")
    print(f"wrote {ckpt}")
    if args.expect_drop and len(history) >= 2 and history[-1] >= history[0]:
        raise SystemExit(f"loss did not drop: {history[0]:.4f} -> {history[-1]:.4f}")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--teachers", type=str, default="", help="teacher npz path (alias of --npz)")
    p.add_argument("--npz", type=str, default="", help="teacher/dataset npz")
    p.add_argument("--synthetic-n", type=int, default=256)
    p.add_argument("--val-frac", type=float, default=0.1)
    p.add_argument("--resume", type=str, default="")
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
