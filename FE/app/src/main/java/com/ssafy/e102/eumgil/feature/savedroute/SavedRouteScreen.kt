package com.ssafy.e102.eumgil.feature.savedroute

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumBorderSubtle
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing
import com.ssafy.e102.eumgil.core.model.RouteOption

@Composable
fun SavedRouteScreen(
    uiState: SavedRouteUiState,
    onAction: (SavedRouteUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedRemovalCount =
        uiState.pendingPlaceRemovalIds.size + uiState.pendingRouteRemovalIds.size
    val hasSelectedTabContent =
        when (uiState.selectedTab) {
            SavedBookmarkTab.PLACE -> uiState.placeContent.places.isNotEmpty()
            SavedBookmarkTab.ROUTE -> uiState.routeContent.routes.isNotEmpty()
        }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SavedRouteTopBar(
                isEditMode = uiState.isEditMode,
                isActionEnabled =
                    if (uiState.isEditMode) {
                        !uiState.isApplyingEditChanges
                    } else {
                        hasSelectedTabContent
                    },
                onActionClick = {
                    onAction(
                        if (uiState.isEditMode) {
                            SavedRouteUiAction.EditDoneClicked
                        } else {
                            SavedRouteUiAction.EditClicked
                        },
                    )
                },
            )
        },
        bottomBar = {
            if (uiState.isEditMode) {
                SavedBookmarkEditBottomBar(
                    selectedCount = selectedRemovalCount,
                    isActionEnabled = !uiState.isApplyingEditChanges && selectedRemovalCount > 0,
                    onDeleteClick = { onAction(SavedRouteUiAction.DeleteSelectedClicked) },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = EumSpacing.medium, vertical = EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            SavedBookmarkTabRow(
                selectedTab = uiState.selectedTab,
                onTabSelected = { tab -> onAction(SavedRouteUiAction.TabSelected(tab)) },
            )
            SavedBookmarkSectionHeader(
                selectedTab = uiState.selectedTab,
                isEditMode = uiState.isEditMode,
                placeCount = uiState.placeContent.places.size,
                routeCount = uiState.routeContent.routes.size,
                selectedCount = selectedRemovalCount,
                placeSortOrder = uiState.placeSortOrder,
                routeSortOrder = uiState.routeSortOrder,
                onSortOrderSelected = { sortOrder ->
                    onAction(SavedRouteUiAction.SortOrderSelected(sortOrder))
                },
            )

            when (uiState.selectedTab) {
                SavedBookmarkTab.PLACE ->
                    SavedPlaceContent(
                        content = uiState.placeContent,
                        isEditMode = uiState.isEditMode,
                        isActionEnabled = !uiState.isApplyingEditChanges,
                        pendingRemovalIds = uiState.pendingPlaceRemovalIds,
                        onAction = onAction,
                        modifier = Modifier.weight(1f),
                    )
                SavedBookmarkTab.ROUTE ->
                    SavedRouteBookmarkContent(
                        content = uiState.routeContent,
                        isEditMode = uiState.isEditMode,
                        isActionEnabled = !uiState.isApplyingEditChanges,
                        pendingRemovalIds = uiState.pendingRouteRemovalIds,
                        onAction = onAction,
                        modifier = Modifier.weight(1f),
                    )
            }
        }
    }
}

@Composable
private fun SavedBookmarkSectionHeader(
    selectedTab: SavedBookmarkTab,
    isEditMode: Boolean,
    placeCount: Int,
    routeCount: Int,
    selectedCount: Int,
    placeSortOrder: SavedBookmarkSortOrder,
    routeSortOrder: SavedBookmarkSortOrder,
    onSortOrderSelected: (SavedBookmarkSortOrder) -> Unit,
) {
    val isSortMenuExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val title =
        when (selectedTab) {
            SavedBookmarkTab.PLACE ->
                stringResource(id = R.string.saved_route_place_count_label)
            SavedBookmarkTab.ROUTE ->
                stringResource(id = R.string.saved_route_route_count_label)
        }
    val count =
        if (isEditMode) {
            selectedCount
        } else {
            when (selectedTab) {
                SavedBookmarkTab.PLACE -> placeCount
                SavedBookmarkTab.ROUTE -> routeCount
            }
        }
    val trailingLabel =
        when {
            isEditMode -> null
            selectedTab == SavedBookmarkTab.PLACE -> sortOrderLabel(placeSortOrder)
            else -> sortOrderLabel(routeSortOrder)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SavedBookmarkSectionHeaderMinHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        trailingLabel?.let { label ->
            Box {
                TextButton(
                    onClick = { isSortMenuExpanded.value = true },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_dropdown),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = isSortMenuExpanded.value,
                    onDismissRequest = { isSortMenuExpanded.value = false },
                    modifier =
                        Modifier
                            .width(SavedBookmarkSortDropdownWidth)
                            .background(MaterialTheme.colorScheme.surface),
                ) {
                    SavedBookmarkSortOrder.entries.forEach { sortOrder ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sortOrderLabel(sortOrder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                isSortMenuExpanded.value = false
                                onSortOrderSelected(sortOrder)
                            },
                            contentPadding = PaddingValues(horizontal = EumSpacing.medium, vertical = 10.dp),
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sortOrderLabel(sortOrder: SavedBookmarkSortOrder): String =
    when (sortOrder) {
        SavedBookmarkSortOrder.NEAREST -> stringResource(id = R.string.saved_route_sort_nearest)
        SavedBookmarkSortOrder.RECENT -> stringResource(id = R.string.saved_route_sort_recent_used)
    }

@Composable
private fun SavedRouteTopBar(
    isEditMode: Boolean,
    isActionEnabled: Boolean,
    onActionClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp),
        ) {
            Text(
                text = stringResource(id = R.string.route_saved_route),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val editBackDescription = stringResource(id = R.string.saved_route_edit_back_a11y)
            val editStartDescription = stringResource(id = R.string.saved_route_edit_mode_inactive_a11y)
            if (isEditMode) {
                TextButton(
                    onClick = onActionClick,
                    enabled = isActionEnabled,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .semantics { contentDescription = editBackDescription },
                    contentPadding = PaddingValues(horizontal = EumSpacing.small, vertical = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_back),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint =
                            if (isActionEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                    )
                }
            } else if (isActionEnabled) {
                TextButton(
                    onClick = onActionClick,
                    enabled = true,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .semantics { contentDescription = editStartDescription },
                    contentPadding = PaddingValues(horizontal = EumSpacing.small, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.saved_route_edit),
                        style = MaterialTheme.typography.labelLarge,
                        color =
                            if (isActionEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedBookmarkTabRow(
    selectedTab: SavedBookmarkTab,
    onTabSelected: (SavedBookmarkTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.full),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
        ) {
            val indicatorWidth = (maxWidth - SavedBookmarkTabButtonGap) / 2
            val targetIndicatorOffset =
                if (selectedTab == SavedBookmarkTab.PLACE) {
                    0.dp
                } else {
                    indicatorWidth + SavedBookmarkTabButtonGap
                }
            val animatedIndicatorOffset by animateDpAsState(
                targetValue = targetIndicatorOffset,
                animationSpec = tween(SavedBookmarkTabButtonAnimationMillis),
                label = "SavedBookmarkTabIndicatorOffset",
            )

            Surface(
                modifier =
                    Modifier
                        .offset(x = animatedIndicatorOffset)
                        .width(indicatorWidth)
                        .height(SavedBookmarkTabHeight),
                shape = RoundedCornerShape(EumRadius.full),
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {}
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SavedBookmarkTabButtonGap),
            ) {
                SavedBookmarkTabButton(
                    label = stringResource(id = R.string.saved_route_tab_place),
                    selected = selectedTab == SavedBookmarkTab.PLACE,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(SavedBookmarkTab.PLACE) },
                )
                SavedBookmarkTabButton(
                    label = stringResource(id = R.string.saved_route_tab_route),
                    selected = selectedTab == SavedBookmarkTab.ROUTE,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(SavedBookmarkTab.ROUTE) },
                )
            }
        }
    }
}

@Composable
private fun SavedBookmarkTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabSelectedStateDescription = stringResource(id = R.string.a11y_tab_selected)
    val tabUnselectedStateDescription = stringResource(id = R.string.a11y_tab_unselected)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(EumRadius.full))
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                    stateDescription =
                        if (selected) {
                            tabSelectedStateDescription
                        } else {
                            tabUnselectedStateDescription
                        }
                }
                .clickable(
                    role = Role.Tab,
                    onClick = onClick,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(SavedBookmarkTabHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun SavedPlaceContent(
    content: SavedPlaceContentUiState,
    isEditMode: Boolean,
    isActionEnabled: Boolean,
    pendingRemovalIds: Set<String>,
    onAction: (SavedRouteUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content.screenState) {
        SavedBookmarkContentState.LOADING ->
            SavedBookmarkLoadingState(
                title = stringResource(id = R.string.saved_route_place_loading_title),
                description = stringResource(id = R.string.saved_route_place_loading_description),
                modifier = modifier.fillMaxWidth(),
            )
        SavedBookmarkContentState.EMPTY ->
            SavedBookmarkEmptyState(
                title = stringResource(id = R.string.saved_route_place_empty_title),
                description = stringResource(id = R.string.saved_route_place_empty_description),
                primaryActionLabel = stringResource(id = R.string.saved_route_explore_map),
                onPrimaryActionClick = { onAction(SavedRouteUiAction.ExploreMapClicked) },
                modifier = modifier.fillMaxWidth(),
            )
        SavedBookmarkContentState.ERROR ->
            SavedBookmarkStateCard(
                title = stringResource(id = R.string.saved_route_place_error_title),
                description = content.errorMessage ?: stringResource(id = R.string.saved_route_error_description),
                iconRes = R.drawable.ic_status_warning,
                primaryActionLabel = stringResource(id = R.string.saved_route_retry),
                onPrimaryActionClick = { onAction(SavedRouteUiAction.RetryClicked) },
                secondaryActionLabel = stringResource(id = R.string.saved_route_explore_map),
                onSecondaryActionClick = { onAction(SavedRouteUiAction.ExploreMapClicked) },
                isError = true,
                modifier = modifier.fillMaxWidth(),
            )
        SavedBookmarkContentState.CONTENT ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = SavedBookmarkListBottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            ) {
                content.errorMessage?.let { message ->
                    item {
                        SavedRouteInlineMessage(message = message)
                    }
                }
                items(
                    items = content.places,
                    key = SavedPlaceUiModel::placeId,
                ) { place ->
                    SavedPlaceListItem(
                        place = place,
                        isEditMode = isEditMode,
                        isPendingRemoval = place.placeId in pendingRemovalIds,
                        isActionEnabled = isActionEnabled,
                        onPlaceClick =
                            if (isEditMode) {
                                {
                                    onAction(SavedRouteUiAction.PlaceDeleteClicked(placeId = place.placeId))
                                }
                            } else {
                                {
                                    onAction(SavedRouteUiAction.PlaceClicked(placeId = place.placeId))
                                }
                            },
                        onPrimaryActionClick = {
                            onAction(
                                if (isEditMode) {
                                    SavedRouteUiAction.PlaceDeleteClicked(placeId = place.placeId)
                                } else {
                                    SavedRouteUiAction.PlaceRouteGuideClicked(placeId = place.placeId)
                                },
                            )
                        },
                        onDetailActionClick = { onAction(SavedRouteUiAction.PlaceClicked(placeId = place.placeId)) },
                    )
                }
            }
    }
}

@Composable
private fun SavedRouteBookmarkContent(
    content: SavedRouteBookmarkContentUiState,
    isEditMode: Boolean,
    isActionEnabled: Boolean,
    pendingRemovalIds: Set<String>,
    onAction: (SavedRouteUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (content.screenState) {
        SavedBookmarkContentState.LOADING ->
            SavedBookmarkLoadingState(
                title = stringResource(id = R.string.saved_route_route_loading_title),
                description = stringResource(id = R.string.saved_route_route_loading_description),
                modifier = modifier.fillMaxWidth(),
            )
        SavedBookmarkContentState.EMPTY ->
            SavedBookmarkEmptyState(
                title = stringResource(id = R.string.saved_route_route_empty_title),
                description = stringResource(id = R.string.saved_route_route_empty_description),
                primaryActionLabel = stringResource(id = R.string.saved_route_route_setting_action),
                onPrimaryActionClick = { onAction(SavedRouteUiAction.RouteSettingClicked) },
                modifier = modifier.fillMaxWidth(),
            )
        SavedBookmarkContentState.ERROR ->
            SavedBookmarkStateCard(
                title = stringResource(id = R.string.saved_route_route_error_title),
                description = content.errorMessage ?: stringResource(id = R.string.saved_route_route_error_description),
                iconRes = R.drawable.ic_status_warning,
                primaryActionLabel = stringResource(id = R.string.saved_route_retry),
                onPrimaryActionClick = { onAction(SavedRouteUiAction.RetryClicked) },
                secondaryActionLabel = stringResource(id = R.string.saved_route_route_setting_action),
                onSecondaryActionClick = { onAction(SavedRouteUiAction.RouteSettingClicked) },
                isError = true,
                modifier = modifier.fillMaxWidth(),
            )
        SavedBookmarkContentState.CONTENT ->
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = SavedBookmarkListBottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            ) {
                content.errorMessage?.let { message ->
                    item {
                        SavedRouteInlineMessage(message = message)
                    }
                }
                items(
                    items = content.routes,
                    key = SavedRouteBookmarkUiModel::bookmarkId,
                ) { routeBookmark ->
                    SavedRouteBookmarkListItem(
                        routeBookmark = routeBookmark,
                        isEditMode = isEditMode,
                        isPendingRemoval = routeBookmark.bookmarkId in pendingRemovalIds,
                        isActionEnabled = isActionEnabled,
                        onRouteClick =
                            if (isEditMode) {
                                {
                                    onAction(SavedRouteUiAction.RouteDeleteClicked(bookmarkId = routeBookmark.bookmarkId))
                                }
                            } else if (!isActionEnabled) {
                                null
                            } else {
                                {
                                    onAction(SavedRouteUiAction.RouteClicked(bookmarkId = routeBookmark.bookmarkId))
                                }
                            },
                        onPrimaryActionClick = {
                            onAction(
                                if (isEditMode) {
                                    SavedRouteUiAction.RouteDeleteClicked(bookmarkId = routeBookmark.bookmarkId)
                                } else {
                                    SavedRouteUiAction.RouteGuideClicked(bookmarkId = routeBookmark.bookmarkId)
                                },
                            )
                        },
                    )
                }
            }
    }
}

@Composable
private fun SavedBookmarkEmptyState(
    title: String,
    description: String,
    primaryActionLabel: String,
    onPrimaryActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = EumSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            NoRippleSavedRouteNavigationButton(
                onClick = onPrimaryActionClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SavedBookmarkPrimaryCtaHeight),
                fullWidthContent = true,
                shape = RoundedCornerShape(EumRadius.small),
            ) {
                Text(
                    text = primaryActionLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SavedBookmarkLoadingState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = EumSpacing.large, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SavedBookmarkStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    primaryActionLabel: String? = null,
    onPrimaryActionClick: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryActionClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    isError: Boolean = false,
) {
    val borderColor =
        if (isError) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.36f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    val iconTint =
        if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }

    Surface(
        modifier =
            modifier.semantics {
                liveRegion =
                    when {
                        isError -> LiveRegionMode.Assertive
                        isLoading -> LiveRegionMode.Polite
                        else -> LiveRegionMode.Polite
                    }
            },
        shape = RoundedCornerShape(SavedBookmarkCardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.52f)),
        shadowElevation = SavedBookmarkCardElevation,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = EumSpacing.large, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                iconRes != null ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .padding(EumSpacing.small)
                                    .size(44.dp),
                            tint = iconTint,
                        )
                    }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                textAlign = TextAlign.Center,
            )
            SavedBookmarkStateActions(
                primaryActionLabel = primaryActionLabel,
                onPrimaryActionClick = onPrimaryActionClick,
                secondaryActionLabel = secondaryActionLabel,
                onSecondaryActionClick = onSecondaryActionClick,
            )
        }
    }
}

