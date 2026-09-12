"""CLI alias: INT8 XNNPACK graph, float I/O."""

from pathlib import Path

from ml.export.to_tflite import export_tflite


if __name__ == "__main__":
    _, int8 = export_tflite(Path("ml/models"))
    print(int8)
