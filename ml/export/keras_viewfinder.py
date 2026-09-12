"""Keras NHWC replica of ViewfinderNet for TFLite export. Same MNV4-Conv-S spec."""

from __future__ import annotations

from ml.students.spec import FEATURE_CHANNELS, INPUT_SIZE, MNV4_CONV_S, NUM_CLASSES
from ml.students.viewfinder import make_divisible


def _conv_bn_relu(x, out_ch, kernel, stride, name):
    import tensorflow as tf

    pad = (kernel - 1) // 2
    if pad:
        x = tf.keras.layers.ZeroPadding2D(pad, name=f"{name}_pad")(x)
    x = tf.keras.layers.Conv2D(
        out_ch, kernel, strides=stride, padding="valid", use_bias=False, name=f"{name}_conv"
    )(x)
    x = tf.keras.layers.BatchNormalization(name=f"{name}_bn")(x)
    return tf.keras.layers.ReLU(name=f"{name}_relu")(x)


def _dw(x, kernel, stride, name):
    import tensorflow as tf

    pad = (kernel - 1) // 2
    if pad:
        x = tf.keras.layers.ZeroPadding2D(pad, name=f"{name}_pad")(x)
    x = tf.keras.layers.DepthwiseConv2D(
        kernel, strides=stride, padding="valid", use_bias=False, name=f"{name}_dw"
    )(x)
    return tf.keras.layers.BatchNormalization(name=f"{name}_bn")(x)


def _uib(x, in_ch, out_ch, expand, start_k, mid_k, stride, name):
    import tensorflow as tf

    shortcut = x
    if start_k:
        x = _dw(x, start_k, 1, f"{name}_sdw")
    hidden = make_divisible(in_ch * expand)
    x = tf.keras.layers.Conv2D(hidden, 1, use_bias=False, name=f"{name}_exp")(x)
    x = tf.keras.layers.BatchNormalization(name=f"{name}_exp_bn")(x)
    x = tf.keras.layers.ReLU(name=f"{name}_exp_relu")(x)
    if mid_k:
        x = _dw(x, mid_k, stride, f"{name}_mdw")
        x = tf.keras.layers.ReLU(name=f"{name}_mdw_relu")(x)
    x = tf.keras.layers.Conv2D(out_ch, 1, use_bias=False, name=f"{name}_proj")(x)
    x = tf.keras.layers.BatchNormalization(name=f"{name}_proj_bn")(x)
    if stride == 1 and in_ch == out_ch:
        x = tf.keras.layers.Add(name=f"{name}_add")([shortcut, x])
    return x


def build_keras_viewfinder():
    import tensorflow as tf

    inp = tf.keras.Input(shape=(INPUT_SIZE, INPUT_SIZE, 3), name="preview_rgb")
    x = inp
    c = 3
    for i, (kind, *cfg) in enumerate(MNV4_CONV_S):
        if kind == "conv_bn":
            k, s, f = cfg
            x = _conv_bn_relu(x, f, k, s, f"c{i}")
            c = f
        else:
            start_k, mid_k, s, f, e = cfg
            x = _uib(x, c, f, e, start_k, mid_k, s, f"u{i}")
            c = f
    x = tf.keras.layers.GlobalAveragePooling2D(name="gap")(x)
    box = tf.keras.layers.Dense(4, name="box_fc")(x)
    box = tf.keras.layers.Activation("sigmoid", name="subject_box")(box)
    obj = tf.keras.layers.Dense(1, name="obj_fc")(x)
    obj = tf.keras.layers.Activation("sigmoid", name="subject_obj")(obj)
    logits = tf.keras.layers.Dense(NUM_CLASSES, name="composition_logits")(x)
    return tf.keras.Model(inp, [box, obj, logits], name="viewfinder")
