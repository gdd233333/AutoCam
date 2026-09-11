# AutoCam iOS（Ship 2）

本目录目前只有说明，没有 Xcode 工程。Ship 1 是 Android / HyperOS。

待办（不在本阶段实现）：

- `AVCaptureDevice.DiscoverySession` 枚举 constituent devices
- `videoZoomFactor` 映射到契约 `zoomRatio`
- 虚拟设备 vs 物理镜头切换
- 与 `contracts/` 同一套 CommandBus 语义

不要在这里引入第三方相机 SDK 当作 HAL。
