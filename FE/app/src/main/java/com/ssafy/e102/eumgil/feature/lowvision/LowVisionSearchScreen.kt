package com.ssafy.e102.eumgil.feature.lowvision

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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.ssafy.e102.eumgil.core.designsystem.component.place.PlaceListSubText
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.SearchSortOption
import com.ssafy.e102.eumgil.feature.search.SearchResultUiState
import com.ssafy.e102.eumgil.feature.search.SearchUiAction
import com.ssafy.e102.eumgil.feature.search.SearchUiState
import com.ssafy.e102.eumgil.feature.search.resolveSearchResultDistanceUiState

internal object LowVisionSearchLayoutDefaults {
    val resultCardMinHeight = 288.dp
    val resultCardGap = 12.dp
    val twoCardViewportBudget = 680.dp
    val resultListBottomPadding = 64.dp
    val actionButtonHeight = 58.dp
    val actionButtonGap = 10.dp
    val cardHorizontalPadding = 20.dp
    val cardVerticalPadding = 16.dp
    val cardContentGap = 10.dp
    val cardHeaderGap = 16.dp
    val indexBadgeSize = 52.dp
    val indexFontSize = 30.sp
    val indexLineHeight = 34.sp
    val titleFontSize = 30.sp
    val titleLineHeight = 34.sp
    val addressFontSize = 20.sp
    val addressLineHeight = 24.sp
    val infoSectionMinHeight = 116.dp
    val sectionDividerThickness = 3.dp
    val sectionDividerWidthFraction = 0.2f
    val sectionDividerTopPadding = 2.dp
    val actionSectionTopPadding = 6.dp
    val actionIconSize = 32.dp
    val actionIconTextGap = 16.dp
    val actionLabelFontSize = 28.sp
    val actionLabelLineHeight = 32.sp
    const val noResultText = "결과없음"
    const val noResultTalkBackText = "결과없음. 다시 검색하시겠습니까"
    val roomyPhoneBreakpoint = 390.dp
    const val compactTextMaxLines = 2
    const val roomyTextMaxLines = 3
    const val actionButtonCount = 2

    fun resultCardMetrics(maxWidth: Dp): LowVisionSearchResultCardMetrics =
        if (maxWidth >= roomyPhoneBreakpoint) {
            LowVisionSearchResultCardMetrics(
                titleMaxLines = roomyTextMaxLines,
                addressMaxLines = roomyTextMaxLines,
            )
        } else {
            LowVisionSearchResultCardMetrics(
                titleMaxLines = compactTextMaxLines,
                addressMaxLines = compactTextMaxLines,
            )
        }
}

internal data class LowVisionSearchResultCardMetrics(
    val titleMaxLines: Int,
    val addressMaxLines: Int,
)

@Composable
fun LowVisionSearchScreen(
    uiState: SearchUiState,
    onAction: (SearchUiAction) -> Unit,
    modifier: Modifier = Modifier,
    categoryLabel: String? = null,
    isPreparingLocation: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PlaceListBg)
                .statusBarsPadding()
                .padding(
                    horizontal = LowVisionScreenDefaults.screenHorizontalPadding,
                    vertical = LowVisionScreenDefaults.screenVerticalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(LowVisionScreenDefaults.headerGap),
    ) {
        Text(
            text = "\uAC80\uC0C9 \uACB0\uACFC",
            fontSize = LowVisionScreenDefaults.headerFontSize,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = LowVisionScreenDefaults.headerLineHeight,
            color = PlaceListAmber,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!categoryLabel.isNullOrBlank()) {
            LowVisionSearchCategoryHeader(categoryLabel = categoryLabel)
        }
        LowVisionSearchSortControl(
            selectedSortOption = uiState.sortOption,
            onSortOptionSelected = { sortOption ->
                onAction(SearchUiAction.SortOptionSelected(sortOption = sortOption))
            },
        )

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            when (val state = uiState.resultState) {
                is SearchResultUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PlaceListAmber)
                    }
                }

                else -> if (isPreparingLocation) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PlaceListAmber)
                    }
                } else {
                    LowVisionSearchResultContent(
                        state = state,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun LowVisionSearchResultContent(
    state: SearchResultUiState,
    onAction: (SearchUiAction) -> Unit,
) {
    when (state) {
        is SearchResultUiState.Success -> {
            if (state.results.isEmpty()) {
                LowVisionSearchNoResultMessage()
            } else {
                LowVisionSearchResultList(
                    results = state.results,
                    onBookmarkClick = { result ->
                        onAction(SearchUiAction.LowVisionBookmarkSaveClicked(result = result))
                    },
                    onNavigateClick = { result ->
                        onAction(SearchUiAction.SearchResultClicked(result = result))
                    },
                    onBriefingClick = { result ->
                        onAction(SearchUiAction.SearchResultBriefingClicked(result = result))
                    },
                )
            }
        }

        is SearchResultUiState.Error ->
            LowVisionSearchNoResultMessage(
                message = state.message?.takeIf(String::isNotBlank) ?: LowVisionSearchLayoutDefaults.noResultText,
            )

        else -> LowVisionSearchNoResultMessage()
    }
}

@Composable
private fun LowVisionSearchCategoryHeader(categoryLabel: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = categoryLabel,
            color = Color.White,
            fontSize = 36.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.18f)
                    .height(6.dp)
                    .background(
                        color = PlaceListAmber,
                        shape = RoundedCornerShape(999.dp),
                    ),
        )
    }
}

