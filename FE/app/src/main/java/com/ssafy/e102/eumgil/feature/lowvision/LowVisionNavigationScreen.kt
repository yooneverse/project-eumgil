package com.ssafy.e102.eumgil.feature.lowvision

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.feature.lowvision.component.LowVisionBottomNav
import com.ssafy.e102.eumgil.feature.navigation.NavigationGuidanceAction
import com.ssafy.e102.eumgil.feature.navigation.NavigationScreenState
import com.ssafy.e102.eumgil.feature.navigation.NavigationUiAction
import com.ssafy.e102.eumgil.feature.navigation.NavigationUiState
import com.ssafy.e102.eumgil.feature.navigation.iconRes

private val LowVisionNavigationBackground = Color(0xFF0D0D0F)
private val LowVisionNavigationPanel = Color(0xFF202123)
private val LowVisionNavigationYellow = LowVisionScreenDefaults.brandYellow
private val LowVisionNavigationCoral = Color(0xFFFF8B78)
private val LowVisionNavigationInactive = Color(0xFFE7E7E7)
private val LowVisionNavigationDivider = Color(0xFF36363A)

private const val LOW_VISION_NAVIGATION_DISTANCE_LABEL = "\uB0A8\uC740 \uAC70\uB9AC"
private const val LOW_VISION_NAVIGATION_TIME_LABEL = "\uB0A8\uC740 \uC2DC\uAC04"
private const val LOW_VISION_NAVIGATION_DISTANCE_PENDING = "\uB0A8\uC740 \uAC70\uB9AC \uD655\uC778 \uC911"
private const val LOW_VISION_NAVIGATION_TIME_PENDING = "\uB0A8\uC740 \uC2DC\uAC04 \uD655\uC778 \uC911"
private const val LOW_VISION_NAVIGATION_SEGMENT_PREFIX = "\uC774\uBC88 \uAD6C\uAC04"
private const val LOW_VISION_NAVIGATION_SEGMENT_PENDING_SHORT = "\uD655\uC778 \uC911"
private const val LOW_VISION_NAVIGATION_APPROXIMATE_SUFFIX = "\uC815\uB3C4"
private const val LOW_VISION_NAVIGATION_PREPARING_ACTION_TALKBACK = "\uC548\uB0B4\uB97C \uC900\uBE44\uD558\uACE0 \uC788\uC5B4\uC694"
private const val LOW_VISION_NAVIGATION_MINUTE_UNIT = "\uBD84"

internal object LowVisionNavigationLayoutDefaults {
    val contentTopPadding = 56.dp
    val contentBottomPadding = 18.dp
    val contentGap = 18.dp
    val liveGuidanceMinHeight = 388.dp
    val liveGuidanceVerticalPadding = 30.dp
    val liveGuidanceIconSize = 108.dp
    val liveGuidanceTransitIconSize = 88.dp
    val liveGuidanceIconTextGap = 22.dp
    val liveGuidanceEyebrowFontSize = 26.sp
    val liveGuidanceEyebrowLineHeight = 32.sp
    val liveGuidanceMetricFontSize = 40.sp
    val liveGuidanceMetricLineHeight = 46.sp
    val liveGuidanceSegmentDistanceFontSize = 72.sp
    val liveGuidanceSegmentDistanceLineHeight = 80.sp
    val liveGuidanceActionFontSize = 62.sp
    val liveGuidanceActionLineHeight = 70.sp
    val liveGuidanceDetailFontSize = 30.sp
    val liveGuidanceDetailLineHeight = 38.sp
    val metricStripHeight = 136.dp
    val metricLabelFontSize = 24.sp
    val metricLabelLineHeight = 30.sp
    val metricNumberFontSize = 44.sp
    val metricNumberLineHeight = 50.sp
    val metricUnitFontSize = 24.sp
    val metricUnitLineHeight = 30.sp
    val statusCardVerticalPadding = 18.dp
    val statusCardHeaderFontSize = 26.sp
    val statusCardHeaderLineHeight = 32.sp
    val statusCardTitleFontSize = 40.sp
    val statusCardTitleLineHeight = 48.sp
    val statusCardBodyFontSize = 26.sp
    val statusCardBodyLineHeight = 34.sp
    val exitCardVerticalPadding = 22.dp
    val exitIconContainerSize = 92.dp
    val exitIconSize = 48.dp
    val exitIconTextGap = 14.dp
    val exitLabelFontSize = 36.sp
    val exitLabelLineHeight = 42.sp
}

