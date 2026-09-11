# Feature flags

`SharedPreferences` 文件 `autocam`，键前缀 `flag.`。实现：`com.autocam.flags.FeatureFlags`。

| 键 | 类型 | Debug 默认 | Release 默认 |
|----|------|------------|--------------|
| `flag.engine.mock` | bool | true | false |
| `flag.hal.multi_lens` | bool | false | false |
| `flag.hal.zoom_ratio` | bool | true | true |
| `flag.zoom.blend` | bool | false | false |
| `flag.zoom.dual_physical` | bool | **false** | **false** |
| `flag.ai.guide` | enum `off\|rule\|neural` | `off` | `off` |
| `flag.ai.refine_still` | bool | false | false |
| `flag.grade.lut` | bool | false | false |
| `flag.capture.dng` | bool | false | false |
| `flag.capture.full_res` | bool | false | false |
| `flag.debug.include_hal_dump` | bool | false | false |
| `flag.debug.flag_secure` | bool | false | true |

`ai.guide` 从第一天就是枚举，没有 boolean 迁移。Debug 页可循环 `off → rule → neural`。

未通过金测试 + 真机冒烟前保持默认。AI / blend 失败不得阻止拍照。

## EventLog

`files/logs/autocam.log`，每条 `EngineEvent` 一行 JSON，4 MiB 环。无上传。

Debug zip（`files/debug/autocam-debug.zip`）：`flags.json` + 日志 + 可选 user profile / backend_probe。默认不含 `hal_dump.json`。
