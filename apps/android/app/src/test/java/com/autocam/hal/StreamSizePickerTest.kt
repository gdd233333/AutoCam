package com.autocam.hal

import android.util.Size
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamSizePickerTest {
    @Test
    fun closestPicks1080p() {
        val sizes = arrayOf(Size(1280, 720), Size(1920, 1080), Size(3840, 2160))
        val picked = StreamSizePicker.closest(sizes, 1920, 1080)
        assertEquals(1920, picked!!.width)
        assertEquals(1080, picked.height)
    }

    @Test
    fun nearestPixelsPrefers12mpFourByThree() {
        val sizes = arrayOf(
            Size(8000, 6000),
            Size(4096, 3072),
            Size(1920, 1080),
        )
        val picked = StreamSizePicker.nearestPixels(sizes, 12_500_000, 4.0 / 3.0)
        assertEquals(4096, picked!!.width)
        assertEquals(3072, picked.height)
    }
}
