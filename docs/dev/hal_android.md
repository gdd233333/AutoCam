# Android Camera2 HAL dump

PR-07 硬门闩。真机枚举 **不出预览**。PR-08+ 只消费 dump，不得把架构文档里的 15S Pro 猜测尺寸当实现常量。

## 产出

设备上：

- `files/hal_dump.json`
- `Android/data/com.autocam.app/files/hal_dump.json`（adb pull）

仓库副本（本 PR 从 15S Pro 拉回）：`docs/dev/dumps/xiaomi_15s_pro_hal_dump.json`

内容：

- `Build.MODEL` / `DEVICE` / `PRODUCT` / `HARDWARE` / `MANUFACTURER`
- 每个 cameraId：facing、hardware level、capabilities、physical ids、焦距、光圈、`CONTROL_ZOOM_RATIO` 范围、sensor 尺寸、JPEG/YUV/PRIVATE 输出尺寸
- Profile A / A JPEG-max / B / B 无分析 / C / dual-physical 的 `isSessionConfigurationSupported`
- 小米相机生态引擎 SDK 探测（见 [xiaomi_camera_engine.md](xiaomi_camera_engine.md)）

查询顺序：API 35+ `CameraManager.getCameraDeviceSetup`；失败再 `openCamera` + `CameraDevice.isSessionConfigurationSupported`。不 `setRepeatingRequest`。

## 运行

```powershell
$env:JAVA_HOME = "D:\miniconda3\pkgs\openjdk-17.0.14-h5da7b33_0\Library"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd apps\android
.\gradlew.bat connectedDebugAndroidTest
adb pull /sdcard/Android/data/com.autocam.app/files/hal_dump.json docs/dev/dumps/xiaomi_15s_pro_hal_dump.json
```

或在 App Debug 页点 **Dump HAL**（需 CAMERA 权限；Mock 模式下仍可枚举 characteristics）。

`hal.multi_lens` 仍默认 false。绑定 DeviceProfile 是 PR-12。

## 15S Pro (`25042PN24C` / `dijun`) 实测摘要

2026-09-12，HyperOS / Android 17 (SDK 37)，`HARDWARE=O1_asic`。

| cameraId | facing | level | 焦距 mm | 光圈 | zoomRatio | physical ids | LOGICAL_MULTI_CAMERA |
|----------|--------|-------|---------|------|-----------|--------------|----------------------|
| `0` | back | FULL | 6.68 | 1.44 | **1.0–10.0** | 空 | **false** |
| `1` | front | FULL | 2.83 | 2.0 | 1.0–10.0 | 空 | false |

`CameraManager.getCameraIdList()` 对第三方只给 **0 / 1**。`dumpsys media.camera` 另有隐藏 id（5/6/7），第三方 Camera2 **打不开**。

Session 组合（`CameraDevice.isSessionConfigurationSupported`，camera `0`）：

| profile | 流 | supported |
|---------|----|-----------|
| A_STILL | PRIV 1920×1080 + YUV 1920×1080 + JPEG 4080×3072 | **true** |
| A_STILL_JPEG_MAX | 同上 + JPEG 4096×3072 | **true** |
| B_VIDEO | PRIV preview + PRIV record 1080p + YUV analysis | **true** |
| B_VIDEO_NO_ANALYSIS | 无 YUV | **true** |
| C_DEGRADED_STILL / VIDEO | 两流 | **true** |
| DUAL_PHYSICAL_YUV | 未探测 | 无 physicalCameraIds |

含义（PR-08 必须遵守）：

- **公开** `getCameraIdList()` 只有 `0`/`1`。UW/Tele **可以**用隐藏 id 打开（见下）。
- JPEG **默认** 4080×3072 / 4096×3072，约 12.5 MP（QCFA 四合一）。
- **50 MP 存在**：厂商流表 `xiaomi.scaler.availableStreamConfigurations` 有 JPEG **8192×6144**（50.3 MP）和 8160×6144。公共 `StreamConfigurationMap` **不列出**这些尺寸，也没有 `ULTRA_HIGH_RESOLUTION_SENSOR`。
- `com.xiaomi.miCam.sensorInfo.qcfaSupported=1`，CaptureRequest 键 `qcfa.isSuperRemosaic` 存在。系统相机的「50MP 单独开关」就是这条 remosaic，不是另一颗镜头。
- 第三方探测：`isSessionConfigurationSupported(PRIV 1080p + JPEG 8192×6144)` 在 id `0/2/3/4` 上均为 **true**。真正出 50MP 像素还要在 still 请求里打开 remosaic；录像/部分算法模式不会走这条。
- `CameraDeviceSetup` 反射未用上，回退 `openCamera`。需要用户授予 CAMERA。

