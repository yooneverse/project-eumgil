package com.ssafy.e102.eumgil.feature.terms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.feature.terms.component.TermsPagerIndicator

internal object TermsGuideLayoutDefaults {
    const val showBottomNav: Boolean = false
    const val mainActionCardWeight = 2f
    const val detailActionCardWeight = 1f
    val actionCardCornerRadius = 35.dp
    val moreButtonCornerRadius = actionCardCornerRadius
    val cardLabelFontSize = 48.sp
    val textIconFontSize = 88.sp
    val textIconLineHeight = 96.sp
    val hintFontSize = 28.sp
    val hintLineHeight = 36.sp
    val moreButtonFontSize = 34.sp
    val moreButtonLineHeight = 42.sp
    val moreButtonMinHeight = 74.dp
    val actionCardGap = 32.dp
    val mainActionHorizontalPadding = 42.dp
    val bottomContentHorizontalPadding = 28.dp
    val bottomContentBottomPadding = 28.dp
}

/**
 * High-contrast "약관 안내" walkthrough screen — generic shell for all 5 steps.
 *
 * Source: Figma file MREqSzkmwhRcXnFS3lzW17 (E102-mockup), nodes
 * 328:486 / 328:528 / 328:570 / 328:612 / 328:652.
 *
 * Per-step content (icon, card label, hint copy, presence of "자세히 보기" button)
 * comes from [TermsGuideStep]. The screen itself is intentionally pure UI: it does
 * not own step state, navigation, or persistence — those belong to [TermsGuideRoute].
 *
 * Design rules taken from the Figma variables panel:
 *   - color/yellow/50  = #FFCC00  (Supernova)
 *   - color/black/solid = #000000
 *   - color/grey/27    = #444444  (Tundora, inactive dots)
 *   - radius main-card/button = 35dp
 *   - title 24sp/ExtraBold, card 40sp/Black + letter spacing -1sp,
 *     hint 28sp/Black, button 34sp/Black.
 */
@Composable
fun TermsGuideScreen(
    uiState: TermsGuideUiState,
    onAdvance: () -> Unit,
    onMoreDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = uiState.step
    val cardLabel = stringResource(id = step.cardLabelRes)
    val hintText = stringResource(id = step.hintRes)
    val cardA11y = stringResource(id = R.string.terms_guide_card_a11y, cardLabel, hintText)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 1. Header area: pagination dots + "약관 안내" title.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TermsPagerIndicator(
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps,
            )
            Text(
                text = stringResource(id = R.string.terms_guide_header_title),
                color = Color(0xFFFFCC00),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 40.dp),
            verticalArrangement = Arrangement.spacedBy(TermsGuideLayoutDefaults.actionCardGap),
        ) {
            // 2. Main card — yellow surface with step icon + step label.
            //    Double-tap commits the step (advance or finalize), per Figma hint copy
            //    and the existing onboarding voice-guide pattern.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(TermsGuideLayoutDefaults.mainActionCardWeight)
                    .padding(horizontal = TermsGuideLayoutDefaults.mainActionHorizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(TermsGuideLayoutDefaults.actionCardCornerRadius))
                        .background(Color(0xFFFFCC00))
                        .border(
                            width = 2.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(TermsGuideLayoutDefaults.actionCardCornerRadius),
                        )
                        .semantics {
                            contentDescription = cardA11y
                        }
                        .clickable(
                            role = Role.Button,
                            onClickLabel = cardA11y,
                            onClick = onAdvance,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (step.iconText == null) {
                            Icon(
                                painter = painterResource(id = step.iconRes),
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(90.dp),
                            )
                        } else {
                            Text(
                                text = step.iconText,
                                color = Color.Black,
                                fontSize = TermsGuideLayoutDefaults.textIconFontSize,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.sp,
                                lineHeight = TermsGuideLayoutDefaults.textIconLineHeight,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = cardLabel,
                            color = Color.Black,
                            fontSize = TermsGuideLayoutDefaults.cardLabelFontSize,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp,
                        )
                    }
                }
            }

            // 3. Bottom detail action — use the same 2:1 touch-target rhythm as low-vision home.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(TermsGuideLayoutDefaults.detailActionCardWeight)
                    .background(Color.Black)
                    .padding(
                        start = TermsGuideLayoutDefaults.bottomContentHorizontalPadding,
                        end = TermsGuideLayoutDefaults.bottomContentHorizontalPadding,
                        bottom = TermsGuideLayoutDefaults.bottomContentBottomPadding,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = hintText,
                    color = Color.White,
                    fontSize = TermsGuideLayoutDefaults.hintFontSize,
                    lineHeight = TermsGuideLayoutDefaults.hintLineHeight,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )

                if (step.showMoreButton) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .defaultMinSize(minHeight = TermsGuideLayoutDefaults.moreButtonMinHeight)
                            .clip(RoundedCornerShape(TermsGuideLayoutDefaults.moreButtonCornerRadius))
                            .background(Color(0xFFFFCC00))
                            .clickable { onMoreDetails() }
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(id = R.string.terms_guide_more_button),
                            color = Color.Black,
                            fontSize = TermsGuideLayoutDefaults.moreButtonFontSize,
                            lineHeight = TermsGuideLayoutDefaults.moreButtonLineHeight,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
