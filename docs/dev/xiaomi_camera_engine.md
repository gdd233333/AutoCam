# Xiaomi Camera Engine SDK（Alternative G）

PR-07 评估。结论：**Ship 1 继续走 Camera2**。不引入小米相机生态引擎作为 HAL 后端。

## 探测

`XiaomiEngineProbe` 对下列类做 `Class.forName`（应用 classloader，不反射 `xiaomi.camera.*` 隐藏 API）：

- `com.xiaomi.camera.core.CameraEngine`
- `com.xiaomi.camera.engine.CameraEngine`
- `com.xiaomi.engine.MiaosaiEngine`
- `com.xiaomi.camera.algoengine.AlgoEngine`
- `com.xiaomi.camera.imagecodec.ImageCodec`
- `miui.camera.CameraEngine`

并检查是否安装 `com.android.camera` / `com.xiaomi.camera` / `com.mlab.cam`。系统相机包存在只说明 OEM 应用在，不代表有可链接的第三方 SDK。

## 为何不用

- 无公开、可进 Apache-2.0 应用的 Maven/AAR 文档，能稳定暴露 `CONTROL_ZOOM_RATIO` 与 physical camera id。
- 闭源 IQ 管线（MFNR / 厂商色）无法在不泄漏厂商类型的前提下塞进 `CameraEngine`。
- 公开 `getCameraIdList()` 只有 `0`/`1`。**硬探 id `2`/`3`/`4` 可以打开**：UW、Tele、以及 physical=`0,2,3` 的 logical `4`。
- 已安装 `com.android.camera`。`Class.forName` 全部未命中。不需要小米 SDK 才能用三颗镜头。

## 产品含义

第三方 Camera2 预览/JPEG **没有**系统相机的 Leica/MFNR 管线。`docs/user/getting_started.md` 应写明 IQ 差距。若日后小米发布正式第三方 Camera Engine SDK 且许可证允许，再开 `XiaomiEngine : CameraEngine` issue。
