package com.ssafy.e102.eumgil.feature.mypage

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ssafy.e102.eumgil.BuildConfig
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.component.dialog.EumDuribalCallConfirmDialog
import com.ssafy.e102.eumgil.core.designsystem.component.dialog.EumDuribalCallConfirmDismissStyle
import com.ssafy.e102.eumgil.core.designsystem.component.navigation.EumCenteredTopBar
import com.ssafy.e102.eumgil.core.designsystem.theme.EumBorderSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary200
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSurfaceInfo
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextPrimary
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextSecondary
import com.ssafy.e102.eumgil.core.designsystem.theme.EumTextTertiary

@Composable
fun MyPageScreen(
    uiState: MyPageUiState,
    onAction: (MyPageUiAction) -> Unit,
    isDuribalConfirmDialogVisible: Boolean,
    isWithdrawConfirmDialogVisible: Boolean,
    onDuribalCallClick: () -> Unit,
    onDuribalConfirmDismiss: () -> Unit,
    onDuribalConfirm: () -> Unit,
    onWithdrawClick: () -> Unit,
    onWithdrawConfirmDismiss: () -> Unit,
    onWithdrawConfirm: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            EumCenteredTopBar(
                title = stringResource(id = R.string.my_page_screen_title),
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MyPageBackground,
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        start = EumSpacing.medium,
                        end = EumSpacing.medium,
                        top = EumSpacing.medium,
                        bottom = EumSpacing.small,
                    ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileOverviewCard(
                    uiState = uiState,
                    onUserTypeChangeClick = {
                        onAction(MyPageUiAction.UserTypeChangeClicked)
                    },
                )

                QuickActionGrid(
                    onDuribalCallClick = onDuribalCallClick,
                    onGuideClick = {
                        onAction(MyPageUiAction.MainMenuClicked(MyPageMenuItem.APP_HELP))
                    },
                )

                MainMenuCard(
                    onMenuClick = { menuItem ->
                        onAction(MyPageUiAction.MainMenuClicked(menuItem = menuItem))
                    },
                )
            }

            MyPageFooter(
                isLogoutLoading = uiState.isLogoutLoading,
                onLogoutClick = { onAction(MyPageUiAction.LogoutClicked) },
                onWithdrawClick = onWithdrawClick,
            )
        }
    }

    if (isDuribalConfirmDialogVisible) {
        EumDuribalCallConfirmDialog(
            onDismiss = onDuribalConfirmDismiss,
            onConfirm = onDuribalConfirm,
            dismissStyle = EumDuribalCallConfirmDismissStyle.SecondaryButton,
        )
    }

    if (isWithdrawConfirmDialogVisible) {
        MyPageWithdrawConfirmDialog(
            isLoading = uiState.isWithdrawLoading,
            onDismiss = onWithdrawConfirmDismiss,
            onConfirm = onWithdrawConfirm,
        )
    }
}

@Composable
private fun ProfileOverviewCard(
    uiState: MyPageUiState,
    onUserTypeChangeClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(uiState = uiState)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = resolveDisplayName(uiState),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = EumTextPrimary,
                            maxLines = 1,
                        )
                        MyPageBadge(text = stringResource(id = R.string.my_page_member_badge))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserModeIcon(uiState = uiState)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(id = resolveHeadlineTextRes(uiState)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EumPrimary600,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = resolveModeDescription(uiState),
                                style = MaterialTheme.typography.bodySmall,
                                color = EumTextSecondary,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onUserTypeChangeClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                shape = RoundedCornerShape(EumRadius.medium),
                border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.75f)),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = EumPrimary600,
                    ),
                elevation =
                    ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        disabledElevation = 0.dp,
                    ),
                contentPadding = PaddingValues(horizontal = EumSpacing.medium, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.my_page_change_user_type),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            HorizontalDivider(color = EumBorderSubtle)

            ProfileStatsRow(uiState = uiState)
        }
    }
}