## 隐藏 id（已用 Camera2 打开）

`getCameraIdList()` 不返回它们，但 `getCameraCharacteristics` + `openCamera` **成功**（第三方 + CAMERA 权限）：

| Camera2 id | HAL | facing | 焦距 mm | 光圈 | 35mm（dumpsys） | 角色 |
|------------|-----|--------|---------|------|-----------------|------|
| **`2`** | vendor_xring/2 | back | 2.16 | 2.2 | **14 mm** | UW |
| **`3`** | vendor_xring/3 | back | 19.4 | 2.5 | **120 mm** | Tele 5x |
| **`4`** | vendor_xring/4 | back | 6.68 | 1.44 | 23 mm | **LOGICAL** physical=`0,2,3` |
| `5`/`6`/`7` | 5–7 | front | 2.83 | 2.0 | — | 前置变体 |
| `8` | 8 | back | 6.68 | 1.44 | 23 mm | 另一 LOGICAL `0,2,3` |
| `9`/`10` | 9–10 | — | — | — | — | **system only**，characteristics 被拒 |

`4` 的 `DUAL_PHYSICAL_YUV`（PRIV + physical 0 YUV + physical 2 YUV）**supported=true**。

Logical **`4` 变焦（dump 真值，第三方必须遵守）**：

| 来源 | 范围 |
|------|------|
| 公开 `CONTROL_ZOOM_RATIO_RANGE` | **1.0–10.0** |
| `android.scaler.availableMaxDigitalZoom` | 10.0 |
| `com.xiaomi.camera.videosat.zoomRange` | 0.6–15 |
| `xiaomi.smoothTransition.xiaomiSatMaxZoom` | **120**（系统相机 100x+ SAT） |

第三方 repeating request **只能**写公开 1–10。把 100/120 塞进 `CONTROL_ZOOM_RATIO` 会越界，HAL 可能直接死。系统相机的 100x+ 走厂商 SAT/超分，不是这条 AOSP key。id `4` 与 `0/2/3/8` 冲突；第三方 `setRepeatingRequest` 会 `session_error`（已不再杀进程）。

**软件 SAT（Cycle cam `4` 或 `hal.multi_lens`）**：不打开硬件 `4`。按用户变焦 0.61–10（相对主摄 23 mm）在物理 `2` / `0` / `3` 之间切，滞回 2 次、freeze+fade 盖住 close/open。切点默认 UW→main `0.95` / main→UW `0.72`，main→tele `4.80` / tele→main `3.60`。每颗镜头的 `CONTROL_ZOOM_RATIO` 从该传感器的 1.0 起算（UW 用户 0.61→请求 1.0，tele 用户 5.22→请求 1.0）。直接开 `2`/`3` 仍可单独用 UW/Tele。

## PR-08 Profile A 预览（dump 真值）

默认 `engine.mock=true`。Debug 关掉 mock 后走 Camera2。

| 项 | 值 |
|----|----|
| 默认 cameraId | 公开 **`0`** |
| `hal.multi_lens=true` 或 Cycle cam `4` | **软件 SAT**：物理 `2`/`0`/`3` + freeze-fade，不打开硬件 `4` |
| Preview PRIVATE | 1920×1080 |
| Analysis YUV_420_888 | 1920×1080，只 `acquireLatestImage().close()`，不推理 |
| JPEG（session 占位，拍照 PR-09） | 4080×3072 量级 |
| 变焦 | `CONTROL_ZOOM_RATIO` **1.0–10.0**，不重建 session |
| Repeating | `TEMPLATE_PREVIEW` 30 fps |

TextureView 出预览。参数条：AE / AF / AWB / EV，AE off 时 ISO。