@Composable
private fun SavedBookmarkStateActions(
    primaryActionLabel: String?,
    onPrimaryActionClick: (() -> Unit)?,
    secondaryActionLabel: String?,
    onSecondaryActionClick: (() -> Unit)?,
) {
    if (primaryActionLabel == null || onPrimaryActionClick == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
    ) {
        NoRippleSavedRouteNavigationButton(
            onClick = onPrimaryActionClick,
            modifier = Modifier.weight(1f),
            fullWidthContent = true,
        ) {
            Text(text = primaryActionLabel)
        }
        if (secondaryActionLabel != null && onSecondaryActionClick != null) {
            NoRippleSavedRouteNavigationButton(
                onClick = onSecondaryActionClick,
                modifier = Modifier.weight(1f),
                isOutlined = true,
                fullWidthContent = true,
            ) {
                Text(text = secondaryActionLabel)
            }
        }
    }
}

@Composable
private fun NoRippleSavedRouteNavigationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isOutlined: Boolean = false,
    fullWidthContent: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(EumRadius.full),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor =
        when {
            !enabled && isOutlined -> MaterialTheme.colorScheme.surface
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            isOutlined -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.primary
        }
    val contentColor =
        when {
            !enabled && isOutlined -> MaterialTheme.colorScheme.primary.copy(alpha = 0.56f)
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            isOutlined -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onPrimary
        }
    val border =
        if (isOutlined) {
            BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.75f))
        } else {
            null
        }

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
    ) {
        Row(
            modifier =
                (if (fullWidthContent) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                })
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun SavedRouteInlineMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EumRadius.medium),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.34f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(EumSpacing.medium),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun SavedPlaceListItem(
    place: SavedPlaceUiModel,
    isEditMode: Boolean,
    isPendingRemoval: Boolean,
    isActionEnabled: Boolean,
    onPlaceClick: (() -> Unit)?,
    onPrimaryActionClick: () -> Unit,
    onDetailActionClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val accessibilityDescription =
        stringResource(
            id = R.string.saved_route_place_a11y_description,
            place.name,
            place.address ?: stringResource(id = R.string.saved_route_address_empty),
        )
    val border =
        if (isPendingRemoval) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f))
        } else {
            BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f))
        }
    val containerColor =
        if (isPendingRemoval) {
            SavedBookmarkPendingDeleteContainerColor
        } else {
            MaterialTheme.colorScheme.surface
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SavedBookmarkCardCornerRadius),
        color = containerColor,
        border = border,
        shadowElevation = SavedBookmarkCardElevation,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(SavedBookmarkCardContentPadding),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (onPlaceClick != null) {
                                    Modifier.clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        role = Role.Button,
                                        onClick = onPlaceClick,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .semantics {
                                contentDescription = accessibilityDescription
                            },
                    horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SavedPlaceCategoryIconTile(category = place.category)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SavedPlaceCategoryPill(label = savedBookmarkPlaceCategoryLabel(category = place.category))
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleMedium.copy(lineHeight = SavedBookmarkPlaceNameLineHeight),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = SavedBookmarkPrimaryTextMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = place.address ?: stringResource(id = R.string.saved_route_address_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    bookmarkDistanceLabel(place.distanceMeters)?.let { distanceLabel ->
                        SavedBookmarkDistanceLabel(label = distanceLabel)
                    }
                }
            }
            if (!isEditMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
                ) {
                    SavedBookmarkDetailActionButton(
                        enabled = isActionEnabled,
                        onClick = onDetailActionClick,
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = SavedBookmarkPrimaryCtaHeight),
                    )
                    SavedBookmarkPrimaryActionButton(
                        enabled = isActionEnabled,
                        onClick = onPrimaryActionClick,
                        accessibilityContext = place.name,
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = SavedBookmarkPrimaryCtaHeight),
                        isOutlined = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedPlaceCategoryIconTile(category: String?) {
    Surface(
        modifier = Modifier.size(SavedBookmarkPlaceIconTileSize),
        shape = RoundedCornerShape(SavedBookmarkPlaceIconTileCornerRadius),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(id = savedBookmarkPlaceCategoryIconRes(category)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(SavedBookmarkCategoryIconSize),
            )
        }
    }
}

