package com.ssafy.e102.eumgil.feature.lowvision

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.component.place.PlaceListAmber
import com.ssafy.e102.eumgil.core.designsystem.component.place.PlaceListBg
import com.ssafy.e102.eumgil.core.designsystem.component.place.PlaceListOnAmber
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.feature.savedroute.savedPlaceCategoryIconRes
import com.ssafy.e102.eumgil.feature.savedroute.savedPlaceCategoryLabel
import com.ssafy.e102.eumgil.feature.lowvision.component.LowVisionBottomNav
import com.ssafy.e102.eumgil.feature.savedroute.SavedPlaceUiModel
import com.ssafy.e102.eumgil.feature.savedroute.SavedBookmarkContentState
import com.ssafy.e102.eumgil.feature.savedroute.SavedRouteUiAction
import com.ssafy.e102.eumgil.feature.savedroute.SavedRouteUiState

private val LowVisionYellow = PlaceListAmber

internal object LowVisionBookmarkLayoutDefaults {
    val placeCardMinHeight = LowVisionSearchLayoutDefaults.resultCardMinHeight
    val placeCardGap = LowVisionSearchLayoutDefaults.resultCardGap
    val resultListBottomPadding = LowVisionSearchLayoutDefaults.resultListBottomPadding
    val actionButtonHeight = LowVisionSearchLayoutDefaults.actionButtonHeight
    val actionButtonGap = LowVisionSearchLayoutDefaults.actionButtonGap
    val cardHorizontalPadding = LowVisionSearchLayoutDefaults.cardHorizontalPadding
    val cardVerticalPadding = LowVisionSearchLayoutDefaults.cardVerticalPadding
    val cardContentGap = LowVisionSearchLayoutDefaults.cardContentGap
    val cardHeaderGap = LowVisionSearchLayoutDefaults.cardHeaderGap
    val indexBadgeSize = LowVisionSearchLayoutDefaults.indexBadgeSize
    val indexFontSize = LowVisionSearchLayoutDefaults.indexFontSize
    val indexLineHeight = LowVisionSearchLayoutDefaults.indexLineHeight
    val titleFontSize = LowVisionSearchLayoutDefaults.titleFontSize
    val titleLineHeight = LowVisionSearchLayoutDefaults.titleLineHeight
    val addressFontSize = LowVisionSearchLayoutDefaults.addressFontSize
    val addressLineHeight = LowVisionSearchLayoutDefaults.addressLineHeight
    val infoSectionMinHeight = LowVisionSearchLayoutDefaults.infoSectionMinHeight
    val sectionDividerThickness = LowVisionSearchLayoutDefaults.sectionDividerThickness
    val sectionDividerWidthFraction = LowVisionSearchLayoutDefaults.sectionDividerWidthFraction
    val sectionDividerTopPadding = LowVisionSearchLayoutDefaults.sectionDividerTopPadding
    val actionSectionTopPadding = LowVisionSearchLayoutDefaults.actionSectionTopPadding
    val actionIconSize = LowVisionSearchLayoutDefaults.actionIconSize
    val actionIconTextGap = LowVisionSearchLayoutDefaults.actionIconTextGap
    val actionLabelFontSize = LowVisionSearchLayoutDefaults.actionLabelFontSize
    val actionLabelLineHeight = LowVisionSearchLayoutDefaults.actionLabelLineHeight
    val categoryIconSize = 32.dp
    val roomyPhoneBreakpoint = LowVisionSearchLayoutDefaults.roomyPhoneBreakpoint
    const val compactTextMaxLines = LowVisionSearchLayoutDefaults.compactTextMaxLines
    const val roomyTextMaxLines = LowVisionSearchLayoutDefaults.roomyTextMaxLines
    const val actionButtonCount = 2

    fun placeCardMetrics(maxWidth: Dp): LowVisionBookmarkPlaceCardMetrics =
        if (maxWidth >= roomyPhoneBreakpoint) {
            LowVisionBookmarkPlaceCardMetrics(
                titleMaxLines = roomyTextMaxLines,
                addressMaxLines = roomyTextMaxLines,
            )
        } else {
            LowVisionBookmarkPlaceCardMetrics(
                titleMaxLines = compactTextMaxLines,
                addressMaxLines = compactTextMaxLines,
            )
        }
}

internal data class LowVisionBookmarkPlaceCardMetrics(
    val titleMaxLines: Int,
    val addressMaxLines: Int,
)

