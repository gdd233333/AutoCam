package com.autocam.golden

import com.autocam.engine.JsonSubset
import com.autocam.engine.SetZoom
import com.autocam.mock.MockCameraEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractGoldenTest {
    @Test
    fun setZoomSequence() {
        GoldenRunner().playResource("golden/sequences/set_zoom.json")
    }

    @Test
    fun guideUserPanZoomSequence() {
        GoldenRunner().playResource("golden/sequences/guide_user_pan_zoom.json")
    }

    @Test
    fun captureApplyCropSequence() {
        GoldenRunner().playResource("golden/sequences/capture_apply_crop.json")
    }

    @Test
    fun workedExampleUwToMain() {
        GoldenRunner().playResource("golden/sequences/worked_example_uw_to_main.json")
    }

    @Test
    fun commandBusSetZoomMatchesGoldenSend() = runBlocking {
        val runner = GoldenRunner()
        val send = GoldenRunner.parse(
            GoldenRunner.readResource("golden/sequences/set_zoom.json"),
        ).getValue("send")
        runner.engine.openSession(GoldenRunner.DEFAULT_OPEN)
        runner.bus.setZoom(
            SetZoom(zoomRatio = 2.0, rate = "immediate", source = "user_slider"),
            id = "1",
        )
        val recorded = runner.bus.recorded().last()
        val mismatch = JsonSubset.mismatch(send, recorded)
        assertEquals("CommandBus envelope must subset-equal set_zoom.json send: $mismatch", null, mismatch)
    }

    @Test
    fun engineMockFlagIsOn() {
        assertTrue(MockCameraEngine.ENGINE_MOCK)
    }
}

class FixtureGoldenTest {
    private val fixtures = listOf(
        "still_object_table",
        "still_food",
        "still_building",
        "still_person",
    )

    @Test
    fun everyStillFixtureCapturesNonUnitCrop() {
        for (id in fixtures) {
            val runner = GoldenRunner()
            runner.play(
                GoldenRunner.parse(
                    """
                    {
                      "name": "fixture_$id",
                      "fixture": "$id",
                      "fakeClock": true,
                      "steps": [
                        {
                          "send": {
                            "op": "openSession",
                            "id": "1",
                            "body": {
                              "facing": "back",
                              "sessionProfile": "still",
                              "aiGuide": "rule"
                            }
                          }
                        },
                        {
                          "send": { "op": "captureStill", "id": "2" },
                          "expectStill": {
                            "width": 4096,
                            "height": 3072,
                            "appliedLutId": null
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            )
            val still = kotlinx.coroutines.runBlocking { runner.engine.captureStill() }
            val unit = still.cropBoxNorm.x0 == 0.0 &&
                still.cropBoxNorm.y0 == 0.0 &&
                still.cropBoxNorm.x1 == 1.0 &&
                still.cropBoxNorm.y1 == 1.0
            assertNotEquals("$id crop must not be the unit box", true, unit)
            assertEquals(4096, still.width)
        }
    }
}

class JsonSubsetTest {
    @Test
    fun extraKeysAllowed() {
        val expected = GoldenRunner.parse("""{"zoomRatio": 2.0}""")
        val actual = GoldenRunner.parse("""{"zoomRatio": 2.0, "activeLensId": "physical_main"}""")
        assertEquals(null, JsonSubset.mismatch(expected, actual))
    }

    @Test
    fun missingKeyFails() {
        val expected = GoldenRunner.parse("""{"zoomRatio": 2.0}""")
        val actual = GoldenRunner.parse("""{"activeLensId": "physical_main"}""")
        assertTrue(JsonSubset.mismatch(expected, actual)!!.contains("zoomRatio"))
    }

    @Test
    fun nullMatchesNull() {
        val expected = kotlinx.serialization.json.JsonObject(
            mapOf("appliedLutId" to kotlinx.serialization.json.JsonNull),
        )
        val actual = kotlinx.serialization.json.JsonObject(
            mapOf("appliedLutId" to kotlinx.serialization.json.JsonNull, "width" to kotlinx.serialization.json.JsonPrimitive(1)),
        )
        assertEquals(null, JsonSubset.mismatch(expected, actual))
    }
}
