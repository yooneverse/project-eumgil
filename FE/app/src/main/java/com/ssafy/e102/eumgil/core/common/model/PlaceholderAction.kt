package com.ssafy.e102.eumgil.core.common.model

data class PlaceholderAction(
    val label: String,
    val onClick: () -> Unit,
    val isPrimary: Boolean = false,
)
