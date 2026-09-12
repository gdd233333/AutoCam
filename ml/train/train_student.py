#!/usr/bin/env python3
"""Train viewfinder student on synthetic data or teacher npz. AMP, 12 GB safe batch 16."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np
import torch
from torch.utils.data import ConcatDataset, DataLoader, Dataset, Subset, TensorDataset

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from ml.datasets.npz_set import NpzDataset
from ml.datasets.synthetic import make_batch
from ml.students.losses import viewfinder_loss
from ml.students.viewfinder import ViewfinderNet, count_parameters


def _synthetic_uint8(n: int, seed: int) -> TensorDataset:
    data = make_batch(n, np.random.default_rng(seed))
    rgb = torch.from_numpy((np.clip(data["rgb"], 0, 1) * 255).astype(np.uint8))
    return TensorDataset(
        rgb,
        torch.from_numpy(data["box"]),
        torch.from_numpy(data["label"]),
        torch.from_numpy(data["obj"]),
    )


def _shard_split(npz: NpzDataset, val_frac: float, seed: int) -> tuple[list[int], list[int], np.ndarray]:
    rng = np.random.default_rng(seed)
    train_idx: list[int] = []
    val_idx: list[int] = []
    labels = npz.labels()
    for si, n in enumerate(npz.lengths):
        base = int(npz.offsets[si])
        perm = rng.permutation(n)
        n_val = int(n * val_frac) if val_frac > 0 else 0
        val_idx.extend((base + perm[:n_val]).tolist())
        train_idx.extend((base + perm[n_val:]).tolist())
    train_idx.sort()
    val_idx.sort()
    return train_idx, val_idx, labels


def _epoch_loader(
    npz: NpzDataset | None,
    train_idx: list[int],
    syn: Dataset | None,
    batch: int,
    workers: int,
    pin: bool,
    seed: int,
    epoch: int,
) -> DataLoader:
    rng = np.random.default_rng(seed + epoch)
    parts: list[Dataset] = []
    if npz is not None and train_idx:
        by_shard: dict[int, list[int]] = {}
        for i in train_idx:
            si, _ = npz._shard_index(i)
            by_shard.setdefault(si, []).append(i)
        shards = list(by_shard.keys())
        rng.shuffle(shards)
        for si in shards:
            local = by_shard[si]
            rng.shuffle(local)
            parts.append(Subset(npz, local))
    if syn is not None:
        parts.append(syn)
    ds: Dataset = ConcatDataset(parts) if len(parts) > 1 else parts[0]
    kwargs: dict = {
        "batch_size": batch,
        "shuffle": False,
        "drop_last": False,
        "num_workers": workers,
        "pin_memory": pin,
    }
    if workers > 0:
        kwargs["prefetch_factor"] = 2
    if not parts:
        raise SystemExit("empty train set")
    return DataLoader(ds, **kwargs)


def _to_device(rgb, box, label, obj, device):
    rgb = rgb.to(device, non_blocking=True)
    if rgb.dtype == torch.uint8:
        rgb = rgb.float().div_(255)
    box = box.to(device, non_blocking=True)
    label = label.to(device, non_blocking=True)
    obj = obj.to(device, non_blocking=True)
    if device.type == "cuda":
        rgb = rgb.contiguous(memory_format=torch.channels_last)
    return rgb, box, label, obj


def train(args: argparse.Namespace) -> None:
    device = torch.device("cuda" if torch.cuda.is_available() and not args.cpu else "cpu")
    if device.type == "cuda":
        torch.backends.cudnn.benchmark = True
    print(f"device={device} cuda={torch.cuda.is_available()}")
    model = ViewfinderNet().to(device)
    if device.type == "cuda":
        model = model.to(memory_format=torch.channels_last)
    print(f"params={count_parameters(model)}")
    if args.resume and Path(args.resume).exists():
        state = torch.load(args.resume, map_location=device, weights_only=True)
        model.load_state_dict(state["model"] if isinstance(state, dict) and "model" in state else state)
        print(f"resumed {args.resume}")
    npz: NpzDataset | None = None
    syn: Dataset | None = None
    npz_path = args.teachers or args.npz
    train_idx: list[int] = []
    val_idx: list[int] = []
    label_arr = np.zeros((0,), dtype=np.int64)
    if npz_path:
        npz = NpzDataset(Path(npz_path))
        train_idx, val_idx, label_arr = _shard_split(npz, args.val_frac, args.seed)
        print(f"npz n={len(npz)} train={len(train_idx)} val={len(val_idx)}")
    if args.synthetic_n > 0:
        syn = _synthetic_uint8(args.synthetic_n, args.seed)
        print(f"synthetic n={args.synthetic_n}")
    if npz is None and syn is None:
        raise SystemExit("need --npz/--teachers and/or --synthetic-n > 0")
    workers = args.workers
    if workers < 0:
        workers = 0 if device.type != "cuda" else min(4, max(1, (os.cpu_count() or 4) // 2))
    pin = device.type == "cuda"
    val_loader = None
    if npz is not None and val_idx:
        val_kw: dict = {
            "batch_size": max(args.batch, 32),
            "shuffle": False,
            "num_workers": workers,
            "pin_memory": pin,
        }
        if workers > 0:
            val_kw["prefetch_factor"] = 2
        val_loader = DataLoader(Subset(npz, val_idx), **val_kw)
    if label_arr.size:
        train_labels = label_arr[np.array(train_idx, dtype=np.int64)] if train_idx else label_arr
        counts = torch.bincount(torch.from_numpy(train_labels).clamp(0, 7), minlength=8).float()
    else:
        counts = torch.ones(8)
    class_weight = (counts.sum() / (8.0 * counts.clamp(min=1.0))).to(device)
    print("class counts", [int(x) for x in counts.tolist()], "weights", [round(float(x), 3) for x in class_weight])
    print(f"dataloader workers={workers} pin_memory={pin}")
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    n_train = len(train_idx) + (len(syn) if syn is not None else 0)
    steps_per_epoch = max(1, (n_train + args.batch - 1) // args.batch)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=max(1, args.epochs * steps_per_epoch))
    use_amp = device.type == "cuda" and args.amp
    amp_device = "cuda" if device.type == "cuda" else "cpu"
    scaler = torch.amp.GradScaler(amp_device, enabled=use_amp)
    accum = max(1, args.accum)
    history: list[dict] = []
    best_val = float("inf")
    bad_epochs = 0
    args.out.mkdir(parents=True, exist_ok=True)
    print("hint: random 8-class CE≈2.08; keep the epoch with lowest val (healthy ~1.2–2.2). stop if val rises while train falls.")
    model.train()
    step = 0
    opt.zero_grad(set_to_none=True)
    loader = _epoch_loader(npz, train_idx, syn, args.batch, workers, pin, args.seed, 0)
    for epoch in range(args.epochs):
        if epoch > 0:
            loader = _epoch_loader(npz, train_idx, syn, args.batch, workers, pin, args.seed, epoch)
        losses = []
        for i, (rgb, box, label, obj) in enumerate(loader):
            rgb, box, label, obj = _to_device(rgb, box, label, obj, device)
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
            losses.append(float(stats["loss"].detach()))
        mean = float(np.mean(losses)) if losses else 0.0
        row: dict = {"epoch": epoch + 1, "train": mean}
        msg = f"epoch {epoch+1}/{args.epochs} loss={mean:.4f}"
        if val_loader is not None:
            model.eval()
            vlosses = []
            n_ok = 0
            n_all = 0
            with torch.inference_mode():
                for rgb, box, label, obj in val_loader:
                    rgb, box, label, obj = _to_device(rgb, box, label, obj, device)
                    pred = model(rgb)
                    vlosses.append(float(viewfinder_loss(pred, box, label, obj, class_weight=class_weight)["loss"]))
                    pred_c = pred["composition_logits"].argmax(dim=-1)
                    n_ok += int((pred_c == label).sum().item())
                    n_all += int(label.numel())
            model.train()
            vmean = float(np.mean(vlosses))
            vacc = n_ok / max(n_all, 1)
            row["val"] = vmean
            row["val_acc"] = vacc
            msg += f" val={vmean:.4f} val_acc={vacc:.3f}"
            if vmean + 1e-4 < best_val:
                best_val = vmean
                bad_epochs = 0
                best_path = args.out / "viewfinder_best.pt"
                torch.save({"model": model.state_dict(), "history": history + [row], "val": vmean}, best_path)
                msg += "  [best]"
            else:
                bad_epochs += 1
                msg += f"  (no improve {bad_epochs}/{args.patience})"
        history.append(row)
        print(msg)
        (args.out / "history.json").write_text(json.dumps(history, indent=2), encoding="utf-8")
        if val_loader is not None and args.patience > 0 and bad_epochs >= args.patience:
            print(f"early stop: val not improving for {args.patience} epochs (best val={best_val:.4f})")
            break
    ckpt = args.out / "viewfinder_last.pt"
    torch.save({"model": model.state_dict(), "history": history}, ckpt)
    print(f"wrote {ckpt}")
    if best_val < float("inf"):
        print(f"use {args.out / 'viewfinder_best.pt'}  (best val={best_val:.4f})")
    if args.expect_drop and len(history) >= 2 and history[-1]["train"] >= history[0]["train"]:
        raise SystemExit(f"loss did not drop: {history[0]['train']:.4f} -> {history[-1]['train']:.4f}")


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
    p.add_argument("--patience", type=int, default=5, help="early stop after this many worse val epochs; 0=off")
    p.add_argument("--workers", type=int, default=-1, help="DataLoader workers; -1 = auto")
    p.add_argument("--expect-drop", action="store_true")
    args = p.parse_args()
    if args.no_amp:
        args.amp = False
    train(args)


if __name__ == "__main__":
    main()
