package com.ssafy.e102.eumgil.feature.lowvision

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R

private val CompleteBackground = Color(0xFF0D0D0F)
private val CompleteYellow = LowVisionScreenDefaults.brandYellow
private val CompleteBlack = Color(0xFF000000)

internal object LowVisionNavigationCompleteLayoutDefaults {
    val horizontalPadding = 24.dp
    val verticalPadding = 44.dp
    val cardGap = 28.dp
    val cardCornerRadius = 26.dp
    val cardContentPadding = 20.dp
    val saveCardIconTextGap = 28.dp
    val doneCardIconTextGap = 24.dp
    val saveIconSize = 132.dp
    val completeIconSize = 148.dp
    val titleFontSize = 52.sp
    val titleLineHeight = 58.sp
}

internal data class LowVisionNavigationCompleteCard(
    val label: String,
    @DrawableRes val iconRes: Int,
)

internal fun lowVisionNavigationCompleteCards(): List<LowVisionNavigationCompleteCard> =
    listOf(
        LowVisionNavigationCompleteCard(
            label = "\uB3C4\uCC29\uC9C0 \uC800\uC7A5",
            iconRes = R.drawable.ic_voice_location_pin,
        ),
        LowVisionNavigationCompleteCard(
            label = "\uC644\uB8CC",
            iconRes = R.drawable.ic_status_check,
        ),
    )

@Composable
fun LowVisionNavigationCompleteScreen(
    isSaveEnabled: Boolean,
    onSaveClick: () -> Unit,
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = lowVisionNavigationCompleteCards()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(CompleteBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = LowVisionNavigationCompleteLayoutDefaults.horizontalPadding,
                    vertical = LowVisionNavigationCompleteLayoutDefaults.verticalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(LowVisionNavigationCompleteLayoutDefaults.cardGap),
    ) {
        LowVisionCompleteSaveCard(
            card = cards[0],
            enabled = isSaveEnabled,
            onClick = onSaveClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
        LowVisionCompleteDoneCard(
            card = cards[1],
            onClick = onCompleteClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
    }
}

@Composable
private fun LowVisionCompleteSaveCard(
    card: LowVisionNavigationCompleteCard,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.45f

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(LowVisionNavigationCompleteLayoutDefaults.cardCornerRadius))
                .lowVisionButtonSemantics(
                    label = card.label,
                    actionHint = "\uB450 \uBC88 \uD0ED\uD558\uBA74 \uB3C4\uCC29\uC9C0\uB97C \uC800\uC7A5\uD569\uB2C8\uB2E4.",
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(LowVisionNavigationCompleteLayoutDefaults.cardCornerRadius),
        color = CompleteYellow.copy(alpha = alpha),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(LowVisionNavigationCompleteLayoutDefaults.cardContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(LowVisionNavigationCompleteLayoutDefaults.saveIconSize)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CompleteBlack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = card.iconRes),
                    contentDescription = null,
                    tint = CompleteYellow,
                    modifier = Modifier.size(92.dp),
                )
            }
            Spacer(modifier = Modifier.height(LowVisionNavigationCompleteLayoutDefaults.saveCardIconTextGap))
            Text(
                text = card.label,
                color = CompleteBlack,
                fontSize = LowVisionNavigationCompleteLayoutDefaults.titleFontSize,
                lineHeight = LowVisionNavigationCompleteLayoutDefaults.titleLineHeight,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LowVisionCompleteDoneCard(
    card: LowVisionNavigationCompleteCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(LowVisionNavigationCompleteLayoutDefaults.cardCornerRadius))
                .border(
                    width = 2.dp,
                    color = CompleteYellow,
                    shape = RoundedCornerShape(LowVisionNavigationCompleteLayoutDefaults.cardCornerRadius),
                )
                .lowVisionButtonSemantics(
                    label = card.label,
                    actionHint = "\uB450 \uBC88 \uD0ED\uD558\uBA74 \uC800\uC2DC\uB825\uC790 \uD648\uC73C\uB85C \uC774\uB3D9\uD569\uB2C8\uB2E4.",
                )
                .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(LowVisionNavigationCompleteLayoutDefaults.cardCornerRadius),
        color = CompleteBackground,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(LowVisionNavigationCompleteLayoutDefaults.cardContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(6.dp, CompleteYellow),
            ) {
                Icon(
                    painter = painterResource(id = card.iconRes),
                    contentDescription = null,
                    tint = CompleteYellow,
                    modifier =
                        Modifier
                            .size(LowVisionNavigationCompleteLayoutDefaults.completeIconSize)
                            .padding(24.dp),
                )
            }
            Spacer(modifier = Modifier.height(LowVisionNavigationCompleteLayoutDefaults.doneCardIconTextGap))
            Text(
                text = card.label,
                color = CompleteYellow,
                fontSize = LowVisionNavigationCompleteLayoutDefaults.titleFontSize,
                lineHeight = LowVisionNavigationCompleteLayoutDefaults.titleLineHeight,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
