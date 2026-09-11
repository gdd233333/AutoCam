# MockCameraEngine

实现在 **PR-03**：`apps/android/.../mock/MockCameraEngine.kt`。

本目录不放第二套契约。Mock 必须：

- 实现 `CameraEngine` 的闭包方法（见 `docs/dev/contracts.md`）
- 消费 `contracts/golden/fixtures/` 与 `contracts/golden/sequences/`
- 用 fake clock，不读真相机
- `flag.engine.mock=true` 时强制走 Mock，忽略任何想打开 Camera2 的请求

`OpenSessionRequest` **没有** `engine` 字段。
