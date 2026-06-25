package com.ssafy.e102.eumgil.feature.lowvision

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.feature.lowvision.component.LowVisionBottomNav

private val LowVisionCategoryYellow = LowVisionScreenDefaults.brandYellow
private val LowVisionCategoryBackground = Color(0xFF0D0D0F)

internal object LowVisionCategoryLayoutDefaults {
    const val columnCount = 2
    const val rowCount = 2
    const val cardColumnWeight = 1f
    const val cardContentBudgetHeightDp = 240f
    const val centersHeaderText = true
    const val showsBackButton = false
    val headerGridGap = LowVisionScreenDefaults.headerGap
    val gridGap = 24.dp
    val cardMinHeight = 286.dp
    val headerFontSize = LowVisionScreenDefaults.headerFontSize
    val headerLineHeight = LowVisionScreenDefaults.headerLineHeight
    val scrollBottomSpacer = 112.dp
    val cardCornerRadius = 18.dp
    val cardBorderWidth = 3.dp
    val cardHorizontalPadding = 16.dp
    val cardVerticalPadding = 24.dp
    val cardIconSize = 96.dp
    val cardIconTextGap = 28.dp
    val cardLabelFontSize = 38.sp
    val cardLabelLineHeight = 46.sp
    const val cardLabelMaxLines = 2
}

internal fun lowVisionCategoryDisplayLabel(label: String): String =
    when (val trimmedLabel = label.trim()) {
        "\uC219\uBC15\uC2DC\uC124" -> "\uC219\uBC15\n\uC2DC\uC124"
        else -> trimmedLabel.replace(Regex("\\s+"), "\n")
    }

internal fun lowVisionCategoryResultA11yHint(label: String): String =
    "${label.trim()}에 대한 결과를 안내합니다."

internal fun lowVisionCategorySelectionStateText(isSelected: Boolean): String =
    if (isSelected) "선택됨" else "선택 안 됨"

internal val lowVisionCategoryOptions =
    listOf(
        LowVisionCategoryOption(
            label = "\uC74C\uC2DD\uC810",
            selectionA11yDescription = "이용할 수 있는 음식점과 카페를 안내합니다",
            iconRes = R.drawable.ic_lowvision_category_restaurant,
        ),
        LowVisionCategoryOption(
            label = "\uAD00\uAD11\uC9C0",
            selectionA11yDescription = "이용할 수 있는 관광지를 안내합니다",
            iconRes = R.drawable.ic_lowvision_category_tourism,
        ),
        LowVisionCategoryOption(
            label = "\uC219\uBC15\uC2DC\uC124",
            selectionA11yDescription = "이용할 수 있는 숙박시설을 안내합니다",
            iconRes = R.drawable.ic_place_accommodation,
        ),
        LowVisionCategoryOption(
            label = "\uBCD1\uC6D0",
            selectionA11yDescription = "이용할 수 있는 병원과 의료시설을 안내합니다",
            iconRes = R.drawable.ic_place_healthcare,
        ),
        LowVisionCategoryOption(
            label = "\uBCF5\uC9C0\uAD00",
            selectionA11yDescription = "이용할 수 있는 복지시설을 안내합니다",
            iconRes = R.drawable.ic_place_welfare,
        ),
        LowVisionCategoryOption(
            label = "\uAD00\uACF5\uC11C",
            selectionA11yDescription = "이용할 수 있는 관공서를 안내합니다",
            iconRes = R.drawable.ic_place_public_office,
        ),
    )

@Composable
fun LowVisionCategoryScreen(
    onCategorySelected: (String) -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryLabel by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(LowVisionCategoryBackground),
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
            verticalArrangement = Arrangement.spacedBy(LowVisionCategoryLayoutDefaults.headerGridGap),
        ) {
            LowVisionCategoryHeader()
            LowVisionCategoryGrid(
                selectedCategoryLabel = selectedCategoryLabel,
                onCategorySelected = { categoryLabel ->
                    selectedCategoryLabel = categoryLabel
                    onCategorySelected(categoryLabel)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
        }

        LowVisionBottomNav(
            selectedTab = LowVisionBottomTab.CATEGORY,
            onTabSelected = onTabSelected,
        )
    }
}

