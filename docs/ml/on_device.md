# 端侧推理

阶梯：LiteRT GPU FP16 →（vendor NPU 空表跳过）→ INT8 CPU。`vendorNpuSymbols = []`。

`BackendProbe` 持久化 `files/backend_probe.json`。模型 sha 不变则用 last-good。Warmup 不计 SLO。

Ship 1 解释器在 `com.autocam.infer.InferenceRuntime`（TFLite Interpreter）。无模型文件时 `available()==false`，`ai.guide=neural` 必须回 `rule`（接线在 PR-20）。

C++ `autocam::inference_available()` 目前恒 false，等 LiteRT native。
