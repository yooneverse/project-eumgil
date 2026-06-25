package com.ssafy.e102.eumgil.feature.lowvision

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.feature.lowvision.component.LowVisionBottomNav

private val LowVisionYellow = LowVisionScreenDefaults.brandYellow

internal object LowVisionMyPageLayoutDefaults {
    const val actionCount = 3
    const val actionSectionWeight = 1f
    val actionMinHeight = 112.dp
    val actionLabelFontSize = 36.sp
    val actionLabelLineHeight = 42.sp
}

internal object LowVisionAppInfoLayoutDefaults {
    const val infoPanelCount = 2
    const val textSizeActionCount = 1
    const val withdrawActionCount = 1
    val textSizeActionMinHeight = LowVisionMyPageLayoutDefaults.actionMinHeight
    val withdrawActionMinHeight = LowVisionMyPageLayoutDefaults.actionMinHeight
}

@Composable
fun LowVisionMyPageScreen(
    isLogoutLoading: Boolean,
    snackbarHostState: SnackbarHostState,
    onModeChangeClick: () -> Unit,
    onAppInfoClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .statusBarsPadding()
                        .padding(
                            horizontal = LowVisionScreenDefaults.screenHorizontalPadding,
                            vertical = LowVisionScreenDefaults.screenVerticalPadding,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LowVisionScreenDefaults.headerGap),
            ) {
                Text(
                    text = stringResource(id = R.string.low_vision_my_page_title),
                    modifier = Modifier.fillMaxWidth(),
                    color = LowVisionYellow,
                    fontSize = LowVisionScreenDefaults.headerFontSize,
                    fontWeight = FontWeight.Black,
                    lineHeight = LowVisionScreenDefaults.headerLineHeight,
                    textAlign = TextAlign.Center,
                )

                LowVisionMyPageAction(
                    labelRes = R.string.low_vision_my_page_mode_change,
                    iconRes = R.drawable.ic_lowvision_mode_change,
                    filled = true,
                    onClick = onModeChangeClick,
                    modifier = Modifier.weight(LowVisionMyPageLayoutDefaults.actionSectionWeight),
                )
                LowVisionMyPageAction(
                    labelRes = R.string.low_vision_my_page_app_info,
                    iconRes = R.drawable.ic_status_help_circle,
                    filled = false,
                    onClick = onAppInfoClick,
                    modifier = Modifier.weight(LowVisionMyPageLayoutDefaults.actionSectionWeight),
                )
                LowVisionMyPageAction(
                    labelRes =
                        if (isLogoutLoading) {
                            R.string.low_vision_my_page_logout_loading
                        } else {
                            R.string.low_vision_my_page_logout
                        },
                    iconRes = R.drawable.ic_lowvision_logout,
                    filled = false,
                    enabled = !isLogoutLoading,
                    onClick = onLogoutClick,
                    modifier = Modifier.weight(LowVisionMyPageLayoutDefaults.actionSectionWeight),
                )
            }

            LowVisionBottomNav(
                selectedTab = LowVisionBottomTab.MY_PAGE,
                onTabSelected = onTabSelected,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 104.dp),
        )
    }
}

@Composable
fun LowVisionAppInfoScreen(
    isWithdrawLoading: Boolean,
    snackbarHostState: SnackbarHostState,
    onTextSizeClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .statusBarsPadding()
                        .padding(
                            horizontal = LowVisionScreenDefaults.screenHorizontalPadding,
                            vertical = LowVisionScreenDefaults.screenVerticalPadding,
                        ),
                verticalArrangement = Arrangement.spacedBy(LowVisionScreenDefaults.headerGap),
            ) {
                Text(
                    text = stringResource(id = R.string.low_vision_app_info_title),
                    modifier = Modifier.fillMaxWidth(),
                    color = LowVisionYellow,
                    fontSize = LowVisionScreenDefaults.headerFontSize,
                    fontWeight = FontWeight.Black,
                    lineHeight = LowVisionScreenDefaults.headerLineHeight,
                    textAlign = TextAlign.Center,
                )
                LowVisionInfoPanel(
                    titleRes = R.string.low_vision_app_info_service_title,
                    bodyRes = R.string.low_vision_app_info_service_body,
                    modifier = Modifier.weight(1f),
                )
                LowVisionInfoPanel(
                    titleRes = R.string.low_vision_app_info_support_title,
                    bodyRes = R.string.low_vision_app_info_support_body,
                    modifier = Modifier.weight(1f),
                )
                LowVisionMyPageAction(
                    labelRes = R.string.low_vision_app_info_text_size,
                    iconRes = R.drawable.ic_terms_document,
                    filled = true,
                    onClick = onTextSizeClick,
                    modifier = Modifier.heightIn(min = LowVisionAppInfoLayoutDefaults.textSizeActionMinHeight),
                )
                LowVisionMyPageAction(
                    labelRes =
                        if (isWithdrawLoading) {
                            R.string.low_vision_app_info_withdraw_loading
                        } else {
                            R.string.low_vision_app_info_withdraw
                        },
                    iconRes = R.drawable.ic_lowvision_logout,
                    filled = false,
                    enabled = !isWithdrawLoading,
                    onClick = onWithdrawClick,
                    modifier = Modifier.heightIn(min = LowVisionAppInfoLayoutDefaults.withdrawActionMinHeight),
                )
            }

            LowVisionBottomNav(
                selectedTab = LowVisionBottomTab.MY_PAGE,
                onTabSelected = onTabSelected,
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 104.dp),
        )
    }
}

@Composable
private fun LowVisionMyPageAction(
    @StringRes labelRes: Int,
    @DrawableRes iconRes: Int,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = stringResource(id = labelRes)
    val baseBackgroundColor = if (filled) LowVisionYellow else Color.Black
    val baseContentColor = if (filled) Color.Black else LowVisionYellow
    val backgroundColor = if (enabled) baseBackgroundColor else baseBackgroundColor.copy(alpha = 0.55f)
    val contentColor = if (enabled) baseContentColor else baseContentColor.copy(alpha = 0.55f)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = LowVisionMyPageLayoutDefaults.actionMinHeight)
                .lowVisionButtonSemantics(label)
                .clickable(
                    enabled = enabled,
                    onClickLabel = label,
                    role = Role.Button,
                    onClick = onClick,
                ),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border =
            if (filled) {
                null
            } else {
                BorderStroke(
                    width = 3.dp,
                    color =
                        if (enabled) {
                            LowVisionYellow
                        } else {
                            LowVisionYellow.copy(alpha = 0.55f)
                        },
                )
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(54.dp),
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = contentColor,
                fontSize = LowVisionMyPageLayoutDefaults.actionLabelFontSize,
                fontWeight = FontWeight.Black,
                lineHeight = LowVisionMyPageLayoutDefaults.actionLabelLineHeight,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun LowVisionInfoPanel(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(width = 3.dp, color = LowVisionYellow, shape = RoundedCornerShape(16.dp))
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(id = titleRes),
            color = LowVisionYellow,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 36.sp,
        )
        Text(
            text = stringResource(id = bodyRes),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp,
        )
    }
}
