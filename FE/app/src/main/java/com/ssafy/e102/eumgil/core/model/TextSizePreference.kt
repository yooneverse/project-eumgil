package com.ssafy.e102.eumgil.core.model

enum class TextSizePreference(
    val scale: Float,
) {
    DEFAULT(scale = 1.00f),
    LARGE(scale = 1.15f),
    EXTRA_LARGE(scale = 1.30f),
    ;

    companion object {
        fun fromStoredValue(value: String?): TextSizePreference =
            entries.firstOrNull { preference -> preference.name == value } ?: DEFAULT
    }
}