internal data class LowVisionNavigationActionCard(
    val label: String,
    @DrawableRes val iconRes: Int,
)

internal data class LowVisionNavigationMetricSection(
    val label: String,
    val metricIndex: Int,
) {
    fun talkBackText(value: String): String = "$label $value"
}

internal data class LowVisionNavigationLiveGuidanceDisplay(
    val eyebrow: String,
    val remainingDistanceText: String,
    val remainingTimeText: String,
    val segmentDistanceText: String,
    val actionText: String,
    val detailText: String,
    val talkBackText: String,
    @DrawableRes val iconRes: Int,
    val guidanceAction: NavigationGuidanceAction,
)

internal data class LowVisionNavigationStatusDisplay(
    val header: String,
    val metricSummary: String,
    val title: String,
    val body: String,
    val talkBackText: String,
)

internal fun lowVisionNavigationMetricSections(): List<LowVisionNavigationMetricSection> =
    listOf(
        LowVisionNavigationMetricSection(label = "남은 거리", metricIndex = 0),
        LowVisionNavigationMetricSection(label = "남은 시간", metricIndex = 1),
    )

internal fun lowVisionNavigationActionCards(): List<LowVisionNavigationActionCard> =
    listOf(
        LowVisionNavigationActionCard(
            label = "\uC548\uB0B4 \uC644\uB8CC",
            iconRes = R.drawable.ic_action_close,
        ),
    )

internal fun lowVisionNavigationBottomTabs(): List<LowVisionBottomTab> =
    listOf(
        LowVisionBottomTab.HOME,
        LowVisionBottomTab.BOOKMARK,
        LowVisionBottomTab.CATEGORY,
        LowVisionBottomTab.MY_PAGE,
    )

internal const val LOW_VISION_NAVIGATION_LOAD_ERROR_MESSAGE: String = "길 안내를 불러오지 못했습니다."

internal fun lowVisionNavigationExitAction(): NavigationUiAction =
    NavigationUiAction.NavigationCompleteClicked

internal fun shouldShowLowVisionNavigationLoadError(
    uiState: NavigationUiState,
    loadErrorMessage: String?,
): Boolean = uiState.screenState == NavigationScreenState.Loading && !loadErrorMessage.isNullOrBlank()

internal fun lowVisionNavigationDisplayMetric(
    section: LowVisionNavigationMetricSection,
    rawValue: String,
): String {
    val trimmedValue = rawValue.trim()
    if (trimmedValue.isBlank() || trimmedValue == "-") return "-"

    return when (section.metricIndex) {
        0 -> trimmedValue.asDistanceLabel()
        1 -> trimmedValue.asMinuteLabel()
        else -> trimmedValue
    }
}

