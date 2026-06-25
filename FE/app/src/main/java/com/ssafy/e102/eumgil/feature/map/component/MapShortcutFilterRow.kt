package com.ssafy.e102.eumgil.feature.map.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterChipState
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterKey
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterRowState

@Composable
fun MapShortcutFilterRow(
    state: MapShortcutFilterRowState,
    onChipClick: (MapShortcutFilterKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = true,
    ) {
        items(
            items = state.chips,
            key = { chip -> chip.key.name },
        ) { chip ->
            ShortcutFilterChip(
                chip = chip,
                onClick = { onChipClick(chip.key) },
            )
        }
    }
}

@Composable
private fun ShortcutFilterChip(
    chip: MapShortcutFilterChipState,
    onClick: () -> Unit,
) {
    val selected = chip.isSelected
    val enabled = chip.isEnabled
    val selectionStateDescription =
        stringResource(
            id =
                if (selected) {
                    R.string.a11y_option_selected
                } else {
                    R.string.a11y_option_unselected
                },
        )
    val containerColor =
        when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            enabled -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val textColor =
        when {
            selected -> MaterialTheme.colorScheme.primary
            enabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val borderColor =
        when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        }
    val iconTint = if (enabled) EumPrimary600 else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                stateDescription = selectionStateDescription
            },
        enabled = true,
        shape = RoundedCornerShape(EumRadius.scaleS),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (selected) 4.dp else 2.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 38.dp)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = shortcutFilterIcon(chip.key)),
                contentDescription = null,
                modifier = Modifier.size(shortcutFilterIconSizeDp(chip.key).dp),
                tint = iconTint,
            )
            Text(
                text = shortcutFilterLabel(chip.key),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = textColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun shortcutFilterLabel(key: MapShortcutFilterKey): String =
    when (key) {
        MapShortcutFilterKey.TOILET -> "장애인 화장실"
        MapShortcutFilterKey.ELEVATOR -> "엘리베이터"
        MapShortcutFilterKey.CHARGING_STATION -> "전동보장구 충전"
        MapShortcutFilterKey.FOOD_CAFE -> "식당·카페"
        MapShortcutFilterKey.TOURIST_SPOT -> "무장애 관광지"
        MapShortcutFilterKey.ACCOMMODATION -> "숙박"
        MapShortcutFilterKey.HEALTHCARE -> "병원"
        MapShortcutFilterKey.WELFARE -> "복지관"
        MapShortcutFilterKey.PUBLIC_OFFICE -> "관공서"
    }

@DrawableRes
private fun shortcutFilterIcon(key: MapShortcutFilterKey): Int =
    when (key) {
        MapShortcutFilterKey.TOILET -> R.drawable.ic_accessibility_tag_accessible_toilet
        MapShortcutFilterKey.ELEVATOR -> R.drawable.ic_accessibility_tag_elevator
        MapShortcutFilterKey.CHARGING_STATION -> R.drawable.ic_accessibility_tag_charging_station
        MapShortcutFilterKey.FOOD_CAFE -> R.drawable.ic_place_food_cafe
        MapShortcutFilterKey.TOURIST_SPOT -> R.drawable.ic_place_tourist_spot
        MapShortcutFilterKey.ACCOMMODATION -> R.drawable.ic_accessibility_tag_accessible_room
        MapShortcutFilterKey.HEALTHCARE -> R.drawable.ic_place_healthcare
        MapShortcutFilterKey.WELFARE -> R.drawable.ic_place_welfare
        MapShortcutFilterKey.PUBLIC_OFFICE -> R.drawable.ic_place_public_office
    }

internal fun shortcutFilterIconSizeDp(key: MapShortcutFilterKey): Int =
    when (key) {
        MapShortcutFilterKey.TOILET -> 19
        MapShortcutFilterKey.ELEVATOR -> 18
        MapShortcutFilterKey.TOURIST_SPOT,
        MapShortcutFilterKey.ACCOMMODATION,
        MapShortcutFilterKey.WELFARE,
        MapShortcutFilterKey.PUBLIC_OFFICE,
        -> 20
        else -> 16
    }