@Composable
private fun ProfileAvatar(uiState: MyPageUiState) {
    val avatarDescription = stringResource(id = R.string.my_page_profile_avatar_description)
    val avatarRes = resolveProfileAvatarRes(uiState)

    Box(
        modifier =
            Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(EumSurfaceInfo)
                .semantics { contentDescription = avatarDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (avatarRes == R.drawable.ic_mypage_sf3_person_circle) {
            Icon(
                painter = painterResource(id = avatarRes),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = EumPrimary600,
            )
        } else {
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun UserModeIcon(uiState: MyPageUiState) {
    Box(
        modifier =
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(EumRadius.medium))
                .background(EumSurfaceInfo),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = resolveUserModeIconRes(uiState)),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = EumPrimary600,
        )
    }
}

@Composable
private fun ProfileStatsRow(uiState: MyPageUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem(
            iconRes = R.drawable.ic_mypage_sf3_exclamation_bubble,
            label = stringResource(id = R.string.my_page_stat_reports),
            value = uiState.reportHistoryCount,
            unit = "건",
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatItem(
            iconRes = R.drawable.ic_mypage_sf3_bookmark,
            label = stringResource(id = R.string.my_page_stat_bookmarks),
            value = uiState.totalBookmarkCount,
            unit = "건",
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatItem(
            iconRes = R.drawable.ic_mypage_sf3_location_north_line,
            label = stringResource(id = R.string.my_page_stat_recent_navigation),
            value = uiState.recentNavigationCount,
            unit = "회",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatItem(
    @DrawableRes iconRes: Int,
    label: String,
    value: Int,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = EumPrimary600,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = EumTextTertiary,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(value.toString())
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                            append(unit)
                        }
                    },
                style = MaterialTheme.typography.titleMedium,
                color = EumTextPrimary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier =
            Modifier
                .height(42.dp)
                .width(1.dp)
                .background(EumBorderSubtle),
    )
}

@Composable
private fun QuickActionGrid(
    onDuribalCallClick: () -> Unit,
    onGuideClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickActionCard(
            titleRes = R.string.my_page_duribal_title,
            iconRes = R.drawable.ic_mypage_duribal_call_vehicle,
            containerColor = EumSurfaceInfo,
            iconTint = Color.Unspecified,
            iconSize = 34.dp,
            onClick = onDuribalCallClick,
            modifier = Modifier.weight(1f),
        )
        QuickActionCard(
            titleRes = R.string.my_page_guide_title,
            iconRes = R.drawable.ic_mypage_sf3_doc_text,
            containerColor = MaterialTheme.colorScheme.surface,
            iconSize = 30.dp,
            onClick = onGuideClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionCard(
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int,
    containerColor: Color,
    iconTint: Color = EumPrimary600,
    iconSize: Dp = 34.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .heightIn(min = 88.dp)
                .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(EumRadius.medium),
        color = containerColor,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(id = titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = EumTextPrimary,
                    maxLines = 2,
                    softWrap = true,
                )
            }
            Icon(
                painter = painterResource(id = R.drawable.ic_mypage_sf3_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = EumPrimary600,
            )
        }
    }
}

@Composable
private fun MainMenuCard(
    onMenuClick: (MyPageMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.large),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
        ) {
            MyPageMenuRow(
                menuItem = MyPageMenuItem.NOTICE,
                titleRes = R.string.my_page_menu_notice,
                iconRes = R.drawable.ic_mypage_sf3_bell,
                onClick = onMenuClick,
            )
            MyPageMenuDivider()
            MyPageMenuRow(
                menuItem = MyPageMenuItem.TEXT_SIZE,
                titleRes = R.string.my_page_menu_text_size,
                iconRes = R.drawable.ic_mypage_sf3_doc_text,
                onClick = onMenuClick,
            )
            MyPageMenuDivider()
            MyPageMenuRow(
                menuItem = MyPageMenuItem.PRIVACY_POLICY,
                titleRes = R.string.my_page_app_info_privacy_policy,
                iconRes = R.drawable.ic_mypage_sf3_shield,
                onClick = onMenuClick,
            )
            MyPageMenuDivider()
            MyPageMenuRow(
                menuItem = MyPageMenuItem.SERVICE_TERMS,
                titleRes = R.string.my_page_app_info_service_terms,
                iconRes = R.drawable.ic_mypage_sf3_doc_text,
                onClick = onMenuClick,
            )
        }
    }
}

@Composable
private fun MyPageMenuRow(
    menuItem: MyPageMenuItem,
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int,
    onClick: (MyPageMenuItem) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val title = stringResource(id = titleRes)
    val suppressRipple = shouldSuppressMyPageMenuRipple(menuItem)
    val clickableModifier =
        if (suppressRipple) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = title,
                onClick = { onClick(menuItem) },
            )
        } else {
            Modifier.clickable(
                role = Role.Button,
                onClickLabel = title,
                onClick = { onClick(menuItem) },
            )
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .then(clickableModifier),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = EumTextPrimary,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = EumTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_mypage_sf3_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = EumTextTertiary,
        )
    }
}

@Composable
private fun MyPageMenuDivider() {
    HorizontalDivider(color = EumBorderSubtle)
}

@Composable
private fun MyPageFooter(
    isLogoutLoading: Boolean,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = EumSpacing.small, vertical = 0.dp)
                .heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FooterTextButton(
            text =
                stringResource(
                    id =
                        if (isLogoutLoading) {
                            R.string.my_page_logout_loading
                        } else {
                            R.string.my_page_logout
                        },
                ),
            enabled = !isLogoutLoading,
            onClick = onLogoutClick,
        )
        Spacer(modifier = Modifier.width(EumSpacing.medium))
        Box(
            modifier =
                Modifier
                    .height(16.dp)
                    .width(1.dp)
                    .background(EumBorderSubtle),
        )
        Spacer(modifier = Modifier.width(EumSpacing.medium))
        FooterTextButton(
            text = stringResource(id = R.string.my_page_app_info_withdraw),
            enabled = true,
            onClick = onWithdrawClick,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(id = R.string.my_page_app_info_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = EumTextTertiary,
        )
    }
}

