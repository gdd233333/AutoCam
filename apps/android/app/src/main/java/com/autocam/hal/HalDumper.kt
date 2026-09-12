package com.autocam.hal

import android.content.Context
import android.os.Build
import com.autocam.engine.EngineEvent
import com.autocam.observability.EventLog
import java.io.File
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HalDumper(
    private val context: Context,
    private val eventLog: EventLog? = null,
) {
    fun dump(): HalDump {
        val enumerator = PhysicalLensEnumerator(context)
        val cameras = enumerator.enumerate()
        val manager = context.getSystemService(android.content.Context.CAMERA_SERVICE)
            as android.hardware.camera2.CameraManager
        val extraIds = ExtraIdProbe(manager, enumerator).probe()
        val extraWithChars = extraIds.filter { !it.inPublicList && it.characteristicsOk && it.node != null }
        val combos = cameras.flatMap { SessionComboProbe(context).probe(it) } +
            extraWithChars.mapNotNull { it.node }.flatMap { SessionComboProbe(context).probe(it) }
        return HalDump(
            dumpedAtIso = Instant.now().toString(),
            build = BuildDump(
                model = Build.MODEL,
                device = Build.DEVICE,
                product = Build.PRODUCT,
                hardware = Build.HARDWARE,
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                sdkInt = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE,
                fingerprint = Build.FINGERPRINT,
            ),
            cameras = cameras,
            extraIds = extraIds,
            sessionCombos = combos,
            xiaomiCameraEngine = XiaomiEngineProbe().probe(context),
        )
    }

    fun dumpToFile(): File {
        val dump = dump()
        val json = HalJson.encodeToString(dump)
        val files = listOf(
            File(context.filesDir, FILE_NAME),
            File(context.getExternalFilesDir(null), FILE_NAME),
        )
        var last: File = files.first()
        for (file in files) {
            file.parentFile?.mkdirs()
            file.writeText(json, Charsets.UTF_8)
            last = file
        }
        eventLog?.append(
            EngineEvent(
                timestampNs = System.nanoTime(),
                code = "hal_dump_saved",
                level = "info",
                messageKey = "event.hal_dump_saved",
            ),
        )
        return last
    }

    companion object {
        const val FILE_NAME: String = "hal_dump.json"
        val HalJson: Json = Json {
            encodeDefaults = true
            explicitNulls = true
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }
}
