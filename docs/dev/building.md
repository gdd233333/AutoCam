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

## Native（C++ / JNI）

Host googletest（不需要 NDK）：

```powershell
cmake -S shared/core-cpp -B build/core-cpp -DAUTOCAM_BUILD_TESTS=ON -DAUTOCAM_BUILD_JNI=OFF
cmake --build build/core-cpp
ctest --test-dir build/core-cpp --output-on-failure
```

Android `libautocam.so` 随 `assembleDebug` 由 CMake 编出（需要 NDK + SDK CMake 3.22.1）。`NativeCore.tryAdd` 在 JVM 单测里必须返回 null。

## 尚未接入

- Camera2 session 与 HAL dump（PR-07+）
