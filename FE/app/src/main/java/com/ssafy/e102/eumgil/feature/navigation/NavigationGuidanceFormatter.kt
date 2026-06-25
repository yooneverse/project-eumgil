package com.ssafy.e102.eumgil.feature.navigation

import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteSegment
import java.util.Locale

internal data class NavigationBriefingItem(
    val sequence: Int,
    val instruction: String,
    val guidanceAction: NavigationGuidanceAction,
)

internal fun RouteCandidate.toNavigationBriefingItems(): List<NavigationBriefingItem> =
    segments.mapIndexed { index, segment ->
        NavigationBriefingItem(
            sequence = index + 1,
            instruction = segment.toCompactNavigationInstruction(),
            guidanceAction = toNavigationGuidanceAction(segment),
        )
    }

internal fun RouteSegment.toCompactNavigationInstruction(): String {
    val action = guidanceMessage.toCompactNavigationAction()
    if (action == ARRIVE_LABEL) return action

    val distance = distanceMeters.toCompactNavigationDistance()
    return if (distance.isBlank()) {
        action
    } else {
        "$distance $AFTER_LABEL $action"
    }
}

private fun String.toCompactNavigationAction(): String {
    val message = trim().lowercase()
    return when {
        message.contains("start the route") -> START_LABEL
        message.contains("next route segment") -> NEXT_SEGMENT_LABEL
        message.contains("destination") -> DESTINATION_DIRECTION_LABEL
        message.contains(ARRIVE_KEYWORD) -> ARRIVE_LABEL
        message.contains(RIGHT_TURN_KEYWORD) ||
            message.contains(RIGHT_SIDE_KEYWORD) ||
            message.contains("right") -> RIGHT_TURN_LABEL
        message.contains(LEFT_TURN_KEYWORD) ||
            message.contains(LEFT_SIDE_KEYWORD) ||
            message.contains("left") -> LEFT_TURN_LABEL
        message.contains(STRAIGHT_KEYWORD) ||
            message.contains("straight") ||
            message.contains("continue") -> STRAIGHT_LABEL
        message.contains(CROSSWALK_KEYWORD) ||
            message.contains(CROSS_KEYWORD) ||
            message.contains("cross") -> CROSS_LABEL
        message.contains(END_GUIDANCE_KEYWORD) -> END_GUIDANCE_LABEL
        else -> ROUTE_MOVE_LABEL
    }
}

private fun Int.toCompactNavigationDistance(): String =
    when {
        this <= 0 -> ""
        this < 1_000 -> "${this}m"
        this % 1_000 == 0 -> "${this / 1_000}km"
        else -> String.format(Locale.US, "%.1fkm", this / 1_000.0)
    }

private const val AFTER_LABEL = "\uD6C4"
private const val ARRIVE_LABEL = "\uB3C4\uCC29"
private const val START_LABEL = "\uCD9C\uBC1C"
private const val NEXT_SEGMENT_LABEL = "\uB2E4\uC74C \uAD6C\uAC04"
private const val DESTINATION_DIRECTION_LABEL = "\uB3C4\uCC29\uC9C0 \uBC29\uD5A5"
private const val RIGHT_TURN_LABEL = "\uC6B0\uD68C\uC804"
private const val LEFT_TURN_LABEL = "\uC88C\uD68C\uC804"
private const val STRAIGHT_LABEL = "\uC9C1\uC9C4"
private const val CROSS_LABEL = "\uD6A1\uB2E8"
private const val END_GUIDANCE_LABEL = "\uC548\uB0B4 \uC885\uB8CC"
private const val ROUTE_MOVE_LABEL = "\uACBD\uB85C \uC774\uB3D9"

private const val ARRIVE_KEYWORD = "\uB3C4\uCC29"
private const val RIGHT_TURN_KEYWORD = "\uC6B0\uD68C\uC804"
private const val RIGHT_SIDE_KEYWORD = "\uC624\uB978\uCABD"
private const val LEFT_TURN_KEYWORD = "\uC88C\uD68C\uC804"
private const val LEFT_SIDE_KEYWORD = "\uC67C\uCABD"
private const val STRAIGHT_KEYWORD = "\uC9C1\uC9C4"
private const val CROSSWALK_KEYWORD = "\uD6A1\uB2E8\uBCF4\uB3C4"
private const val CROSS_KEYWORD = "\uAC74\uB108"
private const val END_GUIDANCE_KEYWORD = "\uC548\uB0B4 \uC885\uB8CC"
