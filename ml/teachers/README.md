# 教师预推理（离卡）

```powershell
python ml/datasets/prepare.py
python ml/teachers/run_teachers.py --use-index-label --device cuda
```

- **CLIP-ViT-B/32**：`open-clip-torch` 或 `transformers`；都没有则用主体中心几何启发式。
- **显著性 box**：`ml/teachers/saliency.py`（center-surround + Sobel），与端侧 v0 同类。CADB `composition_elements` 里像样的框会优先。
- 取景器学生 **不学 mask**。

输出：`ml/teachers/viewfinder.npz`（gitignore）。
