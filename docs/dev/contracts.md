# CameraEngine 契约

Kotlin 方法名 = CommandBus `op` = `contracts/schemas/<Name>.schema.json`。没有 `jsonrpc.md`，没有 HTTP 服务。

校验：

```powershell
python -m pip install -r contracts/tools/requirements.txt
python contracts/tools/validate.py
```

CI：`.github/workflows/ci.yml` 在每次 push / PR 跑同一命令。

## 信封

```json
{ "op": "setZoom", "id": "3", "body": { "zoomRatio": 2.0, "rate": "immediate", "source": "user_slider" } }
```

`id` 是单调字符串。`closeSession` / `captureStill` / `stopVideo` **没有** `body`。

`OpenSessionRequest` **没有** `engine` 字段。Mock vs Camera2 由 `flag.engine.mock` 决定。

## 闭包 `op`

| op | body schema | 结果 / 流 |
|----|-------------|-----------|
| `openSession` | OpenSessionRequest | CameraSession |
| `closeSession` | （无） | — |
| `setCaptureParams` | CaptureParams | — |
| `setZoom` | SetZoom | frames |
| `setPhysicalLensHint` | SetPhysicalLensHint | — |
| `guideUser` | GuideUser | frames（fixture 主体按 pan 移动） |
| `captureStill` | （无） | StillResult，`appliedLutId=null` |
| `startVideo` | VideoOptions | VideoSession |
| `stopVideo` | （无） | VideoClipResult |
| `applyCrop` | ApplyCrop | StillResult；仅 `bakeLut=true` 才写 `appliedLutId` |
| `loadDeviceProfile` | DeviceProfile | — |
| `saveZoomCalibration` | ZoomBlendProfile | — |

引擎→UI 流（不是 op）：`ViewfinderFrame`、`CompositionGuide`、`FilterRecommendation`、`EngineEvent`。

## Overlay

`grid_thirds | subject_box | target_reticle | pan_arrow | hint_text | horizon_line`

坐标：取景器归一化，原点左上，已补偿 `rotationDeg`。`hint_text.key` 只允许 strings.xml 键。

## EngineEvent.code

`session_open | session_close | session_error | stream_profile | lens_switch | lens_hint_ignored | freeze_fade | inference_backend | inference_ms | inference_fallback | capture_ms | zoom_cmd_ms | zoom_apply_ms | params_rejected | permission_needed | hal_dump_saved | backend_probe_restored`

## 金测试

见 `contracts/golden/README.md`。

PR-03：`GoldenRunner` + `MockCameraEngine`（`engine.mock=true`）在 JVM 上跑全部 `golden/sequences/*.json` 与静物 fixtures。

```powershell
$env:JAVA_HOME = "D:\miniconda3\pkgs\openjdk-17.0.14-h5da7b33_0\Library"
cd apps\android
.\gradlew.bat testDebugUnitTest
```

- Fake clock：`MockCameraEngine.advanceNs` / `tickFrame`（30 fps = 33_333_333 ns）
- Subset match：期望键必须存在且深等；实际可多键
- `guideUser`：`s += (panNx, panNy) * stepPerFrame`
- 切镜：滞回 2 帧后 `lens_switch`，下一帧起 `freeze_fade`

PR-04：Robolectric 拖动 `zoom_slider`，CommandBus 录到的信封必须与 `set_zoom.json` 的 `send` 字段级 subset 相等。Mock 模式跳过相机权限。

PR-05：`flag.ai.guide` 为枚举 `off|rule|neural`（默认 `off`）。`flag.engine.mock` 控制 PermissionGate。`EventLog` 写 `files/logs/autocam.log`。详见 [flags.md](flags.md)。

PR-06：`shared/core-cpp` + JNI `libautocam`。烟测符号 `autocam::add`；变焦数学仍未实现。

PR-07：真机 `hal_dump.json`。见 [hal_android.md](hal_android.md)。PR-08+ 只消费 dump。

PR-08：Camera2 Profile A 预览 + CaptureParams。分析 YUV 只 acquire。变焦不重建 session。

静物 fixtures：`still_object_table`、`still_food`、`still_building`、`still_person`。无像素。

## 禁止

- LUT id 含 `leica`
- 在契约里写死 15S Pro 的 `physicalCameraId`
- 第二套 JSON-RPC 文档
