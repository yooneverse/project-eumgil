package com.ssafy.e102.eumgil.core.model

import org.json.JSONObject

enum class VoiceAnalyzeMode {
    MOBILITY_IMPAIRED,
    LOW_VISION,
}

enum class VoiceAnalyzeIntent {
    PLACE_SEARCH,
    CATEGORY_SEARCH,
    BOOKMARK_ADD,
    BOOKMARK_DELETE,
    NAVIGATE,
    SHOW_BOOKMARKS,
    SHOW_FAVORITE_ROUTES,
    LOGOUT,
    REPORT,
    NAVIGATION_END,
    OPEN_MY_PAGE,
    OPEN_MAP,
    ASK,
    UNKNOWN,
}

data class VoiceAnalyzeHistoryItem(
    val role: String,
    val content: String,
)

data class VoiceAnalyzeResult(
    val intent: VoiceAnalyzeIntent,
    val placeName: String?,
    val category: String?,
    val bookmarkAction: String?,
    val departure: String?,
    val destination: String?,
    val reportType: String?,
    val description: String?,
    val confirmed: Boolean?,
    val confirmationMessage: String?,
)

fun VoiceAnalyzeResult.toJsonString(): String {
    fun s(v: String?) = if (v != null) JSONObject.quote(v) else "null"
    return "{" +
        "\"intent\":\"$intent\"," +
        "\"placeName\":${s(placeName)}," +
        "\"category\":${s(category)}," +
        "\"bookmarkAction\":${s(bookmarkAction)}," +
        "\"departure\":${s(departure)}," +
        "\"destination\":${s(destination)}," +
        "\"reportType\":${s(reportType)}," +
        "\"description\":${s(description)}," +
        "\"confirmed\":${confirmed?.toString() ?: "null"}," +
        "\"confirmationMessage\":${s(confirmationMessage)}" +
        "}"
}
