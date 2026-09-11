package com.autocam.observability

import com.autocam.engine.EngineEvent
import com.autocam.engine.EngineJson
import com.autocam.flags.FeatureFlags
import com.autocam.flags.MemoryFlagStore
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogTest {
    @Test
    fun appendsOneJsonLinePerEvent() {
        val dir = createTempDirectory("autocam-log").toFile()
        val log = EventLog(File(dir, "autocam.log"))
        log.append(EngineEvent(timestampNs = 1, code = "session_open", level = "info"))
        log.append(EngineEvent(timestampNs = 2, code = "lens_switch", level = "info"))
        val lines = log.readLines()
        assertEquals(2, lines.size)
        val first = EngineJson.decodeFromString<EngineEvent>(lines[0])
        assertEquals("session_open", first.code)
        val second = EngineJson.decodeFromString<EngineEvent>(lines[1])
        assertEquals("lens_switch", second.code)
    }

    @Test
    fun ringDropsOldestWhenOverCap() {
        val dir = createTempDirectory("autocam-log-ring").toFile()
        val log = EventLog(File(dir, "autocam.log"), maxBytes = 400)
        repeat(40) { i ->
            log.append(
                EngineEvent(
                    timestampNs = i.toLong(),
                    code = "zoom_apply_ms",
                    level = "debug",
                    messageKey = "event.zoom_apply_ms",
                ),
            )
        }
        assertTrue(log.path().length() <= 400)
        val lines = log.readLines()
        assertTrue(lines.isNotEmpty())
        val last = EngineJson.decodeFromString<EngineEvent>(lines.last())
        assertEquals(39, last.timestampNs)
    }
}

class DebugBundleTest {
    @Test
    fun exportOmitsHalDumpByDefault() {
        val dir = createTempDirectory("autocam-debug").toFile()
        File(dir, "hal_dump.json").writeText("{}")
        val flags = FeatureFlags(MemoryFlagStore(), debugBuild = true)
        val log = EventLog(File(dir, "logs/autocam.log"))
        log.append(EngineEvent(1, "session_open", "info"))
        val zip = DebugBundle(dir, flags, log).export()
        assertTrue(zip.exists())
        val names = java.util.zip.ZipFile(zip).use { z -> z.entries().toList().map { it.name } }
        assertTrue(names.contains("flags.json"))
        assertTrue(names.contains("autocam.log"))
        assertFalse(names.contains("hal_dump.json"))
    }
}