@Composable
fun LowVisionBookmarkScreen(
    uiState: SavedRouteUiState,
    onAction: (SavedRouteUiAction) -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
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
                text = stringResource(id = R.string.low_vision_bookmark_title),
                modifier = Modifier.fillMaxWidth(),
                color = LowVisionYellow,
                fontSize = LowVisionScreenDefaults.headerFontSize,
                fontWeight = FontWeight.Black,
                lineHeight = LowVisionScreenDefaults.headerLineHeight,
                textAlign = TextAlign.Center,
            )

            when (uiState.placeContent.screenState) {
                SavedBookmarkContentState.LOADING -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = LowVisionYellow)
                    }
                }
                SavedBookmarkContentState.EMPTY -> {
                    LowVisionBookmarkMessage(
                        message = stringResource(id = R.string.low_vision_bookmark_empty),
                    )
                }
                SavedBookmarkContentState.ERROR -> {
                    LowVisionBookmarkMessage(
                        message =
                            uiState.placeContent.errorMessage
                                ?: stringResource(id = R.string.low_vision_bookmark_error),
                    )
                }
                SavedBookmarkContentState.CONTENT -> {
                    LowVisionBookmarkPlaceList(
                        places = uiState.placeContent.places,
                        onBriefingClick = { place ->
                            onAction(SavedRouteUiAction.PlaceBriefingClicked(place.placeId))
                        },
                        onNavigateClick = { place ->
                            onAction(SavedRouteUiAction.PlaceRouteGuideClicked(place.placeId))
                        },
                        onRemoveClick = { place ->
                            onAction(SavedRouteUiAction.PlaceRemoveClicked(place.placeId))
                        },
                    )
                }
            }
        }

        LowVisionBottomNav(
            selectedTab = LowVisionBottomTab.BOOKMARK,
            onTabSelected = onTabSelected,
        )
    }
}