internal fun lowVisionNavigationLiveGuidanceDisplay(uiState: NavigationUiState): LowVisionNavigationLiveGuidanceDisplay {
    if (uiState.screenState == NavigationScreenState.Loading) {
        return LowVisionNavigationLiveGuidanceDisplay(
            eyebrow = LOW_VISION_NAVIGATION_PREPARING_EYEBROW,
            actionText = LOW_VISION_NAVIGATION_PREPARING_ACTION,
            remainingDistanceText = LOW_VISION_NAVIGATION_DISTANCE_PENDING,
            remainingTimeText = LOW_VISION_NAVIGATION_TIME_PENDING,
            segmentDistanceText = LOW_VISION_NAVIGATION_SEGMENT_PENDING_SHORT,
            detailText = LOW_VISION_NAVIGATION_PREPARING_DETAIL,
            talkBackText = "$LOW_VISION_NAVIGATION_SEGMENT_PREFIX $LOW_VISION_NAVIGATION_SEGMENT_PENDING_SHORT $LOW_VISION_NAVIGATION_PREPARING_ACTION_TALKBACK.",
            iconRes = NavigationGuidanceAction.STRAIGHT.iconRes(),
            guidanceAction = NavigationGuidanceAction.STRAIGHT,
        )
    }

    val actionText =
        uiState.stepCard.instruction.trim().ifBlank {
            uiState.stepCard.heroTitle.trim().ifBlank { LOW_VISION_NAVIGATION_PREPARING_ACTION }
        }
    val detailText =
        listOf(
            uiState.stepCard.heroDescription.trim(),
            uiState.stepCard.supportingText.trim(),
        ).firstOrNull { candidate ->
            candidate.isNotBlank() && candidate != actionText
        } ?: LOW_VISION_NAVIGATION_PREPARING_DETAIL
    val eyebrow =
        when (uiState.screenState) {
            NavigationScreenState.Loading -> LOW_VISION_NAVIGATION_PREPARING_EYEBROW
            NavigationScreenState.Ready,
            NavigationScreenState.Empty,
                -> LOW_VISION_NAVIGATION_LIVE_EYEBROW
        }
    val remainingDistanceText =
        lowVisionNavigationMetricPhrase(
            label = LOW_VISION_NAVIGATION_DISTANCE_LABEL,
            rawValue = uiState.remainingDistanceLabel,
            fallback = LOW_VISION_NAVIGATION_DISTANCE_PENDING,
            metricIndex = 0,
        )
    val remainingTimeText =
        lowVisionNavigationMetricPhrase(
            label = LOW_VISION_NAVIGATION_TIME_LABEL,
            rawValue = uiState.remainingEtaLabel,
            fallback = LOW_VISION_NAVIGATION_TIME_PENDING,
            metricIndex = 1,
        )
    val segmentDistanceText =
        lowVisionNavigationSegmentDistancePhrase(uiState.stepCard.distanceLabel, actionText)
    val talkBackText =
        "$LOW_VISION_NAVIGATION_SEGMENT_PREFIX $segmentDistanceText $LOW_VISION_NAVIGATION_APPROXIMATE_SUFFIX " +
            "${uiState.stepCard.guidanceAction.toLowVisionNavigationActionPhrase()}."

    return LowVisionNavigationLiveGuidanceDisplay(
        eyebrow = eyebrow,
        remainingDistanceText = remainingDistanceText,
        remainingTimeText = remainingTimeText,
        segmentDistanceText = segmentDistanceText,
        actionText = actionText,
        detailText = detailText,
        talkBackText = talkBackText,
        iconRes = uiState.stepCard.guidanceAction.iconRes(),
        guidanceAction = uiState.stepCard.guidanceAction,
    )
}

