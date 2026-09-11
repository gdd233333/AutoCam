# autocam core-cpp

C++20 portable core. PR-06 only wires CMake, JNI, and a smoke `autocam::add`.

| Later PR | Header |
|----------|--------|
| PR-11 | `zoom_math.h` |
| PR-12 | `device_profile.h` |
| PR-14 | `composition.h` |
| PR-15 | `crop_rotate.h` |
| PR-16 | `lut.h` |
| PR-19 | `inference.h` |

## Host tests (no NDK)

```powershell
cmake -S shared/core-cpp -B build/core-cpp -DAUTOCAM_BUILD_TESTS=ON -DAUTOCAM_BUILD_JNI=OFF
cmake --build build/core-cpp
ctest --test-dir build/core-cpp --output-on-failure
```

## Android

Gradle `externalNativeBuild` builds `libautocam.so` (`arm64-v8a`, `x86_64`) via the same CMakeLists with `-DAUTOCAM_BUILD_JNI=ON`.
