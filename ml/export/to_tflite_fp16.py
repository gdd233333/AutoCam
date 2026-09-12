"""CLI alias: FP16 LiteRT GPU graph, float I/O."""

from pathlib import Path

from ml.export.to_tflite import export_tflite


if __name__ == "__main__":
    fp16, _ = export_tflite(Path("ml/models"))
    print(fp16)
