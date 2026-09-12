package com.autocam.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {
    @Test
    fun debugDefaults() {
        val flags = FeatureFlags(MemoryFlagStore(), debugBuild = true)
        val snap = flags.readSnapshot()
        assertTrue(snap.engineMock)
        assertFalse(snap.halMultiLens)
        assertTrue(snap.halZoomRatio)
        assertFalse(snap.zoomBlend)
        assertFalse("zoom.dual_physical must default false", snap.zoomDualPhysical)
        assertEquals("off", snap.aiGuide)
        assertFalse(snap.aiRefineStill)
        assertFalse(snap.gradeLut)
        assertFalse(snap.captureDng)
        assertFalse(snap.captureFullRes)
        assertFalse(snap.debugIncludeHalDump)
        assertFalse(snap.debugFlagSecure)
        assertEquals("auto", snap.debugCameraId)
    }

    @Test
    fun releaseDefaults() {
        val flags = FeatureFlags(MemoryFlagStore(), debugBuild = false)
        assertFalse(flags.engineMock())
        assertTrue(flags.debugFlagSecure())
        assertEquals(AiGuide.OFF, flags.aiGuide())
        assertFalse(flags.zoomDualPhysical())
    }

    @Test
    fun aiGuideIsEnumNotBoolean() {
        val flags = FeatureFlags(MemoryFlagStore(), debugBuild = true)
        assertEquals(AiGuide.OFF, flags.aiGuide())
        flags.setAiGuide(AiGuide.RULE)
        assertEquals(AiGuide.RULE, flags.aiGuide())
        assertEquals("rule", flags.readSnapshot().aiGuide)
        flags.setAiGuide(AiGuide.NEURAL)
        assertEquals("neural", flags.aiGuide().wire)
        flags.setAiGuide(AiGuide.fromWire("nope"))
        assertEquals(AiGuide.OFF, flags.aiGuide())
        assertEquals(AiGuide.RULE, AiGuide.OFF.next())
        assertEquals(AiGuide.NEURAL, AiGuide.RULE.next())
        assertEquals(AiGuide.OFF, AiGuide.NEURAL.next())
    }

    @Test
    fun keysUseFlagPrefix() {
        assertTrue(FeatureFlags.Keys.AI_GUIDE.startsWith("flag."))
        assertEquals("flag.ai.guide", FeatureFlags.Keys.AI_GUIDE)
        assertEquals("flag.zoom.dual_physical", FeatureFlags.Keys.ZOOM_DUAL_PHYSICAL)
        assertEquals("flag.engine.mock", FeatureFlags.Keys.ENGINE_MOCK)
    }
}
