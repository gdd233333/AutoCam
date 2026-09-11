package com.autocam.observability

import com.autocam.engine.EngineJson
import com.autocam.flags.FeatureFlags
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString

class DebugBundle(
    private val filesDir: File,
    private val flags: FeatureFlags,
    private val eventLog: EventLog,
) {
    fun export(): File {
        val outDir = File(filesDir, "debug")
        outDir.mkdirs()
        val out = File(outDir, "autocam-debug.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("flags.json"))
            zip.write(EngineJson.encodeToString(flags.readSnapshot()).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val log = eventLog.path()
            if (log.exists()) {
                zip.putNextEntry(ZipEntry("autocam.log"))
                zip.write(log.readBytes())
                zip.closeEntry()
            }
            val probe = File(filesDir, "profiles/backend_probe.json")
            if (probe.exists()) {
                zip.putNextEntry(ZipEntry("backend_probe.json"))
                zip.write(probe.readBytes())
                zip.closeEntry()
            }
            val userZoom = File(filesDir, "profiles").listFiles()
                ?.firstOrNull { it.name.endsWith(".user.json") }
            if (userZoom != null && userZoom.exists()) {
                zip.putNextEntry(ZipEntry(userZoom.name))
                zip.write(userZoom.readBytes())
                zip.closeEntry()
            }
            if (flags.debugIncludeHalDump()) {
                val dump = File(filesDir, "hal_dump.json")
                if (dump.exists()) {
                    zip.putNextEntry(ZipEntry("hal_dump.json"))
                    zip.write(dump.readBytes())
                    zip.closeEntry()
                }
            }
        }
        return out
    }
}
