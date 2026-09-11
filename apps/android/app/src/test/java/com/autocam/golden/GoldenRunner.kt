package com.autocam.golden

import com.autocam.engine.BoxNorm
import com.autocam.engine.CommandBus
import com.autocam.engine.EngineJson
import com.autocam.engine.JsonSubset
import com.autocam.engine.OpenSessionRequest
import com.autocam.mock.MockCameraEngine
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.contentOrNull

class GoldenRunner(
    val engine: MockCameraEngine = MockCameraEngine(),
    val bus: CommandBus = CommandBus(engine),
) {
    fun playResource(path: String) {
        play(parse(readResource(path)))
    }

    fun play(sequence: JsonObject) {
        engine.stepPerFrame = sequence["stepPerFrame"]?.jsonPrimitive?.double ?: 0.25
        sequence["fixture"]?.jsonPrimitive?.contentOrNull?.let { loadFixture(it) }
        sequence["subject0"]?.jsonObject?.let { sub ->
            engine.setSubject(sub.getValue("nx").jsonPrimitive.double, sub.getValue("ny").jsonPrimitive.double)
        }
        if (sequence.containsKey("send") || sequence.containsKey("steps")) {
            sequence["send"]?.jsonObject?.let { runSendAndExpect(sequence, it) }
            sequence["steps"]?.jsonArray?.forEach { step ->
                runStep(step.jsonObject)
            }
        }
        sequence["expectSubjectTrajectory"]?.jsonArray?.let { verifyTrajectory(it) }
    }

    private fun runStep(step: JsonObject) {
        runSendAndExpect(step, step["send"]?.jsonObject)
    }

    private fun runSendAndExpect(node: JsonObject, send: JsonObject?) {
        val eventsBefore = engine.eventLog().size
        val framesBefore = engine.frameLog().size
        val timeBefore = engine.timeNs
        var result: JsonElement? = null

        if (send != null) {
            maybeAutoOpen(send)
            result = kotlinx.coroutines.runBlocking { bus.dispatch(send) }
        }
        node["holdFrames"]?.jsonPrimitive?.int?.let { n ->
            repeat(n) { engine.tickFrame() }
        }
        node["advanceNs"]?.jsonPrimitive?.long?.let { engine.advanceNs(it) }

        node["expectSession"]?.let { expected ->
            val actual = result ?: encode(engine.currentSession()!!)
            assertSubset("expectSession", expected, actual)
        }
        node["expectStill"]?.let { expected ->
            val actual = result ?: error("expectStill but no still result")
            assertSubset("expectStill", expected, actual)
        }
        node["expectFrame"]?.let { expected ->
            awaitFrame(expected, node, framesBefore, timeBefore)
        }
        node["expectEvent"]?.let { expected ->
            val found = engine.eventLog().drop(eventsBefore).any { event ->
                JsonSubset.matches(expected, encode(event))
            }
            if (!found) {
                val codes = engine.eventLog().drop(eventsBefore).map { it.code }
                error("expectEvent $expected not in $codes")
            }
        }
        node["expectGuide"]?.let { expected ->
            val last = engine.guideLog().lastOrNull() ?: error("no guides")
            assertSubset("expectGuide", expected, encode(last))
        }
        node["expectFilters"]?.let { expected ->
            val last = engine.filterLog().lastOrNull() ?: error("no filters")
            assertSubset("expectFilters", expected, encode(last))
        }
    }

    private fun awaitFrame(
        expected: JsonElement,
        node: JsonObject,
        framesBefore: Int,
        timeBefore: Long,
    ) {
        val window = node["nFrameWindow"]?.jsonPrimitive?.int ?: 3
        val withinMs = node["withinMs"]?.jsonPrimitive?.double
        fun elapsedMs(): Double = (engine.timeNs - timeBefore) / 1_000_000.0
        fun searchExisting(): Boolean {
            return engine.frameLog().drop(framesBefore).any { frame ->
                JsonSubset.matches(expected, encode(frame))
            }
        }
        if (searchExisting()) return
        repeat(window) {
            if (withinMs != null && elapsedMs() > withinMs) {
                error("expectFrame exceeded withinMs=$withinMs (elapsed=${elapsedMs()})")
            }
            engine.tickFrame()
            if (searchExisting()) return
        }
        val last = engine.frameLog().lastOrNull()
        val mismatch = last?.let {
            JsonSubset.mismatch(expected, encode(it))
        }
        error("expectFrame not met in $window frames: $mismatch last=$last")
    }

    private fun verifyTrajectory(points: JsonArray) {
        var lastFrame = -1
        for (el in points) {
            val point = el.jsonObject
            val frame = point.getValue("frame").jsonPrimitive.int
            val nx = point.getValue("nx").jsonPrimitive.double
            val ny = point.getValue("ny").jsonPrimitive.double
            if (lastFrame < 0) {
                // frame 0 is the subject at/just after guideUser, no extra tick
            } else {
                repeat(frame - lastFrame) { engine.tickFrame() }
            }
            lastFrame = frame
            val (cx, cy) = engine.subjectCenter()
            if (abs(cx - nx) > 1e-6 || abs(cy - ny) > 1e-6) {
                error("trajectory frame $frame expected ($nx, $ny) got ($cx, $cy)")
            }
        }
    }

    private fun maybeAutoOpen(send: JsonObject) {
        val op = send["op"]?.jsonPrimitive?.content
        if (engine.currentSession() == null && op != "openSession") {
            kotlinx.coroutines.runBlocking {
                engine.openSession(DEFAULT_OPEN)
            }
        }
    }

    private fun loadFixture(id: String) {
        val json = parse(readResource("golden/fixtures/$id/fixture.json"))
        val box = json.getValue("subjectBox").jsonObject
        engine.loadFixture(
            subjectBox = BoxNorm(
                box.getValue("x0").jsonPrimitive.double,
                box.getValue("y0").jsonPrimitive.double,
                box.getValue("x1").jsonPrimitive.double,
                box.getValue("y1").jsonPrimitive.double,
            ),
            compositionClass = json.getValue("compositionClass").jsonPrimitive.content,
            targetNx = json.getValue("suggestedTarget").jsonObject.getValue("nx").jsonPrimitive.double,
            targetNy = json.getValue("suggestedTarget").jsonObject.getValue("ny").jsonPrimitive.double,
            suggestedZoom = json["suggestedZoom"]?.jsonPrimitive?.double ?: 1.0,
        )
    }

    private fun assertSubset(label: String, expected: JsonElement, actual: JsonElement) {
        val mismatch = JsonSubset.mismatch(expected, actual)
        if (mismatch != null) {
            error("$label subset mismatch: $mismatch\nactual=$actual")
        }
    }

    private inline fun <reified T> encode(value: T): JsonElement =
        EngineJson.encodeToJsonElement(value)

    companion object {
        val DEFAULT_OPEN = OpenSessionRequest(
            facing = "back",
            profileId = "xiaomi.15s_pro.hyperos2",
            previewMaxFps = 30,
            previewMaxWidth = 1920,
            sessionProfile = "still",
            aiGuide = "off",
        )

        fun readResource(path: String): String {
            val stream = checkNotNull(GoldenRunner::class.java.classLoader)
                .getResourceAsStream(path)
                ?: error("missing resource $path")
            return stream.bufferedReader().use { it.readText() }
        }

        fun parse(text: String): JsonObject = EngineJson.parseToJsonElement(text).jsonObject
    }
}
