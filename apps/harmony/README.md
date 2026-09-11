这不是 HyperOS；不要为 15S Pro 构建本目录。

Xiaomi 15S Pro / 玄戒 O1 跑的是 **HyperOS 2 on AOSP Android 15**。本目录留给 **Huawei HarmonyOS NEXT（Ship 3）**。

在 Ship 1 不要建 ArkTS 工程、不要引入 `@ohos.multimedia.camera`。Camera2 路径在 `apps/android/`。

待 Ship 3 才填写的对照（空表，禁止编造 API）：

| AutoCam 契约 | HarmonyOS NEXT Camera Kit |
|--------------|---------------------------|
| `CONTROL_ZOOM_RATIO` | |
| physical camera ids | |
| still JPEG | |
| persistent video surface | |