@Composable
private fun LowVisionBookmarkPlaceList(
    places: List<SavedPlaceUiModel>,
    onBriefingClick: (SavedPlaceUiModel) -> Unit,
    onNavigateClick: (SavedPlaceUiModel) -> Unit,
    onRemoveClick: (SavedPlaceUiModel) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardMetrics = LowVisionBookmarkLayoutDefaults.placeCardMetrics(maxWidth)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = EumSpacing.medium,
                    top = EumSpacing.medium,
                    end = EumSpacing.medium,
                    bottom = LowVisionBookmarkLayoutDefaults.resultListBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(LowVisionBookmarkLayoutDefaults.placeCardGap),
        ) {
            itemsIndexed(
                items = places,
                key = { _, place -> place.placeId },
            ) { index, place ->
                LowVisionBookmarkPlaceCard(
                    index = index + 1,
                    place = place,
                    onBriefingClick = { onBriefingClick(place) },
                    onNavigateClick = { onNavigateClick(place) },
                    onRemoveClick = { onRemoveClick(place) },
                    titleMaxLines = cardMetrics.titleMaxLines,
                    addressMaxLines = cardMetrics.addressMaxLines,
                    modifier =
                        Modifier.heightIn(
                            min = LowVisionBookmarkLayoutDefaults.placeCardMinHeight,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LowVisionBookmarkPlaceCard(
    index: Int,
    place: SavedPlaceUiModel,
    onBriefingClick: () -> Unit,
    onNavigateClick: () -> Unit,
    onRemoveClick: () -> Unit,
    titleMaxLines: Int,
    addressMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val addressText = lowVisionBriefAddress(place.address)
    val placeInfoContentDescription =
        "${savedPlaceCategoryLabel(place.category)}. " +
            lowVisionPlaceInfoA11yLabel(
                name = place.name,
                address = place.address,
            )
    val placeInfoSpeechText =
        "${savedPlaceCategoryLabel(place.category)}. " +
            lowVisionPlaceInfoSpeechText(
                name = place.name,
                address = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
            )
    val briefingContentDescription = "${place.name}. 탭하면 경로 브리핑으로 이동합니다."

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = 3.dp,
                    color = PlaceListAmber,
                    shape = RoundedCornerShape(18.dp),
                )
                .clip(RoundedCornerShape(18.dp))
                .background(PlaceListBg)
                .clickable(role = Role.Button, onClick = onBriefingClick)
                .semantics {
                    contentDescription = briefingContentDescription
                }
                .padding(
                    horizontal = LowVisionBookmarkLayoutDefaults.cardHorizontalPadding,
                    vertical = LowVisionBookmarkLayoutDefaults.cardVerticalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(LowVisionBookmarkLayoutDefaults.cardContentGap),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = LowVisionBookmarkLayoutDefaults.infoSectionMinHeight),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LowVisionBookmarkLayoutDefaults.cardHeaderGap),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(LowVisionBookmarkLayoutDefaults.indexBadgeSize)
                            .background(
                                color = PlaceListAmber,
                                shape = RoundedCornerShape(8.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = index.toString(),
                        fontSize = LowVisionBookmarkLayoutDefaults.indexFontSize,
                        fontWeight = FontWeight.Black,
                        color = PlaceListOnAmber,
                        lineHeight = LowVisionBookmarkLayoutDefaults.indexLineHeight,
                        letterSpacing = 0.sp,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(
                                role = Role.Button,
                                onClick = {
                                    view.announceForAccessibility(placeInfoSpeechText)
                                },
                            )
                            .semantics {
                                contentDescription = placeInfoContentDescription
                            },
                ) {
                    Icon(
                        painter = painterResource(id = savedPlaceCategoryIconRes(place.category)),
                        contentDescription = null,
                        tint = PlaceListAmber,
                        modifier = Modifier.size(LowVisionBookmarkLayoutDefaults.categoryIconSize),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = place.name,
                            color = Color.White,
                            fontSize = LowVisionBookmarkLayoutDefaults.titleFontSize,
                            fontWeight = FontWeight.Black,
                            lineHeight = LowVisionBookmarkLayoutDefaults.titleLineHeight,
                            letterSpacing = 0.sp,
                            maxLines = titleMaxLines,
                        )
                        Text(
                            text = addressText,
                            color = Color.White,
                            fontSize = LowVisionBookmarkLayoutDefaults.addressFontSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = LowVisionBookmarkLayoutDefaults.addressLineHeight,
                            letterSpacing = 0.sp,
                            maxLines = addressMaxLines,
                        )
                    }
                }
            }
        }

        LowVisionBookmarkCardSectionDivider()

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = LowVisionBookmarkLayoutDefaults.actionSectionTopPadding),
            verticalArrangement = Arrangement.spacedBy(LowVisionBookmarkLayoutDefaults.actionButtonGap),
        ) {
            LowVisionPlaceCardDefaults.actionOrder.forEach { action ->
                when (action) {
                    LowVisionPlaceCardAction.Navigate -> LowVisionBookmarkButton(
                        labelRes = R.string.low_vision_bookmark_navigate,
                        iconRes = LowVisionPlaceCardDefaults.routeIconRes,
                        onClick = onNavigateClick,
                    )

                    LowVisionPlaceCardAction.Bookmark -> LowVisionBookmarkButton(
                        labelRes = R.string.low_vision_bookmark_remove,
                        iconRes = LowVisionPlaceCardDefaults.saveIconRes,
                        onClick = onRemoveClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun LowVisionBookmarkCardSectionDivider() {
    Box(
        modifier =
            Modifier
                .padding(top = LowVisionBookmarkLayoutDefaults.sectionDividerTopPadding)
                .fillMaxWidth(LowVisionBookmarkLayoutDefaults.sectionDividerWidthFraction)
                .height(LowVisionBookmarkLayoutDefaults.sectionDividerThickness)
                .background(
                    color = PlaceListAmber,
                    shape = RoundedCornerShape(999.dp),
                ),
    )
}

@Composable
private fun LowVisionBookmarkButton(
    @StringRes labelRes: Int,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(id = labelRes)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = LowVisionBookmarkLayoutDefaults.actionButtonHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(PlaceListAmber)
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(color = PlaceListOnAmber),
                    onClickLabel = label,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics {
                    this.role = Role.Button
                    contentDescription = label
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = PlaceListOnAmber,
            modifier = Modifier.size(LowVisionBookmarkLayoutDefaults.actionIconSize),
        )
        Spacer(modifier = Modifier.size(LowVisionBookmarkLayoutDefaults.actionIconTextGap))
        Text(
            text = label,
            color = PlaceListOnAmber,
            fontSize = LowVisionBookmarkLayoutDefaults.actionLabelFontSize,
            fontWeight = FontWeight.Black,
            lineHeight = LowVisionBookmarkLayoutDefaults.actionLabelLineHeight,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(
            modifier =
                Modifier.width(
                    LowVisionBookmarkLayoutDefaults.actionIconSize +
                        LowVisionBookmarkLayoutDefaults.actionIconTextGap,
                ),
        )
    }
}

@Composable
private fun LowVisionBookmarkMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = LowVisionYellow,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 38.sp,
            textAlign = TextAlign.Center,
        )
    }
}
