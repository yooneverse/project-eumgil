package com.ssafy.e102.eumgil.core.designsystem.component.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.designsystem.theme.EumRadius
import com.ssafy.e102.eumgil.core.designsystem.theme.EumSpacing

enum class EumDuribalCallConfirmDismissStyle {
    TextButton,
    SecondaryButton,
}

@Composable
fun EumDuribalCallConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismissStyle: EumDuribalCallConfirmDismissStyle,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.medium),
                ) {
                    Text(
                        text = stringResource(id = R.string.my_page_duribal_call_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(id = R.string.my_page_duribal_call_dialog_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(EumSpacing.small),
                ) {
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(EumRadius.medium),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp),
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
                            text = stringResource(id = R.string.my_page_duribal_call_dialog_confirm),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(EumRadius.medium),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        border =
                            when (dismissStyle) {
                                EumDuribalCallConfirmDismissStyle.TextButton -> null
                                EumDuribalCallConfirmDismissStyle.SecondaryButton ->
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f))
                            },
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
                            text = stringResource(id = R.string.my_page_duribal_call_dialog_dismiss),
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
