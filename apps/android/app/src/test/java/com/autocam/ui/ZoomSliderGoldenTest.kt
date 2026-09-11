package com.autocam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.autocam.engine.CommandBus
import com.autocam.engine.JsonSubset
import com.autocam.engine.OpenSessionRequest
import com.autocam.golden.GoldenRunner
import com.autocam.mock.MockCameraEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoomSliderGoldenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dragZoomSliderMatchesSetZoomGoldenSend() {
        val engine = MockCameraEngine()
        val bus = CommandBus(engine)
        runBlocking {
            engine.openSession(
                OpenSessionRequest(
                    facing = "back",
                    profileId = "xiaomi.15s_pro.hyperos2",
                    previewMaxFps = 30,
                    previewMaxWidth = 1920,
                    sessionProfile = "still",
                    aiGuide = "off",
                ),
            )
        }

        composeRule.setContent {
            MaterialTheme {
                ViewfinderScreen(
                    engine = engine,
                    bus = bus,
                    mock = true,
                    onCaptured = {},
                    onOpenCalibration = {},
                    onOpenDebug = {},
                )
            }
        }

        composeRule.onNodeWithTag(ViewfinderTags.ZOOM_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { setter ->
                setter(2.0f)
            }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            bus.recorded().any { it["op"]?.toString()?.contains("setZoom") == true }
        }

        val send = GoldenRunner.parse(
            GoldenRunner.readResource("golden/sequences/set_zoom.json"),
        ).getValue("send")
        val recorded = bus.recorded().lastOrNull { env ->
            env["op"].toString().contains("setZoom")
        }
        assertNotNull("slider must record a setZoom envelope", recorded)
        val mismatch = JsonSubset.mismatch(send, recorded!!)
        assertEquals(
            "slider CommandBus envelope must subset-equal set_zoom.json send: $mismatch actual=$recorded",
            null,
            mismatch,
        )
    }
}