internal fun lowVisionNavigationStatusDisplay(uiState: NavigationUiState): LowVisionNavigationStatusDisplay {
    val title =
        uiState.stepCard.heroTitle.trim().ifBlank {
            if (uiState.screenState == NavigationScreenState.Loading) {
                LOW_VISION_NAVIGATION_PREPARING_STATUS_TITLE
            } else {
                LOW_VISION_NAVIGATION_STATUS_HEADER
            }
        }
    val bodyCandidates =
        buildList {
            val supportingText = uiState.stepCard.supportingText.trim()
            if (supportingText.isNotBlank() && supportingText != title) add(supportingText)
            val progressLabel = uiState.progressLabel.trim()
            if (progressLabel.isNotBlank() && progressLabel != "-") add("$LOW_VISION_NAVIGATION_PROGRESS_PREFIX $progressLabel")
        }
    val body =
        bodyCandidates.firstOrNull()
            ?: if (uiState.screenState == NavigationScreenState.Loading) {
                LOW_VISION_NAVIGATION_PREPARING_STATUS_BODY
            } else {
                LOW_VISION_NAVIGATION_STATUS_BODY
            }
    val metricSummary =
        listOf(
            lowVisionNavigationMetricPhrase(
                label = LOW_VISION_NAVIGATION_DISTANCE_LABEL,
                rawValue = uiState.remainingDistanceLabel,
                fallback = "$LOW_VISION_NAVIGATION_DISTANCE_LABEL -",
                metricIndex = 0,
                preserveDash = true,
            ),
            lowVisionNavigationMetricPhrase(
                label = LOW_VISION_NAVIGATION_TIME_LABEL,
                rawValue = uiState.remainingEtaLabel,
                fallback = "$LOW_VISION_NAVIGATION_TIME_LABEL -",
                metricIndex = 1,
                preserveDash = true,
            ),
        ).joinToString(separator = " \u00B7 ")
    return LowVisionNavigationStatusDisplay(
        header = LOW_VISION_NAVIGATION_STATUS_HEADER,
        metricSummary = metricSummary,
        title = title,
        body = body,
        talkBackText = listOf(LOW_VISION_NAVIGATION_STATUS_HEADER, metricSummary, title, body).joinToString(separator = " "),
    )
}

private data class LowVisionMetricValueParts(
    val number: String,
    val unit: String,
)

private val metricNumberRegex = Regex("""\d+(?:\.\d+)?""")

private fun String.asDistanceLabel(): String {
    if (endsWith("km", ignoreCase = true)) {
        return metricNumberRegex.find(this)?.value?.let { number -> "${number}km" } ?: this
    }

    val meters = metricNumberRegex.find(this)?.value?.toDoubleOrNull() ?: return this
    return if (meters >= 1000.0) {
        "${(meters / 1000.0).toKilometerText()}km"
    } else {
        "${meters.toInt()}m"
    }
}

private fun String.asMinuteLabel(): String {
    if (endsWith("분")) return this

    val minutes = metricNumberRegex.find(this)?.value?.toDoubleOrNull() ?: return this
    return "${minutes.toInt()}분"
}

private fun lowVisionNavigationMetricPhrase(
    label: String,
    rawValue: String,
    fallback: String,
    metricIndex: Int,
    preserveDash: Boolean = false,
): String {
    val value = rawValue.trim()
    if (value.isBlank() || value == "-") {
        return if (preserveDash) "$label -" else fallback
    }

    val displayValue =
        lowVisionNavigationDisplayMetric(
            LowVisionNavigationMetricSection(label = label, metricIndex = metricIndex),
            value,
        )
    if (displayValue == "-" || (!displayValue.containsKnownMetricUnit() && metricNumberRegex.find(displayValue) == null)) {
        return fallback
    }
    return "$label $displayValue"
}

private fun lowVisionNavigationSegmentDistancePhrase(
    rawDistanceLabel: String,
    actionText: String,
): String {
    val distance =
        rawDistanceLabel.trim().takeIf { value ->
            value.isNotBlank() && value != "-" && (value.containsKnownMetricUnit() || metricNumberRegex.find(value) != null)
        } ?: Regex("""\d+(?:\.\d+)?\s*(?:km|m)""", RegexOption.IGNORE_CASE)
            .find(actionText)
            ?.value
            ?.replace(" ", "")
    return distance ?: LOW_VISION_NAVIGATION_SEGMENT_PENDING_SHORT
}

private fun String.containsKnownMetricUnit(): Boolean =
    contains("km", ignoreCase = true) || contains("m", ignoreCase = true) || contains(LOW_VISION_NAVIGATION_MINUTE_UNIT)

