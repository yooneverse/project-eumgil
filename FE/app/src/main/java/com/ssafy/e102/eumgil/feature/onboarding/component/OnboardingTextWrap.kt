package com.ssafy.e102.eumgil.feature.onboarding.component

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak

private const val WORD_JOINER = '\u2060'

internal fun TextStyle.onboardingHeadingLineBreak(): TextStyle =
    copy(
        lineBreak = LineBreak.Heading,
        hyphens = Hyphens.None,
    )

internal fun TextStyle.onboardingBodyLineBreak(): TextStyle =
    copy(
        lineBreak = LineBreak.Paragraph,
        hyphens = Hyphens.None,
    )

internal fun String.stabilizeOnboardingWrap(): String {
    if (isEmpty()) return this

    return buildString(length * 2) {
        this@stabilizeOnboardingWrap.forEachIndexed { index, current ->
            val previous = this@stabilizeOnboardingWrap.getOrNull(index - 1)

            when {
                current.isHangulSyllable() && previous?.isHangulSyllable() == true -> {
                    append(WORD_JOINER)
                    append(current)
                }
                current == '(' && previous?.isHangulSyllable() == true -> {
                    append(WORD_JOINER)
                    append(current)
                }
                else -> append(current)
            }
        }
    }
}

private fun Char.isHangulSyllable(): Boolean = this in '\uAC00'..'\uD7A3'
