# 训练与端侧推理

完整规格见 [../dev/architecture.md](../dev/architecture.md) 的「构图 AI」一节。

- 教师离卡预计算 `.npz`，不要和 student 同占 12 GB 显存
- 取景器图无 mask、无 guide MLP
- 端侧阶梯：LiteRT GPU FP16 → MNN → INT8 CPU。玄戒 NPU 无公开 SDK，厂商符号表为空