private fun NavigationGuidanceAction.toLowVisionNavigationActionPhrase(): String =
    when (this) {
        NavigationGuidanceAction.ARRIVAL -> "\uBAA9\uC801\uC9C0\uC5D0 \uB3C4\uCC29\uD588\uC2B5\uB2C8\uB2E4"
        NavigationGuidanceAction.START -> "\uCD9C\uBC1C\uC9C0\uC785\uB2C8\uB2E4"
        NavigationGuidanceAction.ALIGHT -> "\uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4"
        NavigationGuidanceAction.BUS -> "\uBC84\uC2A4\uB97C \uC774\uC6A9\uD558\uC138\uC694"
        NavigationGuidanceAction.SUBWAY -> "\uC9C0\uD558\uCCA0\uC744 \uC774\uC6A9\uD558\uC138\uC694"
        NavigationGuidanceAction.STRAIGHT -> "\uC9C1\uC9C4\uD558\uC138\uC694"
        NavigationGuidanceAction.TURN_LEFT -> "\uC88C\uD68C\uC804\uD558\uC138\uC694"
        NavigationGuidanceAction.TURN_RIGHT -> "\uC6B0\uD68C\uC804\uD558\uC138\uC694"
        NavigationGuidanceAction.CROSSWALK -> "\uD6A1\uB2E8\uBCF4\uB3C4\uB97C \uAC74\uB108\uC138\uC694"
        NavigationGuidanceAction.TACTILE_GUIDE -> "\uC810\uC790\uBE14\uB85D\uC744 \uB530\uB77C \uC774\uB3D9\uD558\uC138\uC694"
        NavigationGuidanceAction.ELEVATOR -> "\uC5D8\uB9AC\uBCA0\uC774\uD130\uB97C \uC774\uC6A9\uD558\uC138\uC694"
        NavigationGuidanceAction.CONSTRUCTION -> "\uACF5\uC0AC \uAD6C\uAC04\uC744 \uC8FC\uC758\uD558\uC138\uC694"
        NavigationGuidanceAction.CURB_GAP -> "\uB2E8\uCC28\uB97C \uC8FC\uC758\uD558\uC138\uC694"
        NavigationGuidanceAction.STAIRS -> "\uACC4\uB2E8\uC744 \uC8FC\uC758\uD558\uC138\uC694"
        NavigationGuidanceAction.FALLBACK -> "\uC138\uBD80 \uACBD\uB85C\uB97C \uD655\uC778\uD558\uC138\uC694"
    }

private fun Double.toKilometerText(): String {
    val tenths = kotlin.math.round(this * 10).toInt()
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0) {
        whole.toString()
    } else {
        "$whole.$fraction"
    }
}

private fun lowVisionNavigationMetricValueParts(value: String): LowVisionMetricValueParts {
    val unit =
        when {
            value.endsWith("km", ignoreCase = true) -> "km"
            value.endsWith("m", ignoreCase = true) -> "m"
            value.endsWith("분") -> "분"
            else -> ""
        }

    return if (unit.isBlank()) {
        LowVisionMetricValueParts(number = value, unit = "")
    } else {
        LowVisionMetricValueParts(number = value.removeSuffix(unit), unit = unit)
    }
}

