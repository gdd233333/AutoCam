package com.autocam.infer

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackendProbeResult(
    val modelSha256: String,
    val backend: String,
    val warmupMs: Long,
    val lastGood: Boolean,
)

class BackendProbe(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(dir, FILE_NAME)

    fun load(): BackendProbeResult? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<BackendProbeResult>(file.readText()) }.getOrNull()
    }

    fun save(result: BackendProbeResult) {
        dir.mkdirs()
        file.writeText(json.encodeToString(result))
    }

    fun lastGoodIfShaMatches(sha: String): BackendProbeResult? {
        val prev = load() ?: return null
        return if (prev.modelSha256 == sha && prev.lastGood) prev else null
    }

    companion object {
        const val FILE_NAME: String = "backend_probe.json"
    }
}
