package com.autocam.flags

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * SharedPreferences keys use prefix `flag.`. Defaults match docs/dev/architecture.md.
 */
class FeatureFlags(
    private val store: FlagStore,
    private val debugBuild: Boolean,
) {
    private val _snapshot = MutableStateFlow(readSnapshot())
    val snapshot: StateFlow<FlagSnapshot> = _snapshot.asStateFlow()

    fun engineMock(): Boolean = bool(Keys.ENGINE_MOCK, defaultEngineMock())
    fun setEngineMock(value: Boolean) = setBool(Keys.ENGINE_MOCK, value)

    fun halMultiLens(): Boolean = bool(Keys.HAL_MULTI_LENS, false)
    fun setHalMultiLens(value: Boolean) = setBool(Keys.HAL_MULTI_LENS, value)

    fun halZoomRatio(): Boolean = bool(Keys.HAL_ZOOM_RATIO, true)
    fun setHalZoomRatio(value: Boolean) = setBool(Keys.HAL_ZOOM_RATIO, value)

    fun zoomBlend(): Boolean = bool(Keys.ZOOM_BLEND, false)
    fun setZoomBlend(value: Boolean) = setBool(Keys.ZOOM_BLEND, value)

    fun zoomDualPhysical(): Boolean = bool(Keys.ZOOM_DUAL_PHYSICAL, false)
    fun setZoomDualPhysical(value: Boolean) = setBool(Keys.ZOOM_DUAL_PHYSICAL, value)

    fun aiGuide(): AiGuide = AiGuide.fromWire(store.getString(Keys.AI_GUIDE, AiGuide.OFF.wire))
    fun setAiGuide(value: AiGuide) {
        store.putString(Keys.AI_GUIDE, value.wire)
        publish()
    }

    fun aiRefineStill(): Boolean = bool(Keys.AI_REFINE_STILL, false)
    fun setAiRefineStill(value: Boolean) = setBool(Keys.AI_REFINE_STILL, value)

    fun gradeLut(): Boolean = bool(Keys.GRADE_LUT, false)
    fun setGradeLut(value: Boolean) = setBool(Keys.GRADE_LUT, value)

    fun captureDng(): Boolean = bool(Keys.CAPTURE_DNG, false)
    fun setCaptureDng(value: Boolean) = setBool(Keys.CAPTURE_DNG, value)

    fun captureFullRes(): Boolean = bool(Keys.CAPTURE_FULL_RES, false)
    fun setCaptureFullRes(value: Boolean) = setBool(Keys.CAPTURE_FULL_RES, value)

    fun debugIncludeHalDump(): Boolean = bool(Keys.DEBUG_INCLUDE_HAL_DUMP, false)
    fun setDebugIncludeHalDump(value: Boolean) = setBool(Keys.DEBUG_INCLUDE_HAL_DUMP, value)

    fun debugFlagSecure(): Boolean = bool(Keys.DEBUG_FLAG_SECURE, defaultFlagSecure())
    fun setDebugFlagSecure(value: Boolean) = setBool(Keys.DEBUG_FLAG_SECURE, value)

    fun readSnapshot(): FlagSnapshot {
        return FlagSnapshot(
            engineMock = engineMock(),
            halMultiLens = halMultiLens(),
            halZoomRatio = halZoomRatio(),
            zoomBlend = zoomBlend(),
            zoomDualPhysical = zoomDualPhysical(),
            aiGuide = aiGuide().wire,
            aiRefineStill = aiRefineStill(),
            gradeLut = gradeLut(),
            captureDng = captureDng(),
            captureFullRes = captureFullRes(),
            debugIncludeHalDump = debugIncludeHalDump(),
            debugFlagSecure = debugFlagSecure(),
        )
    }

    private fun defaultEngineMock(): Boolean = debugBuild

    private fun defaultFlagSecure(): Boolean = !debugBuild

    private fun bool(key: String, default: Boolean): Boolean = store.getBoolean(key, default)

    private fun setBool(key: String, value: Boolean) {
        store.putBoolean(key, value)
        publish()
    }

    private fun publish() {
        _snapshot.value = readSnapshot()
    }

    object Keys {
        const val PREFIX = "flag."
        const val ENGINE_MOCK = "flag.engine.mock"
        const val HAL_MULTI_LENS = "flag.hal.multi_lens"
        const val HAL_ZOOM_RATIO = "flag.hal.zoom_ratio"
        const val ZOOM_BLEND = "flag.zoom.blend"
        const val ZOOM_DUAL_PHYSICAL = "flag.zoom.dual_physical"
        const val AI_GUIDE = "flag.ai.guide"
        const val AI_REFINE_STILL = "flag.ai.refine_still"
        const val GRADE_LUT = "flag.grade.lut"
        const val CAPTURE_DNG = "flag.capture.dng"
        const val CAPTURE_FULL_RES = "flag.capture.full_res"
        const val DEBUG_INCLUDE_HAL_DUMP = "flag.debug.include_hal_dump"
        const val DEBUG_FLAG_SECURE = "flag.debug.flag_secure"
    }
}

@Serializable
data class FlagSnapshot(
    val engineMock: Boolean,
    val halMultiLens: Boolean,
    val halZoomRatio: Boolean,
    val zoomBlend: Boolean,
    val zoomDualPhysical: Boolean,
    val aiGuide: String,
    val aiRefineStill: Boolean,
    val gradeLut: Boolean,
    val captureDng: Boolean,
    val captureFullRes: Boolean,
    val debugIncludeHalDump: Boolean,
    val debugFlagSecure: Boolean,
)
