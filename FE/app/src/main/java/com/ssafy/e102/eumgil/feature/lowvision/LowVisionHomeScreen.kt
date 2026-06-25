package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.feature.lowvision.component.LowVisionHomeBottomNav

internal object LowVisionHomeLayoutDefaults {
    const val headerTitle = "\uBD80\uC0B0\uC774\uC74C\uAE38"
    val headerSlotHeight = LowVisionScreenDefaults.headerLineHeight.value.dp
    const val voiceActionCardWeight = 2f
    const val currentLocationCardWeight = 1f
    val actionCardGap = 40.dp
    val actionLabelFontSize = 44.sp
    val actionLabelLineHeight = 50.sp
    val actionLabelFontWeight = FontWeight.Black
    const val showsStatusGuide = false
    const val currentLocationAnnouncesButtonRole = false
}

/**
 * 시각지원 모드 메인 홈 화면.
 *
 * 출처: Figma file MREqSzkmwhRcXnFS3lzW17, node 371:105 ("home").
 *
 * 디자인 규칙(Figma get_variable_defs):
 *   - color/yellow/50  = #FFCC00 (Gold)        — 카드, 활성 nav
 *   - color/black/solid = #000000              — 배경
 *   - color/grey/12    = #1E1E1E                — info-box 배경
 *   - color/grey/20    = #333333                — info-box 보더
 *   - color/grey/47    = #777777 (Boulder)     — 비활성 nav
 *   - color/grey/80    = #CCCCCC (Silver)      — info-box 보조 텍스트
 *   - 카드 radius 24dp, info-box radius 16dp
 *   - 카드 라벨 28.8sp Bold(letter spacing -1), info-title 17.6sp Bold,
 *     info-body 14.4sp Regular, nav 11sp Regular
 *
 * Figma의 시뮬레이터 chrome(상단 9:41 status bar, 하단 안드로이드 navigation bar
 * 모형)은 시스템 시스템바가 처리하는 영역이므로 의도적으로 그리지 않는다.
 */
@Composable
fun LowVisionHomeScreen(
    uiState: LowVisionHomeUiState,
    onVoiceInputClick: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
    currentLocationDisplay: LowVisionCurrentLocationDisplay =
        lowVisionCurrentLocationDisplay(latitude = null, longitude = null),
) {
    val voiceInputLabel = stringResource(id = R.string.low_vision_home_voice_input_label)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .statusBarsPadding()
                .padding(
                    horizontal = LowVisionScreenDefaults.screenHorizontalPadding,
                    vertical = LowVisionScreenDefaults.screenVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(LowVisionHomeLayoutDefaults.actionCardGap),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LowVisionHomeLayoutDefaults.headerSlotHeight)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = LowVisionHomeLayoutDefaults.headerTitle,
                    color = LowVisionScreenDefaults.brandYellow,
                    fontSize = LowVisionScreenDefaults.headerFontSize,
                    lineHeight = LowVisionScreenDefaults.headerLineHeight,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                )
            }

            // 1) 음성 입력 카드 (하단바 제외 영역의 2/3)
            HomeYellowCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(LowVisionHomeLayoutDefaults.voiceActionCardWeight)
                    .lowVisionButtonSemantics(
                        label = voiceInputLabel,
                        actionHint = "두 번 탭하면 시작합니다.",
                    ),
                iconRes = R.drawable.ic_voice_mic,
                iconSize = 64.dp,
                label = voiceInputLabel,
                onClick = onVoiceInputClick,
            )

            // 2) 현재 위치 카드 (하단바 제외 영역의 1/3)
            HomeYellowCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(LowVisionHomeLayoutDefaults.currentLocationCardWeight),
                iconRes = R.drawable.ic_voice_location_pin,
                iconSize = 56.dp,
                label = currentLocationDisplay.title,
                supportingText = currentLocationDisplay.supportingText,
                readOnlyContentDescription = currentLocationDisplay.talkBackText,
                onClick = onCurrentLocationClick,
            )
        }

        LowVisionHomeBottomNav(
            selectedTab = uiState.selectedTab,
            onTabSelected = onTabSelected,
        )
    }
}

@Composable
private fun HomeYellowCard(
    iconRes: Int,
    iconSize: Dp,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    readOnlyContentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(LowVisionScreenDefaults.brandYellow)
            .clickable { onClick() }
            .then(
                if (readOnlyContentDescription != null) {
                    Modifier.clearAndSetSemantics {
                        contentDescription = readOnlyContentDescription
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(iconSize),
            )
            Text(
                text = label,
                color = Color.Black,
                fontSize = LowVisionHomeLayoutDefaults.actionLabelFontSize,
                lineHeight = LowVisionHomeLayoutDefaults.actionLabelLineHeight,
                fontWeight = LowVisionHomeLayoutDefaults.actionLabelFontWeight,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    color = Color.Black,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}
