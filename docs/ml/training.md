# 取景器学生训练（12 GB / 5070 Ti）

结构脑图：[model_structure.md](model_structure.md) · [model_structure.html](model_structure.html)

## 环境

```powershell
cd D:\AutoCam
pip install -r ml/requirements.txt
# CLIP 教师（可选，没有则用几何启发式）
pip install open-clip-torch
# 或: pip install transformers
```

本机已有 `torch 2.13+cu130` 可直接训。

## 一条龙（本周）

**没有公开集 zip 时** — 合成即可把切分头跑起来：

```powershell
python -m pytest ml/tests -q
python ml/train/overfit_synthetic.py
python ml/train/train_student.py --synthetic-n 2048 --epochs 40 --batch 16 --accum 4 --amp
```

**有 CADB 图时：**

```powershell
python ml/datasets/download.py --cadb-ann
# 把 CADB_Dataset/images 放到 ml/datasets/raw/cadb/images
python ml/datasets/prepare.py --cadb ml/datasets/raw/cadb
python ml/teachers/run_teachers.py --use-index-label --device cuda
python ml/train/train_student.py --npz ml/teachers/viewfinder --epochs 30 --amp --synthetic-n 0
# 用 viewfinder_best.pt（按 val_acc）。AdamW + warmup 2 epoch + 骨干 lr×0.3 + EMA 0.999。
# val_acc 仍 <0.30 再试：--optim sgd --lr 0.05
```

CADB 图 zip（约 2GB）：Dropbox 链接在 `ml/datasets/manifest.yaml`。PICD 走官方百度/Drive，解压到 `ml/datasets/raw/picd` 后再 `--picd`。

**自有静物：** 放到 `ml/datasets/raw/folder/`（可用子目录名当 8 类名），`prepare.py --folder`。

## 教师

| 教师 | 产出 | 何时加载 |
|------|------|----------|
| CLIP-ViT-B/32 | 8 类 logits | `run_teachers.py` 无 `--use-index-label` 时 |
| CADB/PICD 标注 | label_i | `--use-index-label` |
| v0 显著性 | box + obj | 没有 CADB 元素框时 |
| U2-Net / YOLO-World | 未接 | 架构允许，不占 12 GB 训练卡 |

训练 GPU **只载学生**。npz 键：`rgb, box, label, obj, logits`。

## 导出

```powershell
pip install -r ml/requirements-export.txt
python ml/export/to_tflite.py --out-dir ml/models
```

权重不进 git。sha256 写 `ml/models/README.md`。
