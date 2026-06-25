package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role

internal fun lowVisionButtonA11yLabel(
    label: String,
    actionHint: String? = null,
): String =
    listOfNotNull(
        "$label 버튼",
        actionHint,
    ).joinToString(separator = ". ")

internal fun lowVisionPreparingButtonA11yLabel(label: String): String =
    "${lowVisionButtonA11yLabel(label)}. 준비 중인 기능입니다."

internal fun Modifier.lowVisionButtonSemantics(
    label: String,
    actionHint: String? = null,
): Modifier =
    clearAndSetSemantics {
        role = Role.Button
        contentDescription = lowVisionButtonA11yLabel(label, actionHint)
    }

internal fun Modifier.lowVisionPreparingButtonSemantics(label: String): Modifier =
    clearAndSetSemantics {
        role = Role.Button
        contentDescription = lowVisionPreparingButtonA11yLabel(label)
    }
