package com.autocam.engine

import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

object JsonSubset {
    fun matches(expected: JsonElement, actual: JsonElement): Boolean {
        return when (expected) {
            is JsonNull -> actual is JsonNull
            is JsonPrimitive -> actual is JsonPrimitive && primitivesEqual(expected, actual)
            is JsonObject ->
                actual is JsonObject &&
                    expected.entries.all { (key, value) ->
                        actual.containsKey(key) && matches(value, actual.getValue(key))
                    }
            is JsonArray ->
                actual is JsonArray &&
                    expected.size <= actual.size &&
                    expected.indices.all { matches(expected[it], actual[it]) }
        }
    }

    fun mismatch(expected: JsonElement, actual: JsonElement, path: String = "$"): String? {
        return when (expected) {
            is JsonNull -> if (actual is JsonNull) null else "$path: expected null"
            is JsonPrimitive ->
                if (actual is JsonPrimitive && primitivesEqual(expected, actual)) {
                    null
                } else {
                    "$path: expected $expected got $actual"
                }
            is JsonObject -> {
                if (actual !is JsonObject) return "$path: expected object"
                expected.entries.firstNotNullOfOrNull { (key, value) ->
                    if (!actual.containsKey(key)) {
                        "$path.$key: missing"
                    } else {
                        mismatch(value, actual.getValue(key), "$path.$key")
                    }
                }
            }
            is JsonArray -> {
                if (actual !is JsonArray) return "$path: expected array"
                if (expected.size > actual.size) {
                    return "$path: expected array size >= ${expected.size}, got ${actual.size}"
                }
                expected.indices.firstNotNullOfOrNull { i ->
                    mismatch(expected[i], actual[i], "$path[$i]")
                }
            }
        }
    }

    private fun primitivesEqual(expected: JsonPrimitive, actual: JsonPrimitive): Boolean {
        val e = expected.doubleOrNull
        val a = actual.doubleOrNull
        if (e != null && a != null) return abs(e - a) < 1e-6
        return expected.content == actual.content
    }
}
