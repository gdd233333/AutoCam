package com.autocam.infer

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.tensorflow.lite.Interpreter

data class NetOut(
    val box: FloatArray,
    val obj: Float,
    val logits: FloatArray,
)

/**
 * Ship 1 viewfinder interpreter. Missing model => available=false (neural falls back to rule).
 * vendorNpuSymbols stays empty; GPU FP16 then INT8 CPU.
 */
class InferenceRuntime(
    context: Context,
    private val probe: BackendProbe = BackendProbe(context.filesDir),
) {
    private val modelsDir = File(context.filesDir, "models")
    private var interpreter: Interpreter? = null
    private var sha: String = ""
    private var backend: String = "none"

    fun available(): Boolean = interpreter != null

    fun backendName(): String = backend

    fun loadBest(): Boolean {
        val fp16 = File(modelsDir, "guide_viewfinder_fp16.tflite")
        val int8 = File(modelsDir, "guide_viewfinder_int8.tflite")
        val preferred = when {
            fp16.exists() -> fp16 to "litert_gpu"
            int8.exists() -> int8 to "cpu_int8"
            else -> null
        }
        if (preferred == null) {
            interpreter = null
            backend = "none"
            return false
        }
        val (file, name) = preferred
        sha = sha256(file)
        val cached = probe.lastGoodIfShaMatches(sha)
        backend = cached?.backend ?: name
        return runCatching {
            interpreter?.close()
            interpreter = Interpreter(file)
            probe.save(BackendProbeResult(sha, backend, warmupMs = 0, lastGood = true))
            true
        }.getOrDefault(false)
    }

    fun run(rgbNhwc: FloatArray): NetOut? {
        val itp = interpreter ?: return null
        if (rgbNhwc.size != 256 * 256 * 3) return null
        val input = rgbNhwc.copyOf()
        val box = Array(1) { FloatArray(4) }
        val obj = Array(1) { FloatArray(1) }
        val logits = Array(1) { FloatArray(8) }
        val outputs = mapOf(0 to box, 1 to obj, 2 to logits)
        return runCatching {
            itp.runForMultipleInputsOutputs(arrayOf(input), outputs)
            NetOut(box[0], obj[0][0], logits[0])
        }.getOrNull()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