@Composable
private fun LowVisionCategoryHeader() {
    Text(
        text = "\uCE74\uD14C\uACE0\uB9AC",
        modifier = Modifier.fillMaxWidth(),
        color = LowVisionCategoryYellow,
        fontSize = LowVisionCategoryLayoutDefaults.headerFontSize,
        lineHeight = LowVisionCategoryLayoutDefaults.headerLineHeight,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.sp,
        textAlign = TextAlign.Center,
    )
}
@Composable
private fun LowVisionCategoryGrid(
    selectedCategoryLabel: String?,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val cardHeight =
            ((maxHeight - LowVisionCategoryLayoutDefaults.gridGap) / LowVisionCategoryLayoutDefaults.rowCount)
                .coerceAtLeast(LowVisionCategoryLayoutDefaults.cardMinHeight)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LowVisionCategoryLayoutDefaults.gridGap),
        ) {
            lowVisionCategoryOptions
                .chunked(LowVisionCategoryLayoutDefaults.columnCount)
                .forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LowVisionCategoryLayoutDefaults.gridGap),
                    ) {
                        rowOptions.forEach { option ->
                            LowVisionCategoryCard(
                                option = option,
                                isSelected = option.label == selectedCategoryLabel,
                                onClick = { onCategorySelected(option.label) },
                                modifier =
                                    Modifier
                                        .weight(LowVisionCategoryLayoutDefaults.cardColumnWeight)
                                        .height(cardHeight),
                            )
                        }
                    }
                }
            Spacer(modifier = Modifier.height(LowVisionCategoryLayoutDefaults.scrollBottomSpacer))
        }
    }
}

@Composable
private fun LowVisionCategoryCard(
    option: LowVisionCategoryOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(LowVisionCategoryLayoutDefaults.cardCornerRadius))
                .border(
                    width = LowVisionCategoryLayoutDefaults.cardBorderWidth,
                    color = LowVisionCategoryYellow,
                    shape = RoundedCornerShape(LowVisionCategoryLayoutDefaults.cardCornerRadius),
                )
                .background(LowVisionCategoryBackground)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = option.resultA11yHint(isSelected = isSelected)
                }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    horizontal = LowVisionCategoryLayoutDefaults.cardHorizontalPadding,
                    vertical = LowVisionCategoryLayoutDefaults.cardVerticalPadding,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = option.iconRes),
            contentDescription = null,
            tint = LowVisionCategoryYellow,
            modifier = Modifier.size(LowVisionCategoryLayoutDefaults.cardIconSize),
        )
        Spacer(modifier = Modifier.height(LowVisionCategoryLayoutDefaults.cardIconTextGap))
        Text(
            text = lowVisionCategoryDisplayLabel(option.label),
            modifier = Modifier.fillMaxWidth(),
            color = LowVisionCategoryYellow,
            fontSize = LowVisionCategoryLayoutDefaults.cardLabelFontSize,
            lineHeight = LowVisionCategoryLayoutDefaults.cardLabelLineHeight,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = LowVisionCategoryLayoutDefaults.cardLabelMaxLines,
        )
    }
}

internal data class LowVisionCategoryOption(
    val label: String,
    val talkBackLabel: String = label,
    val selectionA11yDescription: String? = null,
    val resultA11yHintOverride: String? = null,
    @DrawableRes val iconRes: Int,
) {
    fun resultA11yHint(isSelected: Boolean): String =
        selectionA11yDescription?.let { description ->
            "${lowVisionCategorySelectionStateText(isSelected)}, $description"
        } ?: resultA11yHintOverride ?: lowVisionCategoryResultA11yHint(talkBackLabel)
}
