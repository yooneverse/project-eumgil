package com.ssafy.e102.eumgil.feature.search

import androidx.annotation.StringRes
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.model.SearchResult

internal data class SearchResultAccessibilityTagUiState(
    @StringRes val labelResIds: List<Int>,
) {
    val hasLabels: Boolean
        get() = labelResIds.isNotEmpty()
}

private data class SearchResultAccessibilityTagSpec(
    @StringRes val labelResId: Int,
    val priority: Int,
)

@StringRes
internal fun resolveSearchResultStateDescriptionRes(
    result: SearchResult,
    @StringRes selectableResId: Int = R.string.search_screen_result_selectable,
): Int =
    if (result.latitude.isFinite() &&
        result.longitude.isFinite() &&
        result.latitude in -90.0..90.0 &&
        result.longitude in -180.0..180.0
    ) {
        selectableResId
    } else {
        R.string.search_screen_result_action_limited
    }

internal fun resolveSearchResultAccessibilityTagUiState(
    rawKeys: List<String>,
): SearchResultAccessibilityTagUiState {
    val resolvedLabels =
        rawKeys
            .mapNotNull(::resolveSearchResultAccessibilityTagSpec)
            .distinctBy { spec -> spec.labelResId }
            .sortedBy { spec -> spec.priority }

    return SearchResultAccessibilityTagUiState(
        labelResIds = resolvedLabels.map { spec -> spec.labelResId },
    )
}

private fun resolveSearchResultAccessibilityTagSpec(rawKey: String): SearchResultAccessibilityTagSpec? =
    when (rawKey.trim().lowercase()) {
        "auto-door",
        "wide-entry",
        "wheelchair-turning-space",
        "table-spacing",
        -> SearchResultAccessibilityTagSpec(
            labelResId = R.string.place_accessibility_label_entry_available,
            priority = 0,
        )

        "step-free-entrance" ->
            SearchResultAccessibilityTagSpec(
                labelResId = R.string.place_accessibility_label_step_free,
                priority = 1,
            )

        "ramp" ->
            SearchResultAccessibilityTagSpec(
                labelResId = R.string.place_accessibility_label_ramp,
                priority = 2,
            )

        "elevator" ->
            SearchResultAccessibilityTagSpec(
                labelResId = R.string.place_accessibility_label_elevator,
                priority = 3,
            )

        "accessible-parking" ->
            SearchResultAccessibilityTagSpec(
                labelResId = R.string.place_accessibility_label_accessible_parking,
                priority = 4,
            )

        "accessible-toilet" ->
            SearchResultAccessibilityTagSpec(
                labelResId = R.string.place_accessibility_label_accessible_toilet,
                priority = 5,
            )

        "guidance-facility",
        "rest-area",
        "braille-block",
        "crosswalk",
        "low-height-button",
        -> SearchResultAccessibilityTagSpec(
            labelResId = R.string.place_accessibility_label_guidance_facility,
            priority = 6,
        )

        "accessible-room" ->
            SearchResultAccessibilityTagSpec(
                labelResId = R.string.place_accessibility_label_accessible_room,
                priority = 7,
            )

        else -> null
    }
