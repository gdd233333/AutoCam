# ML

取景器学生模型：冻结 **MobileNetV4-Conv-S + ReLU**。输出 box + obj + 8 类。几何只在 C++ `guide_from_box()`。

训练设备按 RTX 5070 Ti Laptop **12 GB VRAM** 规划。权重不进 git，sha256 写在 `ml/models/README.md`。

PR-17 之前不要下载 PICD 等数据集。先让随机权重导出通过 op 白名单。
