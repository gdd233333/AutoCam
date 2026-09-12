package com.autocam.hal

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomRangeTest {
    @Test
    fun clampKeepsOneX() {
        assertEquals(1f, ZoomRange.clampForRequest(1f, 1f, 10f))
    }

    @Test
    fun clampRejectsVendorSat120() {
        assertEquals(10f, ZoomRange.clampForRequest(120f, 1f, 10f))
        assertEquals(10f, ZoomRange.clampForRequest(100f, 1f, 10f))
    }

    @Test
    fun clampRejectsBelowMin() {
        assertEquals(1f, ZoomRange.clampForRequest(0.6f, 1f, 10f))
    }
}
