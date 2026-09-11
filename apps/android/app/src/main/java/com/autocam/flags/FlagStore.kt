package com.autocam.flags

import android.content.SharedPreferences

interface FlagStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}

class MemoryFlagStore : FlagStore {
    private val bools = mutableMapOf<String, Boolean>()
    private val strings = mutableMapOf<String, String>()

    override fun getBoolean(key: String, default: Boolean): Boolean = bools[key] ?: default

    override fun putBoolean(key: String, value: Boolean) {
        bools[key] = value
    }

    override fun getString(key: String, default: String): String = strings[key] ?: default

    override fun putString(key: String, value: String) {
        strings[key] = value
    }
}

class PrefsFlagStore(private val prefs: SharedPreferences) : FlagStore {
    override fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