@Composable
private fun SavedPlaceCategoryPill(label: String) {
    Surface(
        shape = RoundedCornerShape(EumRadius.full),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = EumSpacing.small, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@DrawableRes
private fun savedBookmarkPlaceCategoryIconRes(category: String?): Int =
    when (category) {
        "TOILET" -> R.drawable.ic_user_wheelchair_compact
        "ELEVATOR" -> R.drawable.ic_place_elevator
        "CHARGING_STATION" -> R.drawable.ic_place_charging_station
        "FOOD_CAFE" -> R.drawable.ic_place_food_cafe
        "TOURIST_SPOT" -> R.drawable.ic_place_tourist_spot
        "ACCOMMODATION" -> R.drawable.ic_place_accommodation
        "HEALTHCARE" -> R.drawable.ic_place_healthcare
        "WELFARE" -> R.drawable.ic_place_welfare
        "PUBLIC_OFFICE" -> R.drawable.ic_place_public_office
        "BRAILLE_BLOCK" -> R.drawable.ic_route_tactile_blocks
        "RESTAURANT" -> R.drawable.ic_place_restaurant
        "TOURIST_ATTRACTION" -> R.drawable.ic_place_tourist_spot
        "OTHER", null -> R.drawable.ic_map_selected_pin_blue
        else -> R.drawable.ic_map_selected_pin_blue
    }

@Composable
private fun savedBookmarkPlaceCategoryLabel(category: String?): String =
    when (category) {
        "FOOD_CAFE" -> "식당·카페"
        "TOURIST_SPOT" -> "무장애 관광지"
        "ACCOMMODATION" -> "숙박"
        "HEALTHCARE" -> "병원"
        "WELFARE" -> "복지관"
        "PUBLIC_OFFICE" -> "관공서"
        "RESTAURANT" -> stringResource(id = R.string.map_filter_category_restaurant)
        "TOURIST_ATTRACTION" -> stringResource(id = R.string.map_filter_category_tourist_attraction)
        "TOILET" -> stringResource(id = R.string.map_filter_category_toilet)
        "ELEVATOR" -> stringResource(id = R.string.map_filter_category_elevator)
        "CHARGING_STATION" -> stringResource(id = R.string.map_filter_category_charging_station)
        "BRAILLE_BLOCK" -> stringResource(id = R.string.map_filter_category_braille_block)
        else -> stringResource(id = R.string.map_filter_category_other)
    }

@Composable
private fun SavedBookmarkDistanceLabel(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_route_detail_row_marker),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SavedBookmarkDetailActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    NoRippleSavedRouteNavigationButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isOutlined = true,
        shape = RoundedCornerShape(EumRadius.small),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.saved_route_place_detail),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            painter = painterResource(id = R.drawable.ic_route_card_chevron),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SavedRouteBookmarkListItem(
    routeBookmark: SavedRouteBookmarkUiModel,
    isEditMode: Boolean,
    isPendingRemoval: Boolean,
    isActionEnabled: Boolean,
    onRouteClick: (() -> Unit)?,
    onPrimaryActionClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val accessibilityDescription =
        stringResource(
            id = R.string.saved_route_route_a11y_description,
            routeBookmark.routeName,
            routeBookmark.startLabel,
            routeBookmark.endLabel,
        )
    val border =
        if (isPendingRemoval) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f))
        } else {
            BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f))
        }
    val containerColor =
        if (isPendingRemoval) {
            SavedBookmarkPendingDeleteContainerColor
        } else {
            MaterialTheme.colorScheme.surface
        }
    val routeOptionLabel =
        routeOptionCompactLabel(
            rawLabel = routeBookmark.routeOptionLabel,
            fallback = routeBookmark.routeOption,
        )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SavedBookmarkCardCornerRadius),
        color = containerColor,
        border = border,
        shadowElevation = SavedBookmarkCardElevation,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(SavedBookmarkCardContentPadding),
            verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (onRouteClick != null) {
                                Modifier.clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onRouteClick,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .semantics {
                            contentDescription = accessibilityDescription
                        },
                horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                    verticalAlignment = Alignment.Top,
                ) {
                    SavedRoutePathDecoration(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .padding(vertical = SavedBookmarkRoutePathVerticalPadding),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(SavedBookmarkRouteWaypointGap),
                    ) {
                        SavedRouteWaypointInfoRow(
                            label = stringResource(id = R.string.route_setting_origin_label),
                            value = routeBookmark.startLabel,
                            accentColor = MaterialTheme.colorScheme.primary,
                        )
                        SavedRouteWaypointInfoRow(
                            label = stringResource(id = R.string.route_setting_destination_label),
                            value = routeBookmark.endLabel,
                            accentColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            SavedRouteMetaRow(
                distanceLabel = bookmarkDistanceLabel(routeBookmark.distanceMeters),
                transportModeLabel = transportModeLabel(routeBookmark.transportMode),
                routeOptionLabel = routeOptionLabel,
                showWalkIcon = routeBookmark.isWalkTransportMode(),
            )
            if (!isEditMode) {
                SavedBookmarkPrimaryActionButton(
                    enabled = isActionEnabled,
                    onClick = onPrimaryActionClick,
                    accessibilityContext = routeBookmark.routeName,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = SavedBookmarkPrimaryCtaHeight),
                    isOutlined = false,
                )
            }
        }
    }
}

