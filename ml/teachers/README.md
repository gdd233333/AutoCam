# 教师预推理（离卡）

训练学生时 GPU **只载学生**。CLIP / U2-Net / YOLO-World 在别的机器或 CPU 上跑完，写成 `viewfinder.npz`。

取景器学生 **不学 mask**（默认 `λ_kd=0`）。显著性教师只给 v0 对照或可选 still 头。

生成示例（有 CLIP 时自己接）：letterbox 到 256，box 用同一变换，8 类 logits。键见 `ml/datasets/manifest.yaml`。
