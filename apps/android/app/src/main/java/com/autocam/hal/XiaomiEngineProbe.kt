package com.autocam.hal

import android.content.Context
import android.content.pm.PackageManager

class XiaomiEngineProbe {
    fun probe(context: Context): XiaomiEngineDump {
        val present = mutableListOf<String>()
        for (name in CLASSES) {
            try {
                Class.forName(name, false, context.classLoader)
                present += name
            } catch (_: ClassNotFoundException) {
                // expected for third-party apps
            }
        }
        val cameraPackages = CANDIDATE_PACKAGES.filter { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        val notes = buildString {
            append("No Xiaomi Camera Engine AAR is on AutoCam's classpath. ")
            append("Ship 1 stays Camera2. System package com.android.camera is the OEM app, not a third-party SDK. ")
            if (present.isEmpty()) {
                append("Runtime Class.forName of documented/guessed engine classes all missed.")
            } else {
                append("Unexpected classes present: $present")
            }
        }
        return XiaomiEngineDump(
            sdkOnClasspath = present.isNotEmpty(),
            classesTried = CLASSES,
            classesPresent = present,
            cameraPackages = cameraPackages,
            notes = notes,
        )
    }

    companion object {
        val CLASSES = listOf(
            "com.xiaomi.camera.core.CameraEngine",
            "com.xiaomi.camera.engine.CameraEngine",
            "com.xiaomi.engine.MiaosaiEngine",
            "com.xiaomi.camera.algoengine.AlgoEngine",
            "com.xiaomi.camera.imagecodec.ImageCodec",
            "miui.camera.CameraEngine",
        )
        val CANDIDATE_PACKAGES = listOf(
            "com.android.camera",
            "com.xiaomi.camera",
            "com.mlab.cam",
        )
    }
}
