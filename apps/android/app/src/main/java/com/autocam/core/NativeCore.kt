package com.autocam.core

/**
 * JNI facade over `libautocam`. Do not load in JVM unit tests.
 */
object NativeCore {
    private val loaded: Boolean by lazy {
        try {
            System.loadLibrary("autocam")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    @JvmStatic
    external fun add(a: Int, b: Int): Int

    fun tryAdd(a: Int, b: Int): Int? {
        if (!loaded) return null
        return add(a, b)
    }
}
