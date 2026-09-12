# 取景器学生训练（12 GB / 5070 Ti）

本周额度空窗用这条。不下载 PICD 也能过拟合证明 AMP。教师 `.npz` 有了再蒸馏。

## 环境

```powershell
cd D:\AutoCam
python -m venv .venv
.\.venv\Scripts\Activate.ps1
# Blackwell 选当前可用的 CUDA wheel，失败再试 cu124
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
pip install -r ml/requirements.txt
```

导出双 tflite（可选，要 TensorFlow）：

```powershell
pip install -r ml/requirements-export.txt
python ml/export/random_weight_export_ci.py
```

## 本周建议日程

1. **冒烟（几分钟）**

```powershell
python -m pytest ml/tests -q
python ml/train/overfit_synthetic.py --epochs 5 --batch 16
```

loss 必须下降。有 CUDA 时默认 AMP。

2. **合成多 epoch（几小时）** — 先把切分头跑稳

```powershell
python ml/train/train_student.py --synthetic-n 2048 --epochs 40 --batch 16 --accum 4 --amp --out ml/models/checkpoints
```

batch 16、累积 4 = 有效 64。12 GB 吃紧就 `--batch 8 --accum 8`。

3. **蒸馏（有教师 npz）**

把 `ml/teachers/viewfinder.npz` 放到仓库外生成后拷进来（gitignore）。键：`rgb` `box`，以及 `label` 或 `logits`。

```powershell
python ml/train/distill.py --teachers ml/teachers/viewfinder.npz --epochs 30 --batch 16 --accum 4 --amp
```

4. **导出**（本机装了 tensorflow）

```powershell
python ml/export/to_tflite.py --out-dir ml/models
```

权重不进 git。把 sha256 填进 `ml/models/README.md`。

## 图与损失

- 输入 letterbox 256，pad 114/255，**禁止 stretch**
- `L = 2.0 * SmoothL1(box) + 1.0 * CE(8 class)`，可选 BCE(obj)
- 无 mask、无 guide MLP、无 λ_rank（still 头是 PR-21）
- 骨干 MobileNetV4-Conv-S，ReLU

## 不要做

- 教师和学生同占一张 12 GB 卡
- 从零训 SAM / CLIP-ViT-L
- 把 `.tflite` / `.pt` commit 进 git
