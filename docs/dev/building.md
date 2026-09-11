# 构建

## 要求

- JDK 17（`JAVA_HOME`）。本机可用 conda 缓存：`D:\miniconda3\pkgs\openjdk-17.0.14-h5da7b33_0\Library`
- Android SDK，至少 `platforms;android-34` 与 build-tools
- 本机常见路径：`%LOCALAPPDATA%\Android\Sdk`

## Android 空应用（PR-01）

```powershell
$env:JAVA_HOME = "D:\miniconda3\pkgs\openjdk-17.0.14-h5da7b33_0\Library"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Copy-Item apps\android\local.properties.example apps\android\local.properties
# 按本机 SDK 路径改 sdk.dir（Windows 要转义反斜杠或用正斜杠）
cd apps\android
.\gradlew.bat assembleDebug
```

产物：`apps/android/app/build/outputs/apk/debug/app-debug.apk`

JVM 金测试（不需要设备）：

```powershell
.\gradlew.bat testDebugUnitTest
```

## 本 PR 没有的东西

- 相机权限与 Camera2 session（PR-07+）
- CMake / JNI（PR-06）
- 契约 schema CI（PR-02）
