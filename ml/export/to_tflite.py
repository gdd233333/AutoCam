from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from ml.datasets.synthetic import make_batch
from ml.export.keras_viewfinder import build_keras_viewfinder
from ml.students.spec import INPUT_SIZE


def _converter(model, quantize: bool):
    import tensorflow as tf

    def make() -> tf.lite.TFLiteConverter:
        conv = tf.lite.TFLiteConverter.from_keras_model(model)
        conv.optimizations = [tf.lite.Optimize.DEFAULT]
        return conv

    if not quantize:
        conv = make()
        conv.target_spec.supported_types = [tf.float16]
        return conv.convert()
    batch = make_batch(16, np.random.default_rng(1))
    rgb = np.transpose(batch["rgb"], (0, 2, 3, 1)).astype(np.float32)

    def gen():
        for i in range(len(rgb)):
            yield [rgb[i : i + 1]]

    conv = make()
    conv.representative_dataset = gen
    conv.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    conv.inference_input_type = tf.float32
    conv.inference_output_type = tf.float32
    try:
        return conv.convert()
    except Exception:
        conv = make()
        conv.representative_dataset = gen
        return conv.convert()


def export_tflite(out_dir: Path) -> tuple[Path, Path]:
    import tensorflow as tf

    tf.keras.backend.clear_session()
    model = build_keras_viewfinder()
    _ = model(np.zeros((1, INPUT_SIZE, INPUT_SIZE, 3), dtype=np.float32))
    out_dir.mkdir(parents=True, exist_ok=True)
    fp16 = out_dir / "guide_viewfinder_fp16.tflite"
    int8 = out_dir / "guide_viewfinder_int8.tflite"
    fp16.write_bytes(_converter(model, quantize=False))
    int8.write_bytes(_converter(model, quantize=True))
    return fp16, int8


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--out-dir", type=Path, default=Path("ml/models"))
    args = p.parse_args()
    a, b = export_tflite(args.out_dir)
    print(f"wrote {a} ({a.stat().st_size} bytes)")
    print(f"wrote {b} ({b.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
