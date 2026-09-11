package com.autocam.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeCoreJvmTest {
    @Test
    fun jvmDoesNotLoadLibautocam() {
        assertNull(NativeCore.tryAdd(2, 3))
        assertEquals(null, NativeCore.tryAdd(2, 3))
    }
}
