# 模型产物

权重与 `.tflite` **不进 git**。发布时用 URL + sha256。

计划文件名（本地 `python ml/export/to_tflite.py` 生成，不进 git）：

| 文件 | 后端 | I/O |
|------|------|-----|
| `guide_viewfinder_fp16.tflite` | LiteRT GPU | float32 |
| `guide_viewfinder_int8.tflite` | XNNPACK CPU | float32 I/O，内部 INT8 |

| 文件 | sha256 |
|------|--------|
| _none yet_ | |