@Composable
fun LowVisionNavigationScreen(
    uiState: NavigationUiState,
    onAction: (NavigationUiAction) -> Unit,
    modifier: Modifier = Modifier,
    onTabSelected: (LowVisionBottomTab) -> Unit = {},
    loadErrorMessage: String? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(LowVisionNavigationBackground),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(
                        top = LowVisionNavigationLayoutDefaults.contentTopPadding,
                        bottom = LowVisionNavigationLayoutDefaults.contentBottomPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(LowVisionNavigationLayoutDefaults.contentGap),
        ) {
            if (shouldShowLowVisionNavigationLoadError(uiState, loadErrorMessage)) {
                LowVisionNavigationLoadError(
                    message = loadErrorMessage.orEmpty(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            } else {
                LowVisionNavigationLiveGuidanceCard(
                    display = lowVisionNavigationLiveGuidanceDisplay(uiState),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = LowVisionNavigationLayoutDefaults.liveGuidanceMinHeight)
                            .weight(1.75f),
                )

                LowVisionNavigationMetricStrip(
                    uiState = uiState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )

                LowVisionExitNavigationCard(
                    card = lowVisionNavigationActionCards().first(),
                    enabled = uiState.isExitEnabled,
                    onClick = { onAction(lowVisionNavigationExitAction()) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }
        }

        LowVisionBottomNav(
            selectedTab = LowVisionBottomTab.HOME,
            onTabSelected = onTabSelected,
        )
    }
}

@Composable
private fun LowVisionNavigationLoadError(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .clearAndSetSemantics {
                    contentDescription = message
                },
        shape = RoundedCornerShape(18.dp),
        color = LowVisionNavigationPanel,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                color = LowVisionNavigationYellow,
                fontSize = 56.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LowVisionNavigationMetricStrip(
    uiState: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = LowVisionNavigationPanel,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lowVisionNavigationMetricSections().forEachIndexed { index, section ->
                val rawValue = uiState.stepCard.metrics.getOrNull(section.metricIndex)?.value.orEmpty()
                val value = lowVisionNavigationDisplayMetric(section, rawValue)
                LowVisionNavigationMetricItem(
                    section = section,
                    value = value,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                )
                if (index == 0) {
                    Box(
                        modifier =
                            Modifier
                                .width(1.dp)
                                .height(92.dp)
                                .background(LowVisionNavigationDivider),
                    )
                }
            }
        }
    }
}

@Composable
private fun LowVisionNavigationMetricItem(
    section: LowVisionNavigationMetricSection,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clearAndSetSemantics {
                    contentDescription = section.talkBackText(value)
                }
                .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = section.label,
            color = Color.White,
            fontSize = LowVisionNavigationLayoutDefaults.metricLabelFontSize,
            lineHeight = LowVisionNavigationLayoutDefaults.metricLabelLineHeight,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        LowVisionNavigationMetricValue(
            value = value,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LowVisionNavigationMetricValue(
    value: String,
    modifier: Modifier = Modifier,
) {
    val parts = lowVisionNavigationMetricValueParts(value)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = parts.number,
            color = LowVisionNavigationYellow,
            fontSize = LowVisionNavigationLayoutDefaults.metricNumberFontSize,
            lineHeight = LowVisionNavigationLayoutDefaults.metricNumberLineHeight,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (parts.unit.isNotBlank()) {
            Text(
                text = parts.unit,
                color = LowVisionNavigationYellow,
                fontSize = LowVisionNavigationLayoutDefaults.metricUnitFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.metricUnitLineHeight,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(start = 3.dp, bottom = 5.dp),
            )
        }
    }
}

@Composable
private fun LowVisionNavigationLiveGuidanceCard(
    display: LowVisionNavigationLiveGuidanceDisplay,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = LowVisionNavigationYellow,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {
                        contentDescription = display.talkBackText
                    }
                    .padding(
                        horizontal = 24.dp,
                        vertical = LowVisionNavigationLayoutDefaults.liveGuidanceVerticalPadding,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = display.iconRes),
                contentDescription = null,
                tint = Color.Black,
                modifier =
                    Modifier
                        .size(
                            lowVisionGuidanceIconSize(display.guidanceAction),
                        ),
            )
            Text(
                text = display.segmentDistanceText,
                color = Color.Black,
                fontSize = LowVisionNavigationLayoutDefaults.liveGuidanceSegmentDistanceFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.liveGuidanceSegmentDistanceLineHeight,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(top = LowVisionNavigationLayoutDefaults.liveGuidanceIconTextGap),
            )
            Text(
                text = display.actionText,
                color = Color.Black,
                fontSize = LowVisionNavigationLayoutDefaults.liveGuidanceActionFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.liveGuidanceActionLineHeight,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = display.detailText,
                color = Color.Black.copy(alpha = 0.82f),
                fontSize = LowVisionNavigationLayoutDefaults.liveGuidanceDetailFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.liveGuidanceDetailLineHeight,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun LowVisionNavigationStatusCard(
    display: LowVisionNavigationStatusDisplay,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = LowVisionNavigationPanel,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {
                        contentDescription = display.talkBackText
                    }
                    .padding(
                        horizontal = 24.dp,
                        vertical = LowVisionNavigationLayoutDefaults.statusCardVerticalPadding,
                    ),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = display.header,
                color = LowVisionNavigationYellow,
                fontSize = LowVisionNavigationLayoutDefaults.statusCardHeaderFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.statusCardHeaderLineHeight,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            )
            Text(
                text = display.metricSummary,
                color = Color.White,
                fontSize = LowVisionNavigationLayoutDefaults.statusCardBodyFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.statusCardBodyLineHeight,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = display.title,
                color = Color.White,
                fontSize = LowVisionNavigationLayoutDefaults.statusCardTitleFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.statusCardTitleLineHeight,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = display.body,
                color = LowVisionNavigationInactive,
                fontSize = LowVisionNavigationLayoutDefaults.statusCardBodyFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.statusCardBodyLineHeight,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun LowVisionExitNavigationCard(
    card: LowVisionNavigationActionCard,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (enabled) 1f else 0.55f

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .lowVisionButtonSemantics(
                    label = card.label,
                    actionHint = "\uB450 \uBC88 \uD0ED\uD558\uBA74 \uAE38 \uC548\uB0B4\uB97C \uC644\uB8CC\uD569\uB2C8\uB2E4.",
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = LowVisionNavigationYellow,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 24.dp,
                        vertical = LowVisionNavigationLayoutDefaults.exitCardVerticalPadding,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = card.label,
                color = Color.Black.copy(alpha = contentAlpha),
                fontSize = LowVisionNavigationLayoutDefaults.exitLabelFontSize,
                lineHeight = LowVisionNavigationLayoutDefaults.exitLabelLineHeight,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun lowVisionGuidanceIconSize(guidanceAction: NavigationGuidanceAction): Dp =
    if (guidanceAction == NavigationGuidanceAction.BUS || guidanceAction == NavigationGuidanceAction.SUBWAY) {
        LowVisionNavigationLayoutDefaults.liveGuidanceTransitIconSize
    } else {
        LowVisionNavigationLayoutDefaults.liveGuidanceIconSize
    }

private const val LOW_VISION_NAVIGATION_LIVE_EYEBROW = "지금 해야 할 행동"
private const val LOW_VISION_NAVIGATION_PREPARING_EYEBROW = "안내 준비 중"
private const val LOW_VISION_NAVIGATION_PREPARING_ACTION = "길 안내를 준비하고 있어요"
private const val LOW_VISION_NAVIGATION_PREPARING_DETAIL = "현재 위치와 경로 안내를 확인하는 중입니다."
private const val LOW_VISION_NAVIGATION_STATUS_HEADER = "이동 상태"
private const val LOW_VISION_NAVIGATION_PREPARING_STATUS_TITLE = "안내 시작 전이에요"
private const val LOW_VISION_NAVIGATION_PREPARING_STATUS_BODY = "경로가 준비되면 지금 해야 할 행동을 바로 알려드릴게요."
private const val LOW_VISION_NAVIGATION_STATUS_BODY = "안전한 경로를 따라 이동 중입니다."
private const val LOW_VISION_NAVIGATION_PROGRESS_PREFIX = "진행 단계"
