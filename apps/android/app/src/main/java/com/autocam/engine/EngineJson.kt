package com.autocam.engine

import kotlinx.serialization.json.Json

val EngineJson: Json = Json {
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = true
    prettyPrint = false
}
