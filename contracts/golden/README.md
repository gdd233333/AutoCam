# Golden sequences

PR-03 `GoldenRunner` 的输入。本目录的 JSON **必须**通过 `python contracts/tools/validate.py`。

## 语义（冻结）

| 规则 | 含义 |
|------|------|
| Fake clock | `MockCameraEngine.advanceNs(int64)`。`withinMs` 比的是 fake time，不是墙钟。 |
| Subset match | 期望对象的每个键必须存在于实际 JSON 且深等；实际可有多余键。 |
| N-frame 窗口 | `expectFrame` 默认在随后 **3** 帧内命中（`nFrameWindow`）。 |
| `guideUser` 轨迹 | 标注主体中心 `s += (panNx, panNy) * stepPerFrame`（默认 0.25 / 帧）。`guide_user_pan_zoom.json` 写死期望中心。 |
| 像素 | 不进 JSON。`previewHandle` 仅进程内。 |

## PR-04 门禁

Robolectric 拖动变焦滑杆时，CommandBus 录到的信封必须与 `sequences/set_zoom.json` 的 `send` **字段级 subset 相等**。

## 静物 fixtures

`fixtures/still_* /fixture.json`：无像素，只有主体框与构图类。v0 主体来自显著性 blob，不是人脸；`still_person` 只是可选偏置。
