# AutoCam

引导构图的静物相机：调用每一个物理镜头与完整变焦，预览里指挥用户把主体移到构图点并建议焦段，快门后自动裁切/微旋转，推荐 LUT，录像变焦可标定、切镜尽量无跳变。

- 产品名：AutoCam
- 包名：`com.autocam.app`
- 许可证：Apache-2.0
- 第一船：Xiaomi 15S Pro（`25042PN24C` / `dijun` / 玄戒 O1 / HyperOS 2）
- 架构规格：[docs/dev/architecture.md](docs/dev/architecture.md)
- GitHub：https://github.com/gdd233333/AutoCam

## 现在能做什么

Ship 0：Mock 取景器（Compose）+ 已冻结的 CameraEngine 契约。还没有真相机、没有模型。`engine.mock=true` 时不需要相机权限。

校验契约：

```powershell
python -m pip install -r contracts/tools/requirements.txt
python contracts/tools/validate.py
```

JVM 金测试（Mock，无真机）：

```powershell
cd apps\android
.\gradlew.bat testDebugUnitTest
```

```text
contracts/          # PR-02 起冻结 JSON 契约
apps/android/       # Ship 1：Compose + Camera2
apps/ios/           # Ship 2 占位
apps/harmony/       # HarmonyOS NEXT = Ship 3，不是 HyperOS
shared/core-cpp/    # 变焦 / 裁切 / LUT / 推理
ml/                 # 训练与导出
docs/user|dev|ml/
```

## 构建 Android 空应用

需要 JDK 17 与 Android SDK（本机已有 `platforms;android-34`）。

```powershell
$env:JAVA_HOME = "<jdk17>"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd apps\android
.\gradlew.bat assembleDebug
```

SDK 路径写在被 gitignore 的 `apps/android/local.properties`。可从 `local.properties.example` 复制。

## 文档

| 路径 | 内容 |
|------|------|
| [docs/user/getting_started.md](docs/user/getting_started.md) | 使用说明（随功能 PR 补） |
| [docs/dev/architecture.md](docs/dev/architecture.md) | 完整架构规格 |
| [docs/dev/contracts.md](docs/dev/contracts.md) | CameraEngine 命令闭包 |
| [docs/dev/building.md](docs/dev/building.md) | 构建与工具链 |
| [docs/ml/README.md](docs/ml/README.md) | 训练与端侧推理 |

## Git

Conventional Commits。每个最小任务一次 commit。远程：`origin` → https://github.com/gdd233333/AutoCam （公开）。大节点再 push。

```
feat(scope): ...
fix(scope): ...
chore: ...
docs: ...
build: ...
```
