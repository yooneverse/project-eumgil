package com.ssafy.e102.eumgil.feature.terms.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 8x8 dot row used at the top of the terms walkthrough (Figma node 328:489).
 *
 * Active dot uses Supernova (#FFCC00); inactive dots use Tundora (#444444).
 * Spacing matches the Figma item-spacing/10 token (10dp gap).
 */
@Composable
fun TermsPagerIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFFFCC00),
    inactiveColor: Color = Color(0xFF444444),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val activeIndex = (currentStep - 1).coerceIn(0, totalSteps - 1)
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (index == activeIndex) activeColor else inactiveColor,
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}
