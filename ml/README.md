# ML

取景器学生：**MobileNetV4-Conv-S + ReLU**，输出 box + obj + 8 类。几何只在 C++ `guide_from_box()`。

训练按 RTX 5070 Ti Laptop **12 GB** 规划。权重不进 git。步骤见 [docs/ml/training.md](../docs/ml/training.md)。

```powershell
python -m pytest ml/tests -q
python ml/train/overfit_synthetic.py
python ml/export/random_weight_export_ci.py
```