@Composable
private fun LowVisionSearchSortControl(
    selectedSortOption: SearchSortOption,
    onSortOptionSelected: (SearchSortOption) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = PlaceListAmber,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LowVisionSearchSortButton(
            label = stringResource(id = R.string.search_screen_sort_relevance),
            selected = selectedSortOption == SearchSortOption.RELEVANCE,
            onClick = { onSortOptionSelected(SearchSortOption.RELEVANCE) },
            modifier = Modifier.weight(1f),
        )
        LowVisionSearchSortButton(
            label = stringResource(id = R.string.search_screen_sort_distance),
            selected = selectedSortOption == SearchSortOption.DISTANCE,
            onClick = { onSortOptionSelected(SearchSortOption.DISTANCE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LowVisionSearchSortButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) PlaceListAmber else Color.Transparent
    val contentColor = if (selected) PlaceListOnAmber else PlaceListAmber

    Box(
        modifier =
            modifier
                .heightIn(min = 64.dp)
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics {
                    contentDescription =
                        if (selected) {
                            label + " 선택됨"
                        } else {
                            label + " 선택 안 됨"
                        }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LowVisionSearchResultList(
    results: List<SearchResult>,
    onBookmarkClick: (SearchResult) -> Unit,
    onNavigateClick: (SearchResult) -> Unit,
    onBriefingClick: (SearchResult) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cardMetrics = LowVisionSearchLayoutDefaults.resultCardMetrics(maxWidth)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = EumSpacing.medium,
                    top = EumSpacing.medium,
                    end = EumSpacing.medium,
                    bottom = LowVisionSearchLayoutDefaults.resultListBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(LowVisionSearchLayoutDefaults.resultCardGap),
        ) {
            itemsIndexed(
                items = results,
                key = { _, result -> result.placeId },
            ) { index, result ->
                LowVisionSearchResultCard(
                    index = index + 1,
                    name = result.title,
                    address = result.subtitle.ifBlank { null },
                    latitude = result.latitude,
                    longitude = result.longitude,
                    distanceMeters = result.distanceMeters,
                    onBookmarkClick = { onBookmarkClick(result) },
                    onNavigateClick = { onNavigateClick(result) },
                    onContentClick = { onBriefingClick(result) },
                    contentClickDescription = result.lowVisionSearchContentClickDescription(),
                    bookmarkContentDescription = result.lowVisionSearchBookmarkContentDescription(),
                    navigateContentDescription = result.lowVisionSearchNavigateContentDescription(),
                    titleMaxLines = cardMetrics.titleMaxLines,
                    addressMaxLines = cardMetrics.addressMaxLines,
                    modifier =
                        Modifier.heightIn(
                            min = LowVisionSearchLayoutDefaults.resultCardMinHeight,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LowVisionSearchResultCard(
    index: Int,
    name: String,
    address: String?,
    latitude: Double,
    longitude: Double,
    distanceMeters: Int?,
    onBookmarkClick: () -> Unit,
    onNavigateClick: () -> Unit,
    onContentClick: () -> Unit,
    contentClickDescription: String,
    bookmarkContentDescription: String,
    navigateContentDescription: String,
    titleMaxLines: Int,
    addressMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val addressText = lowVisionBriefAddress(address)
    val distanceUiState = resolveSearchResultDistanceUiState(distanceMeters)
    val distanceText =
        distanceUiState?.let { uiState ->
            stringResource(id = uiState.labelResId, uiState.value)
        }
    val placeInfoContentDescription =
        listOfNotNull(
            lowVisionPlaceInfoA11yLabel(
                name = name,
                address = address,
            ),
            distanceText,
        ).joinToString(separator = " ")
    val placeInfoSpeechText =
        listOfNotNull(
            lowVisionPlaceInfoSpeechText(
                name = name,
                address = address,
                latitude = latitude,
                longitude = longitude,
            ),
            distanceText,
        ).joinToString(separator = "\n")

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
                .clickable(role = Role.Button, onClick = onContentClick)
                .semantics {
                    contentDescription = contentClickDescription
                }
                .padding(
                    horizontal = LowVisionSearchLayoutDefaults.cardHorizontalPadding,
                    vertical = LowVisionSearchLayoutDefaults.cardVerticalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(LowVisionSearchLayoutDefaults.cardContentGap),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = LowVisionSearchLayoutDefaults.infoSectionMinHeight),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LowVisionSearchLayoutDefaults.cardHeaderGap),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(LowVisionSearchLayoutDefaults.indexBadgeSize)
                            .background(
                                color = PlaceListAmber,
                                shape = RoundedCornerShape(8.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = index.toString(),
                        fontSize = LowVisionSearchLayoutDefaults.indexFontSize,
                        fontWeight = FontWeight.Black,
                        color = PlaceListOnAmber,
                        lineHeight = LowVisionSearchLayoutDefaults.indexLineHeight,
                        letterSpacing = 0.sp,
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    Text(
                        text = name,
                        fontSize = LowVisionSearchLayoutDefaults.titleFontSize,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = LowVisionSearchLayoutDefaults.titleLineHeight,
                        letterSpacing = 0.sp,
                        maxLines = titleMaxLines,
                    )
                    Text(
                        text = addressText,
                        fontSize = LowVisionSearchLayoutDefaults.addressFontSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = LowVisionSearchLayoutDefaults.addressLineHeight,
                        letterSpacing = 0.sp,
                        maxLines = addressMaxLines,
                    )
                    if (distanceText != null) {
                        Text(
                            text = distanceText,
                            fontSize = LowVisionSearchLayoutDefaults.addressFontSize,
                            fontWeight = FontWeight.Bold,
                            color = PlaceListAmber,
                            lineHeight = LowVisionSearchLayoutDefaults.addressLineHeight,
                            letterSpacing = 0.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        LowVisionPlaceCardSectionDivider()

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = LowVisionSearchLayoutDefaults.actionSectionTopPadding),
            verticalArrangement = Arrangement.spacedBy(LowVisionSearchLayoutDefaults.actionButtonGap),
        ) {
            LowVisionPlaceCardDefaults.actionOrder.forEach { action ->
                when (action) {
                    LowVisionPlaceCardAction.Navigate -> LowVisionSearchActionButton(
                        label = "\uAE38\uCC3E\uAE30",
                        iconRes = LowVisionPlaceCardDefaults.routeIconRes,
                        onClick = onNavigateClick,
                        contentDescription = navigateContentDescription,
                    )

                    LowVisionPlaceCardAction.Bookmark -> LowVisionSearchActionButton(
                        label = "\uC800\uC7A5",
                        iconRes = LowVisionPlaceCardDefaults.saveIconRes,
                        onClick = onBookmarkClick,
                        contentDescription = bookmarkContentDescription,
                    )
                }
            }
        }
    }
}

private fun SearchResult.lowVisionSearchContentClickDescription(): String =
    listOfNotNull(
        title,
        resolveSearchResultDistanceUiState(distanceMeters)?.let { "${distanceMeters}미터 거리" },
        "경로 브리핑. 두 번 탭하면 브리핑 화면으로 이동합니다.",
    ).joinToString(separator = " ")

private fun SearchResult.lowVisionSearchBookmarkContentDescription(): String =
    listOfNotNull(
        title,
        resolveSearchResultDistanceUiState(distanceMeters)?.let { "${distanceMeters}미터 거리" },
        "저장. 저장 후 북마크로 이동합니다.",
    ).joinToString(separator = " ")

private fun SearchResult.lowVisionSearchNavigateContentDescription(): String =
    listOfNotNull(
        title,
        resolveSearchResultDistanceUiState(distanceMeters)?.let { "${distanceMeters}미터 거리" },
        "길찾기. 저시력 안내 화면으로 이동합니다.",
    ).joinToString(separator = " ")

@Composable
private fun LowVisionPlaceCardSectionDivider() {
    Box(
        modifier =
            Modifier
                .padding(top = LowVisionSearchLayoutDefaults.sectionDividerTopPadding)
                .fillMaxWidth(LowVisionSearchLayoutDefaults.sectionDividerWidthFraction)
                .height(LowVisionSearchLayoutDefaults.sectionDividerThickness)
                .background(
                    color = PlaceListAmber,
                    shape = RoundedCornerShape(999.dp),
                ),
    )
}

@Composable
private fun LowVisionSearchActionButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = LowVisionSearchLayoutDefaults.actionButtonHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(PlaceListAmber)
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(color = PlaceListOnAmber),
                    onClick = onClick,
                )
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = PlaceListOnAmber,
            modifier = Modifier.size(LowVisionSearchLayoutDefaults.actionIconSize),
        )
        Spacer(modifier = Modifier.size(LowVisionSearchLayoutDefaults.actionIconTextGap))
        Text(
            text = label,
            fontSize = LowVisionSearchLayoutDefaults.actionLabelFontSize,
            lineHeight = LowVisionSearchLayoutDefaults.actionLabelLineHeight,
            fontWeight = FontWeight.Black,
            color = PlaceListOnAmber,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(
            modifier =
                Modifier.width(
                    LowVisionSearchLayoutDefaults.actionIconSize +
                        LowVisionSearchLayoutDefaults.actionIconTextGap,
                ),
        )
    }
}

@Composable
private fun LowVisionSearchNoResultMessage(
    message: String = LowVisionSearchLayoutDefaults.noResultText,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {
                    contentDescription = LowVisionSearchLayoutDefaults.noResultTalkBackText
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PlaceListSubText,
            textAlign = TextAlign.Center,
        )
    }
}