@Composable
private fun FooterTextButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier =
            Modifier.clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = EumTextTertiary,
    )
}

@Composable
private fun MyPageBadge(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(EumRadius.full))
                .background(EumPrimary200)
                .padding(horizontal = EumSpacing.small, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = EumPrimary600,
        )
    }
}

@Composable
private fun MyPageWithdrawConfirmDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = EumSpacing.medium)
                    .widthIn(max = 360.dp),
            shape = RoundedCornerShape(EumRadius.large),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = EumSpacing.medium,
                            end = EumSpacing.medium,
                            top = EumSpacing.large,
                            bottom = EumSpacing.medium,
                        ),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.large),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(EumSpacing.medium)) {
                    Text(
                        text = stringResource(id = R.string.my_page_withdraw_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(id = R.string.my_page_withdraw_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(EumSpacing.small)) {
                    Button(
                        onClick = onConfirm,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(EumRadius.medium),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        elevation =
                            ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                                disabledElevation = 0.dp,
                            ),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError,
                            )
                        } else {
                            Text(
                                text = stringResource(id = R.string.my_page_withdraw_dialog_confirm),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(EumRadius.medium),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        elevation =
                            ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                                disabledElevation = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(id = R.string.my_page_withdraw_dialog_dismiss),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleChevron() {
    Box(
        modifier =
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_mypage_sf3_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = EumPrimary600,
        )
    }
}

internal fun shouldSuppressMyPageMenuRipple(menuItem: MyPageMenuItem): Boolean =
    menuItem == MyPageMenuItem.TEXT_SIZE ||
        menuItem == MyPageMenuItem.APP_HELP ||
        menuItem == MyPageMenuItem.PRIVACY_POLICY ||
        menuItem == MyPageMenuItem.SERVICE_TERMS

private val MyPageUserMode.labelRes: Int
    get() =
        when (this) {
            MyPageUserMode.LOW_VISION -> R.string.my_page_mode_low_vision
            MyPageUserMode.MOBILITY_IMPAIRED -> R.string.my_page_mode_mobility
            MyPageUserMode.UNKNOWN -> R.string.my_page_mode_unknown
        }

private val MyPageMobilitySubtype.labelRes: Int
    get() =
        when (this) {
            MyPageMobilitySubtype.ELECTRIC_WHEELCHAIR -> R.string.my_page_mobility_subtype_electric
            MyPageMobilitySubtype.MANUAL_WHEELCHAIR -> R.string.my_page_mobility_subtype_manual
            MyPageMobilitySubtype.OTHER -> R.string.my_page_mobility_subtype_other
        }

private val MyPageUiState.totalBookmarkCount: Int
    get() = placeBookmarkCount + routeBookmarkCount

@StringRes
internal fun resolveHeadlineTextRes(uiState: MyPageUiState): Int = uiState.userMode.labelRes

@DrawableRes
internal fun resolveProfileAvatarRes(uiState: MyPageUiState): Int =
    if (uiState.userMode != MyPageUserMode.MOBILITY_IMPAIRED) {
        R.drawable.ic_mypage_sf3_person_circle
    } else {
        when (uiState.mobilitySubtype) {
            MyPageMobilitySubtype.MANUAL_WHEELCHAIR -> R.drawable.manual_galmaegi
            MyPageMobilitySubtype.ELECTRIC_WHEELCHAIR -> R.drawable.auto_galmaegi
            MyPageMobilitySubtype.OTHER -> R.drawable.crutch_galmaegi
            null -> R.drawable.ic_mypage_sf3_person_circle
        }
    }

@DrawableRes
internal fun resolveUserModeIconRes(uiState: MyPageUiState): Int =
    when (uiState.userMode) {
        MyPageUserMode.LOW_VISION -> R.drawable.ic_user_visual_impairment
        MyPageUserMode.MOBILITY_IMPAIRED -> R.drawable.ic_user_wheelchair
        MyPageUserMode.UNKNOWN -> R.drawable.ic_mypage_sf3_person_circle
    }

internal fun resolveDisplayName(uiState: MyPageUiState): String =
    uiState.displayName?.trim()?.takeIf(String::isNotEmpty) ?: "사용자"

@Composable
private fun resolveModeDescription(uiState: MyPageUiState): String =
    uiState.mobilitySubtype
        ?.let { subtype -> stringResource(id = subtype.labelRes) }
        ?: when (uiState.userMode) {
            MyPageUserMode.LOW_VISION -> stringResource(id = R.string.my_page_mode_low_vision_description)
            MyPageUserMode.MOBILITY_IMPAIRED -> stringResource(id = R.string.my_page_mode_mobility_description)
            MyPageUserMode.UNKNOWN -> stringResource(id = R.string.my_page_mode_unknown_description)
        }

private val MyPageBackground = Color.White
