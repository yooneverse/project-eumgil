package com.ssafy.e102.eumgil.core.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object NoOpCurrentHeadingManager : CurrentHeadingManager {
    override val latestHeading: StateFlow<HeadingSnapshot?> = MutableStateFlow(null)

    override fun startHeadingUpdates() = Unit

    override fun stopHeadingUpdates() = Unit
}
