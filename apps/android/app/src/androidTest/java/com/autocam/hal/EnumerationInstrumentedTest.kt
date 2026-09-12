package com.autocam.hal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnumerationInstrumentedTest {
    @Test
    fun dumpHalOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = HalDumper(context).dumpToFile()
        assertTrue("hal_dump.json must exist", file.exists())
        assertTrue("dump should not be empty", file.length() > 64)
        val dump = HalDumper.HalJson.decodeFromString(HalDump.serializer(), file.readText())
        assertTrue("must enumerate cameras", dump.cameras.isNotEmpty())
        val back = dump.cameras.filter { it.facing == "back" }
        assertTrue("must see at least one back camera", back.isNotEmpty())
        assertTrue(
            "device should match 15S Pro / dijun",
            dump.build.device.contains("dijun", ignoreCase = true) ||
                dump.build.model.contains("25042PN24C") ||
                dump.build.model.contains("15S", ignoreCase = true),
        )
        assertTrue("must probe session combos", dump.sessionCombos.isNotEmpty())
        assertTrue(
            "Profile A must be supported on 15S Pro Camera2",
            dump.sessionCombos.any { it.profile == "A_STILL" && it.supported == true },
        )
        assertFalse(
            "Xiaomi Camera Engine SDK is not a public third-party AAR",
            dump.xiaomiCameraEngine.sdkOnClasspath,
        )
    }
}
