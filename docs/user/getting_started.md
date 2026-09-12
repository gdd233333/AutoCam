# AutoCam 使用说明

当前是 **Mock 取景器**（`engine.mock=true`）。不需要相机权限，也不读写真实镜头。

## 打开应用

1. 安装 debug APK 或从 Android Studio 运行 `apps/android`。
2. 直接进入取景器。Mock 模式会跳过 `PermissionGate`。
3. 底部变焦滑杆范围约 0.61x–10x。拖到目标倍率后，应用通过 CommandBus 发送 `setZoom`（`rate=immediate`，`source=user_slider`）。
4. **Shutter** 拍一张静物（Mock JPEG 元数据），进入确认页。
5. 确认页可选 LUT chip（`warm` / `clean` / `vivid`）。点 Save 时，仅在选了 LUT 时 `applyCrop(bakeLut=true)`。Undo 丢弃。
6. **Zoom cal** 可调切镜滞回、fade、增益和色温偏移，保存为 `ZoomBlendProfile`。
7. **Debug** 可开关全部 `flag.*`。`ai.guide` 是 `off | rule | neural`（点 Cycle 循环）。默认 `off`。`grade.lut` 打开后确认页才出现 LUT chip。Export debug zip 含 flags 与日志，默认不含 HAL dump。

## 构图叠加

若 Mock 发出 `CompositionGuide`，叠加层按契约绘制三分线、主体框、箭头和准星。提示文案来自 `strings.xml` 键，例如「把主体移到准星」。叠加层不可点击。

## 真机相机

PR-07 起可在 Debug 页 **Dump HAL**（需 USB 调试 + CAMERA 权限）。dump 只枚举镜头和 session 组合，**不会开预览**。

Ship 1 走 Camera2，**没有**小米系统相机的 MFNR / Leica 成片管线，画质会低于 `com.android.camera`。

预览取景仍是下一阶段（PR-08）。当前默认 `engine.mock=true`。Debug 里关掉 `engine.mock` 后走 Camera2 Profile A 预览（需 CAMERA）。变焦滑杆改 `CONTROL_ZOOM_RATIO`，不重建 session。静物快门仍是 PR-09。

第一船设备：Xiaomi 15S Pro（玄戒 O1 / HyperOS）。
