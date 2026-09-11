package com.autocam.engine

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Compose (PR-04) and GoldenRunner share this bus. Every UI action is a CommandEnvelope.
 */
class CommandBus(private val engine: CameraEngine) {
    private val recorded = mutableListOf<JsonObject>()
    private var nextId = 1L

    fun recorded(): List<JsonObject> = recorded.toList()

    fun clearRecorded() {
        recorded.clear()
    }

    suspend fun dispatch(envelope: JsonObject): JsonElement? {
        val op = envelope["op"]?.jsonPrimitive?.content
            ?: error("CommandEnvelope missing op")
        val id = envelope["id"]?.jsonPrimitive?.contentOrNull ?: (nextId++).toString()
        val body = envelope["body"]
        val stored = buildJsonObject {
            put("op", op)
            put("id", id)
            if (body != null) put("body", body)
        }
        recorded += stored
        return when (op) {
            "openSession" -> {
                val req = EngineJson.decodeFromJsonElement<OpenSessionRequest>(requireBody(body, op))
                EngineJson.encodeToJsonElement(engine.openSession(req))
            }
            "closeSession" -> {
                engine.closeSession()
                null
            }
            "setCaptureParams" -> {
                engine.setCaptureParams(
                    EngineJson.decodeFromJsonElement(requireBody(body, op)),
                )
                null
            }
            "setZoom" -> {
                engine.setZoom(EngineJson.decodeFromJsonElement(requireBody(body, op)))
                null
            }
            "setPhysicalLensHint" -> {
                val hint = EngineJson.decodeFromJsonElement<JsonObject>(requireBody(body, op))
                val lens = hint["lensId"]
                val lensId = if (lens == null || lens is kotlinx.serialization.json.JsonNull) {
                    null
                } else {
                    lens.jsonPrimitive.content
                }
                engine.setPhysicalLensHint(lensId)
                null
            }
            "guideUser" -> {
                engine.guideUser(EngineJson.decodeFromJsonElement(requireBody(body, op)))
                null
            }
            "captureStill" -> EngineJson.encodeToJsonElement(engine.captureStill())
            "startVideo" -> {
                val opts = EngineJson.decodeFromJsonElement<VideoOptions>(requireBody(body, op))
                EngineJson.encodeToJsonElement(engine.startVideo(opts))
            }
            "stopVideo" -> EngineJson.encodeToJsonElement(engine.stopVideo())
            "applyCrop" -> {
                val cmd = EngineJson.decodeFromJsonElement<ApplyCrop>(requireBody(body, op))
                EngineJson.encodeToJsonElement(engine.applyCrop(cmd))
            }
            "loadDeviceProfile" -> {
                engine.loadDeviceProfile(requireBody(body, op).toString())
                null
            }
            "saveZoomCalibration" -> {
                engine.saveZoomCalibration(
                    EngineJson.decodeFromJsonElement(requireBody(body, op)),
                )
                null
            }
            else -> error("unknown op $op")
        }
    }

    suspend fun setZoom(cmd: SetZoom, id: String? = null) {
        dispatch(envelope("setZoom", EngineJson.encodeToJsonElement(cmd), id))
    }

    suspend fun captureStill(id: String? = null): JsonElement? {
        return dispatch(envelope("captureStill", body = null, id = id))
    }

    suspend fun applyCrop(cmd: ApplyCrop, id: String? = null): JsonElement? {
        return dispatch(envelope("applyCrop", EngineJson.encodeToJsonElement(cmd), id))
    }

    suspend fun saveZoomCalibration(profile: ZoomBlendProfile, id: String? = null) {
        dispatch(envelope("saveZoomCalibration", EngineJson.encodeToJsonElement(profile), id))
    }

    private fun envelope(op: String, body: JsonElement?, id: String?): JsonObject {
        return buildJsonObject {
            put("op", op)
            put("id", id ?: (nextId++).toString())
            if (body != null) put("body", body)
        }
    }

    private fun requireBody(body: JsonElement?, op: String): JsonElement {
        return body ?: error("op $op requires body")
    }
}
