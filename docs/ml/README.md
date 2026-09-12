# 训练与端侧推理

完整规格见 [../dev/architecture.md](../dev/architecture.md) 的「构图 AI」一节。本周操作：[training.md](training.md)、[on_device.md](on_device.md)、结构脑图 [model_structure.html](model_structure.html)。

- 教师离卡预计算 `.npz`，不要和 student 同占 12 GB 显存
- 取景器图无 mask、无 guide MLP
- 端侧阶梯：LiteRT GPU FP16 → MNN → INT8 CPU。`vendorNpuSymbols` 为空
