from __future__ import annotations

from pathlib import Path

import yaml

ONNX_TO_TFLITE = {
    "Conv": "CONV_2D",
    "Relu": "RELU",
    "Add": "ADD",
    "Mul": "MUL",
    "Concat": "CONCATENATION",
    "Pad": "PAD",
    "Resize": "RESIZE_BILINEAR",
    "AveragePool": "AVERAGE_POOL_2D",
    "GlobalAveragePool": "MEAN",
    "ReduceMean": "MEAN",
    "Gemm": "FULLY_CONNECTED",
    "MatMul": "FULLY_CONNECTED",
    "Softmax": "SOFTMAX",
    "Sigmoid": "LOGISTIC",
    "Reshape": "RESHAPE",
    "Transpose": "TRANSPOSE",
    "Flatten": "RESHAPE",
    "Squeeze": "RESHAPE",
    "Unsqueeze": "RESHAPE",
    "Clip": "MINIMUM",
    "Max": "MAXIMUM",
    "Min": "MINIMUM",
    "QuantizeLinear": "QUANTIZE",
    "DequantizeLinear": "DEQUANTIZE",
    "Constant": None,
    "Identity": None,
    "Shape": None,
    "Gather": "STRIDED_SLICE",
    "Slice": "STRIDED_SLICE",
    "Cast": None,
    "BatchNormalization": "CONV_2D",  # must be fused before export; mapped only as last resort
}


def load_whitelist(path: Path) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def allowed_names(wl: dict) -> set[str]:
    names = set(wl.get("required") or [])
    names.update(wl.get("allowed_if_logged") or [])
    return names


def tflite_opcodes(model_path: Path) -> list[str]:
    from tensorflow.lite.python import schema_py_generated as schema

    buf = bytearray(model_path.read_bytes())
    model = schema.Model.GetRootAsModel(buf, 0)
    names: list[str] = []
    builtin = schema.BuiltinOperator
    lookup = {getattr(builtin, k): k for k in dir(builtin) if k.isupper()}
    subgraph = model.Subgraphs(0)
    for i in range(subgraph.OperatorsLength()):
        op = subgraph.Operators(i)
        code = model.OperatorCodes(op.OpcodeIndex())
        builtin_code = code.BuiltinCode()
        name = lookup.get(builtin_code, f"UNKNOWN_{builtin_code}")
        if name == "CUSTOM":
            custom = code.CustomCode()
            name = f"CUSTOM:{custom.decode() if custom else ''}"
        names.append(name)
    return names


def onnx_opcodes(model_path: Path) -> list[str]:
    import onnx

    model = onnx.load(str(model_path))
    return [node.op_type for node in model.graph.node]


def assert_subset(used: list[str], allowed: set[str], *, fused_bn_ok: bool = True) -> None:
    bad: list[str] = []
    for op in used:
        if op is None:
            continue
        if op in allowed:
            continue
        if fused_bn_ok and op in ("BatchNormalization", "CONV_2D"):
            continue
        mapped = ONNX_TO_TFLITE.get(op, op)
        if mapped is None:
            continue
        if mapped not in allowed:
            bad.append(op)
    if bad:
        raise SystemExit(f"opcode not in whitelist: {sorted(set(bad))}")
