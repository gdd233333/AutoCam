package com.autocam.hal

import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureParamMapperTest {
    @Test
    fun aeOffAndOn() {
        assertEquals(CaptureRequest.CONTROL_AE_MODE_OFF, CaptureParamMapper.aeMode("off"))
        assertEquals(CaptureRequest.CONTROL_AE_MODE_ON, CaptureParamMapper.aeMode("on"))
        assertEquals(
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH,
            CaptureParamMapper.aeMode("on_auto_flash"),
        )
    }

    @Test
    fun afContinuousDefault() {
        assertEquals(
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            CaptureParamMapper.afMode("continuous_picture"),
        )
        assertEquals(CaptureRequest.CONTROL_AF_MODE_OFF, CaptureParamMapper.afMode("off"))
    }

    @Test
    fun oisAndEis() {
        assertEquals(
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON,
            CaptureParamMapper.oisMode("ois"),
        )
        assertEquals(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON,
            CaptureParamMapper.eisMode("eis"),
        )
        assertEquals(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            CaptureParamMapper.eisMode("off"),
        )
    }
}