@Composable
private fun SavedBookmarkEditBottomBar(
    selectedCount: Int,
    isActionEnabled: Boolean,
    onDeleteClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 3.dp,
    ) {
        Button(
            onClick = onDeleteClick,
            enabled = isActionEnabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = EumSpacing.medium,
                        end = EumSpacing.medium,
                        top = EumSpacing.small,
                        bottom = EumSpacing.small,
                    )
                    .heightIn(min = 56.dp),
            shape = RoundedCornerShape(EumRadius.full),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.32f),
                    disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.70f),
                ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_delete),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(EumSpacing.xSmall))
            Text(
                text = stringResource(id = R.string.saved_route_delete_selected, selectedCount),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SavedBookmarkPrimaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accessibilityContext: String? = null,
    isOutlined: Boolean = true,
) {
    val navigationButtonShape = RoundedCornerShape(EumRadius.small)
    val accessibilityLabel =
        accessibilityContext?.let { context ->
            stringResource(id = R.string.saved_route_action_start_a11y, context)
        }
    val sharedModifier =
        if (accessibilityLabel != null) {
            modifier.semantics { contentDescription = accessibilityLabel }
        } else {
            modifier
        }
    NoRippleSavedRouteNavigationButton(
        onClick = onClick,
        modifier =
            sharedModifier.heightIn(min = 38.dp),
        enabled = enabled,
        isOutlined = isOutlined,
        shape = navigationButtonShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_route_start_navigation_button),
            contentDescription = null,
            tint =
                if (isOutlined) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(id = R.string.saved_route_start_route),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SavedRoutePathDecoration(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(SavedBookmarkRoutePathDotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Box(
            modifier =
                Modifier
                    .size(SavedBookmarkRoutePathDotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
        )
    }
}

@Composable
private fun SavedRouteMetaRow(
    distanceLabel: String?,
    transportModeLabel: String?,
    routeOptionLabel: String?,
    showWalkIcon: Boolean,
) {
    val metaText =
        listOfNotNull(distanceLabel, transportModeLabel, routeOptionLabel)
            .joinToString(separator = " · ")
    if (metaText.isBlank()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showWalkIcon) {
            Icon(
                painter = painterResource(id = R.drawable.ic_route_walk),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = metaText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SavedRouteWaypointInfoRow(
    label: String,
    value: String,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(EumSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(EumRadius.full),
            color = accentColor.copy(alpha = 0.10f),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = EumSpacing.small, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = SavedBookmarkWaypointValueLineHeight),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = SavedBookmarkWaypointValueMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun routeOptionCompactLabel(
    rawLabel: String?,
    fallback: RouteOption,
): String? =
    when (rawLabel?.trim()?.uppercase()) {
        "SAFE" -> stringResource(id = R.string.saved_route_route_option_safe_compact)
        "SHORTEST" -> stringResource(id = R.string.saved_route_route_option_fast_compact)
        "안전", "안전한 길" -> stringResource(id = R.string.saved_route_route_option_safe_compact)
        "빠른", "빠른 길", "최단거리", "최단 거리" -> stringResource(id = R.string.saved_route_route_option_fast_compact)
        "RECOMMENDED", "MIN_TRANSFER", "MIN_WALK", "추천", "환승 최소", "도보 최소", "무단차 우선" -> null
        null, "" ->
            when (fallback) {
                RouteOption.SAFE -> stringResource(id = R.string.saved_route_route_option_safe_compact)
                RouteOption.SHORTEST -> stringResource(id = R.string.saved_route_route_option_fast_compact)
                else -> null
            }
        else -> null
    }

@Composable
private fun bookmarkDistanceLabel(distanceMeters: Int?): String? =
    when {
        distanceMeters == null -> null
        distanceMeters < 1000 -> stringResource(id = R.string.saved_route_distance_meters, distanceMeters)
        else -> stringResource(id = R.string.saved_route_distance_kilometers, distanceMeters / 1000f)
    }

@Composable
private fun transportModeLabel(transportMode: String?): String? =
    when (transportMode?.uppercase()) {
        "WALK" -> stringResource(id = R.string.saved_route_transport_mode_walk)
        "PUBLIC_TRANSIT" -> stringResource(id = R.string.saved_route_transport_mode_transit)
        null, "" -> null
        else -> transportMode
    }

private fun SavedRouteBookmarkUiModel.isWalkTransportMode(): Boolean =
    transportMode?.uppercase() == "WALK" || transportMode == "도보"

private const val SavedBookmarkPrimaryTextMaxLines = 2
private const val SavedBookmarkWaypointValueMaxLines = 1
private val SavedBookmarkPlaceNameLineHeight = 20.sp
private val SavedBookmarkWaypointValueLineHeight = 20.sp
private val SavedBookmarkCategoryIconSize = 40.dp
private val SavedBookmarkCardCornerRadius = 24.dp
private val SavedBookmarkCardContentPadding = 18.dp
private val SavedBookmarkCardElevation = 0.dp
private val SavedBookmarkSectionHeaderMinHeight = 48.dp
private val SavedBookmarkTabButtonGap = 4.dp
private const val SavedBookmarkTabButtonAnimationMillis = 220
private val SavedBookmarkPendingDeleteContainerColor = Color(0xFFFFF1F2)
private val SavedBookmarkPlaceIconTileSize = 72.dp
private val SavedBookmarkPlaceIconTileCornerRadius = 20.dp
private val SavedBookmarkPrimaryCtaHeight = 52.dp
private val SavedBookmarkTabHeight = 48.dp
private val SavedBookmarkRoutePathVerticalPadding = 7.dp
private val SavedBookmarkRouteWaypointGap = 8.dp
private val SavedBookmarkRoutePathDotSize = 14.dp
private val SavedBookmarkSortDropdownWidth = 132.dp
private val SavedBookmarkListBottomContentPadding = 80.dp
