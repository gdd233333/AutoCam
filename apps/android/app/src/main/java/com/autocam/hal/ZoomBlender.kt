package com.autocam.hal

import com.autocam.engine.ZoomBlendBreakpoint
import com.autocam.engine.ZoomBlendProfile

/**
 * UW / Main / Tele state machine. No crossfade state: hold [ZoomBlendProfile.hysteresisFrames]
 * then freeze+fade. Breakpoints default to the 15S Pro dump anchors (14/23/120 mm).
 */
class ZoomBlender(
    var profile: ZoomBlendProfile = DEFAULT,
) {
    var activeLensId: String = LENS_MAIN
        private set
    private var pendingLens: String? = null
    private var pendingCount: Int = 0
    var fadeRemaining: Int = 0
        private set

    data class Tick(
        val activeLensId: String,
        val switched: Boolean,
        val fromLensId: String?,
        val fading: Boolean,
    )

    fun reset(lensId: String = LENS_MAIN) {
        activeLensId = lensId
        pendingLens = null
        pendingCount = 0
        fadeRemaining = 0
    }

    fun force(lensId: String) {
        activeLensId = lensId
        pendingLens = null
        pendingCount = 0
        fadeRemaining = profile.fadeFrames
    }

    fun desiredLens(zoom: Double, current: String = activeLensId): String {
        val uwMain = breakpoint(LENS_UW, LENS_MAIN)
        val mainTele = breakpoint(LENS_MAIN, LENS_TELE)
        val uwToMain = uwMain?.upSwitch ?: 0.95
        val mainToUw = uwMain?.downSwitch ?: 0.72
        val mainToTele = mainTele?.upSwitch ?: 4.80
        val teleToMain = mainTele?.downSwitch ?: 3.60
        return when (current) {
            LENS_UW -> if (zoom >= uwToMain) LENS_MAIN else LENS_UW
            LENS_TELE -> if (zoom <= teleToMain) LENS_MAIN else LENS_TELE
            else -> when {
                zoom <= mainToUw -> LENS_UW
                zoom >= mainToTele -> LENS_TELE
                else -> LENS_MAIN
            }
        }
    }

    fun tick(zoom: Double): Tick {
        val desired = desiredLens(zoom, activeLensId)
        var switched = false
        var from: String? = null
        if (desired != activeLensId) {
            if (pendingLens == desired) {
                pendingCount += 1
            } else {
                pendingLens = desired
                pendingCount = 1
            }
            if (pendingCount >= profile.hysteresisFrames.coerceAtLeast(1)) {
                from = activeLensId
                activeLensId = desired
                pendingLens = null
                pendingCount = 0
                fadeRemaining = profile.fadeFrames.coerceAtLeast(0)
                switched = true
            }
        } else {
            pendingLens = null
            pendingCount = 0
        }
        val fading = fadeRemaining > 0
        if (fadeRemaining > 0) fadeRemaining -= 1
        return Tick(activeLensId, switched, from, fading)
    }

    private fun breakpoint(from: String, to: String): ZoomBlendBreakpoint? {
        return profile.breakpoints.firstOrNull { it.fromLensId == from && it.toLensId == to }
    }

    companion object {
        const val LENS_UW: String = "physical_uw"
        const val LENS_MAIN: String = "physical_main"
        const val LENS_TELE: String = "physical_tele"

        val DEFAULT: ZoomBlendProfile = ZoomBlendProfile(
            hysteresisFrames = 2,
            fadeFrames = 2,
            algorithm = "freeze_fade",
            breakpoints = listOf(
                ZoomBlendBreakpoint(LENS_UW, LENS_MAIN, 0.95, 0.72, 0.02),
                ZoomBlendBreakpoint(LENS_MAIN, LENS_TELE, 4.80, 3.60, 0.03),
            ),
            gainMatch = mapOf(LENS_UW to 1.0, LENS_MAIN to 1.0, LENS_TELE to 1.08),
            cctOffsetK = mapOf(LENS_UW to 0, LENS_MAIN to 0, LENS_TELE to -150),
        )
    }
}

/**
 * User zoom is relative to main 23 mm = 1.0x. Each physical camera's
 * [android.hardware.camera2.CaptureRequest.CONTROL_ZOOM_RATIO] starts at 1.0
 * on that sensor.
 */
object SatZoomMap {
    const val UW_OPTICAL: Double = 14.0 / 23.0
    const val TELE_OPTICAL: Double = 120.0 / 23.0
    const val USER_MIN: Double = 0.61
    const val USER_MAX: Double = 10.0

    fun cameraId(lensId: String): String {
        return when (lensId) {
            ZoomBlender.LENS_UW -> "2"
            ZoomBlender.LENS_TELE -> "3"
            else -> "0"
        }
    }

    fun lensId(cameraId: String): String {
        return when (cameraId) {
            "2" -> ZoomBlender.LENS_UW
            "3" -> ZoomBlender.LENS_TELE
            else -> ZoomBlender.LENS_MAIN
        }
    }

    fun requestZoom(userZoom: Double, lensId: String): Float {
        val optical = when (lensId) {
            ZoomBlender.LENS_UW -> UW_OPTICAL
            ZoomBlender.LENS_TELE -> TELE_OPTICAL
            else -> 1.0
        }
        return (userZoom / optical).toFloat().coerceAtLeast(1f)
    }
}
