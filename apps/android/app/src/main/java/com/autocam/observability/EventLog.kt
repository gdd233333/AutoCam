package com.autocam.observability

import com.autocam.engine.EngineEvent
import com.autocam.engine.EngineJson
import java.io.File
import kotlinx.serialization.encodeToString

/**
 * Local JSONL ring at `files/logs/autocam.log`. One EngineEvent per line. Cap 4 MiB.
 */
class EventLog(
    private val file: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val lock = Any()

    init {
        file.parentFile?.mkdirs()
    }

    fun append(event: EngineEvent) {
        val line = EngineJson.encodeToString(event) + "\n"
        synchronized(lock) {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() + line.length > maxBytes) {
                rotate(line.length.toLong())
            }
            file.appendText(line, Charsets.UTF_8)
        }
    }

    fun path(): File = file

    fun readLines(): List<String> {
        synchronized(lock) {
            if (!file.exists()) return emptyList()
            return file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        }
    }

    private fun rotate(incoming: Long) {
        val existing = if (file.exists()) file.readLines(Charsets.UTF_8) else emptyList()
        val keep = ArrayDeque<String>()
        var size = 0L
        val budget = (maxBytes / 2).coerceAtLeast(incoming)
        for (line in existing.asReversed()) {
            val n = line.toByteArray(Charsets.UTF_8).size + 1L
            if (keep.isNotEmpty() && size + n > budget) break
            keep.addFirst(line)
            size += n
        }
        file.writeText(
            if (keep.isEmpty()) {
                ""
            } else {
                keep.joinToString(separator = "\n", postfix = "\n")
            },
            Charsets.UTF_8,
        )
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 4L * 1024 * 1024
        const val RELATIVE_PATH: String = "logs/autocam.log"
    }
}
