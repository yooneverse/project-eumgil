package com.ssafy.e102.eumgil.core.location

import kotlinx.coroutines.flow.StateFlow

interface CurrentHeadingManager {
    val latestHeading: StateFlow<HeadingSnapshot?>

    fun startHeadingUpdates()

    fun stopHeadingUpdates()
}
