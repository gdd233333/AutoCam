package com.autocam.flags

/**
 * Wire value for `flag.ai.guide`. Enum from day one; never a boolean.
 */
enum class AiGuide(val wire: String) {
    OFF("off"),
    RULE("rule"),
    NEURAL("neural"),
    ;

    fun next(): AiGuide {
        return when (this) {
            OFF -> RULE
            RULE -> NEURAL
            NEURAL -> OFF
        }
    }

    companion object {
        fun fromWire(raw: String?): AiGuide {
            return entries.firstOrNull { it.wire == raw } ?: OFF
        }
    }
}
