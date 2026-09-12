package com.autocam.infer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackendProbeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTripAndShaGate() {
        val probe = BackendProbe(tmp.root)
        assertNull(probe.load())
        probe.save(BackendProbeResult("abc", "cpu_int8", warmupMs = 12, lastGood = true))
        val loaded = probe.load()!!
        assertEquals("cpu_int8", loaded.backend)
        assertEquals(loaded, probe.lastGoodIfShaMatches("abc"))
        assertNull(probe.lastGoodIfShaMatches("nope"))
    }
}
