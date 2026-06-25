package com.ssafy.e102.eumgil.core.designsystem.component.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.app.navigation.TopLevelDestination
import com.ssafy.e102.eumgil.core.designsystem.theme.EumPrimary600

@Composable
fun EumTopLevelTabBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String?,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedState = stringResource(id = R.string.a11y_tab_selected)
    val unselectedState = stringResource(id = R.string.a11y_tab_unselected)
    val layoutSpec = topLevelTabBarLayoutSpec()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .selectableGroup()
                .padding(horizontal = layoutSpec.containerHorizontalPaddingDp.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            destinations.forEach { destination ->
                val selected = destination.route.route == currentRoute
                val contentColor = topLevelTabContentColor(selected = selected)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onDestinationSelected(destination) },
                            role = Role.Tab,
                        )
                        .semantics {
                            stateDescription = if (selected) {
                                selectedState
                            } else {
                                unselectedState
                            }
                        }
                        .testTag("tab_${destination.route.route}")
                        .padding(vertical = layoutSpec.itemVerticalPaddingDp.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(layoutSpec.itemSpacingDp.dp),
                ) {
                    Image(
                        painter = painterResource(id = topLevelTabIconRes(destination, selected)),
                        contentDescription = null,
                        modifier = Modifier.size(destination.iconSizeDp.dp),
                        colorFilter = ColorFilter.tint(contentColor),
                    )
                    Text(
                        text = stringResource(id = destination.labelRes),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = contentColor,
                    )
                }
            }
        }
    }
}

internal data class TopLevelTabBarLayoutSpec(
    val containerHorizontalPaddingDp: Int,
    val itemVerticalPaddingDp: Int,
    val itemSpacingDp: Int,
)

internal fun topLevelTabBarLayoutSpec(): TopLevelTabBarLayoutSpec =
    TopLevelTabBarLayoutSpec(
        containerHorizontalPaddingDp = 12,
        itemVerticalPaddingDp = 8,
        itemSpacingDp = 2,
    )

internal fun topLevelTabIconRes(
    destination: TopLevelDestination,
    selected: Boolean,
): Int =
    if (selected) {
        destination.selectedIconRes
    } else {
        destination.iconRes
    }

internal fun topLevelTabContentColor(selected: Boolean): Color =
    if (selected) {
        EumPrimary600
    } else {
        Color(0xFF6B7280)
    }
