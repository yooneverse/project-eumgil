package com.ssafy.e102.eumgil.feature.textsize

import androidx.annotation.StringRes
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.model.TextSizePreference

data class TextSizeSettingUiState(
    val selectedPreference: TextSizePreference = TextSizePreference.DEFAULT,
    val options: List<TextSizeSettingOption> = TextSizeSettingOptions,
)

data class TextSizeSettingOption(
    val preference: TextSizePreference,
    @StringRes val labelRes: Int,
    @StringRes val scaleLabelRes: Int,
)

sealed interface TextSizeSettingUiAction {
    data object BackClicked : TextSizeSettingUiAction

    data class PreferenceSelected(
        val preference: TextSizePreference,
    ) : TextSizeSettingUiAction
}

sealed interface TextSizeSettingUiEvent {
    data object NavigateBack : TextSizeSettingUiEvent
}

val TextSizeSettingOptions: List<TextSizeSettingOption> =
    listOf(
        TextSizeSettingOption(
            preference = TextSizePreference.DEFAULT,
            labelRes = R.string.text_size_default,
            scaleLabelRes = R.string.text_size_default_description,
        ),
        TextSizeSettingOption(
            preference = TextSizePreference.LARGE,
            labelRes = R.string.text_size_large,
            scaleLabelRes = R.string.text_size_large_description,
        ),
        TextSizeSettingOption(
            preference = TextSizePreference.EXTRA_LARGE,
            labelRes = R.string.text_size_extra_large,
            scaleLabelRes = R.string.text_size_extra_large_description,
        ),
    )
