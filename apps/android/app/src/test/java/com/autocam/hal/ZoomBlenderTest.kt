package com.autocam.hal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomBlenderTest {
    @Test
    fun hysteresisHoldsTwoTicksBeforeUwToMain() {
        val b = ZoomBlender()
        b.reset(ZoomBlender.LENS_UW)
        val first = b.tick(1.20)
        assertFalse(first.switched)
        assertEquals(ZoomBlender.LENS_UW, first.activeLensId)
        val second = b.tick(1.20)
        assertTrue(second.switched)
        assertEquals(ZoomBlender.LENS_UW, second.fromLensId)
        assertEquals(ZoomBlender.LENS_MAIN, second.activeLensId)
    }

    @Test
    fun downSwitchUsesHysteresisBand() {
        val b = ZoomBlender()
        b.reset(ZoomBlender.LENS_MAIN)
        assertEquals(ZoomBlender.LENS_MAIN, b.desiredLens(0.80))
        assertEquals(ZoomBlender.LENS_UW, b.desiredLens(0.70))
        assertEquals(ZoomBlender.LENS_TELE, b.desiredLens(5.00))
    }

    @Test
    fun uwJumpsToTeleWhenZoomIsTele() {
        val b = ZoomBlender()
        b.reset(ZoomBlender.LENS_UW)
        assertEquals(ZoomBlender.LENS_TELE, b.desiredLens(6.0, ZoomBlender.LENS_UW))
        assertEquals(ZoomBlender.LENS_MAIN, b.desiredLens(1.2, ZoomBlender.LENS_UW))
    }

    @Test
    fun teleJumpsToUwWhenWide() {
        val b = ZoomBlender()
        b.reset(ZoomBlender.LENS_TELE)
        assertEquals(ZoomBlender.LENS_UW, b.desiredLens(0.65, ZoomBlender.LENS_TELE))
    }
}

class SatZoomMapTest {
    @Test
    fun uwAtMinIsSensorOneX() {
        assertEquals(1f, SatZoomMap.requestZoom(SatZoomMap.USER_MIN, ZoomBlender.LENS_UW), 0.02f)
    }

    @Test
    fun mainIsIdentity() {
        assertEquals(1f, SatZoomMap.requestZoom(1.0, ZoomBlender.LENS_MAIN), 0.001f)
        assertEquals(4.8f, SatZoomMap.requestZoom(4.8, ZoomBlender.LENS_MAIN), 0.001f)
    }

    @Test
    fun teleOpticalIsAboutFiveX() {
        assertEquals(1f, SatZoomMap.requestZoom(SatZoomMap.TELE_OPTICAL, ZoomBlender.LENS_TELE), 0.02f)
        val at10 = SatZoomMap.requestZoom(10.0, ZoomBlender.LENS_TELE)
        assertTrue(at10 in 1.8f..2.1f)
    }

    @Test
    fun idsRoundTrip() {
        assertEquals("2", SatZoomMap.cameraId(ZoomBlender.LENS_UW))
        assertEquals("0", SatZoomMap.cameraId(ZoomBlender.LENS_MAIN))
        assertEquals("3", SatZoomMap.cameraId(ZoomBlender.LENS_TELE))
    }
}
