package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.feature.lowvision.component.LowVisionBottomNav

private val BriefingBackground = Color(0xFF0D0D0F)
private val BriefingYellow = LowVisionScreenDefaults.brandYellow
private val BriefingBlack = Color(0xFF000000)
private val BriefingWhite = Color(0xFFFFFFFF)

internal object LowVisionRouteBriefingLayoutDefaults {
    val stepRowMinHeight = 118.dp
    val stepInstructionFontSize = 34.sp
    val stepInstructionLineHeight = 40.sp
    const val stepInstructionMaxLines = 2
}

@Composable
fun LowVisionRouteBriefingScreen(
    uiState: LowVisionRouteBriefingUiState,
    visibleSteps: List<LowVisionRouteBriefingStepUiState> = uiState.steps.visibleBriefingSteps(0),
    isPlaying: Boolean,
    onPlaybackClick: () -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BriefingBackground)
                .statusBarsPadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "경로 브리핑",
                color = BriefingWhite,
                fontSize = 56.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                modifier =
                    if (isPlaying) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier
                    },
            )
            Box(
                modifier =
                    Modifier
                        .height(8.dp)
                        .fillMaxWidth(0.25f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(BriefingYellow),
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 270.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                uiState.errorMessage?.let { errorMessage ->
                    BriefingStatusMessage(message = errorMessage)
                }
                if (uiState.errorMessage == null) {
                    when {
                        uiState.isLoading ->
                            BriefingStatusMessage(
                                message =
                                    "\uD604\uC7AC \uC704\uCE58 \uAE30\uC900\uC73C\uB85C \uACBD\uB85C \uBE0C\uB9AC\uD551\uC744 \uBD88\uB7EC\uC624\uB294 \uC911\uC785\uB2C8\uB2E4.",
                            )

                        visibleSteps.isEmpty() ->
                            BriefingStatusMessage(
                                message =
                                    "\uACBD\uB85C \uBE0C\uB9AC\uD551\uC744 \uBD88\uB7EC\uC62C \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.",
                            )

                        else ->
                            visibleSteps.forEach { step ->
                                BriefingStepRow(step = step, suppressTalkBack = isPlaying)
                            }
                    }
                }
            }

            BriefingPlaybackButton(
                isPlaying = isPlaying,
                enabled = !uiState.isLoading && uiState.steps.isNotEmpty(),
                onClick = onPlaybackClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp),
            )
        }

        Box(
            modifier =
                if (isPlaying) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier
                },
        ) {
            LowVisionBottomNav(
                selectedTab = LowVisionBottomTab.HOME,
                onTabSelected = onTabSelected,
            )
        }
    }
}

@Composable
private fun BriefingStatusMessage(message: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = LowVisionRouteBriefingLayoutDefaults.stepRowMinHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = BriefingWhite,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BriefingStepRow(
    step: LowVisionRouteBriefingStepUiState,
    suppressTalkBack: Boolean,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = LowVisionRouteBriefingLayoutDefaults.stepRowMinHeight)
                .then(
                    if (suppressTalkBack) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier.semantics {
                            contentDescription = "${step.sequence}단계 ${step.instruction}"
                        }
                    },
                ),
        shape = RoundedCornerShape(8.dp),
        color = BriefingYellow,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Text(
                text = step.sequence.toString().padStart(2, '0'),
                color = BriefingBlack,
                fontSize = 31.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
            Text(
                text = step.instruction,
                color = BriefingBlack,
                fontSize = LowVisionRouteBriefingLayoutDefaults.stepInstructionFontSize,
                lineHeight = LowVisionRouteBriefingLayoutDefaults.stepInstructionLineHeight,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                maxLines = LowVisionRouteBriefingLayoutDefaults.stepInstructionMaxLines,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    when (step.icon) {
                        LowVisionRouteBriefingStepIcon.STRAIGHT -> "↑"
                        LowVisionRouteBriefingStepIcon.TRANSIT -> "□"
                        LowVisionRouteBriefingStepIcon.TURN -> "↱"
                    },
                color = BriefingBlack,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun BriefingPlaybackButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = routeBriefingPlaybackButtonLabel(isPlaying)
    val icon = if (isPlaying) "■" else "▶"
    val alpha = if (enabled) 1f else 0.45f

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .lowVisionButtonSemantics(
                    label = label,
                    actionHint = routeBriefingPlaybackButtonActionHint(isPlaying),
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = BriefingYellow.copy(alpha = alpha),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = icon,
                color = BriefingBlack,
                fontSize = 82.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
            Spacer(modifier = Modifier.size(38.dp))
            Text(
                text = label,
                color = BriefingBlack,
                fontSize = 72.sp,
                lineHeight = 80.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
            )
        }
    }
}

internal fun routeBriefingPlaybackButtonLabel(isPlaying: Boolean): String =
    if (isPlaying) {
        "\uC911\uC9C0"
    } else {
        "\uC2DC\uC791"
    }

internal fun routeBriefingPlaybackButtonActionHint(isPlaying: Boolean): String =
    if (isPlaying) {
        "두 번 탭하면 경로 안내 음성을 중지합니다."
    } else {
        "두 번 탭하면 경로 안내를 시작합니다. 안내 중에는 화면 항목 안내를 줄입니다. 다시 누르면 중지합니다."
    }
