package com.ssafy.e102.eumgil.feature.lowvision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteLeg
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteStep
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.data.repository.DestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.feature.navigation.NavigationGuidanceAction
import com.ssafy.e102.eumgil.feature.navigation.toCompactNavigationInstruction
import com.ssafy.e102.eumgil.feature.navigation.toNavigationGuidanceAction
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LowVisionRouteBriefingUiState(
    val isLoading: Boolean = true,
    val destinationName: String = "목적지",
    val steps: List<LowVisionRouteBriefingStepUiState> = emptyList(),
    val errorMessage: String? = null,
) {
    val briefingText: String
        get() = steps.toBriefingSpeechText()
}

data class LowVisionRouteBriefingStepUiState(
    val sequence: Int,
    val instruction: String,
    val icon: LowVisionRouteBriefingStepIcon,
)

enum class LowVisionRouteBriefingStepIcon {
    STRAIGHT,
    TRANSIT,
    TURN,
}

internal const val BRIEFING_VISIBLE_STEP_COUNT: Int = 3

internal fun List<LowVisionRouteBriefingStepUiState>.visibleBriefingSteps(
    startIndex: Int,
): List<LowVisionRouteBriefingStepUiState> {
    if (isEmpty()) return emptyList()

    val clampedStartIndex = startIndex.coerceIn(0, lastIndex)
    val windowStartIndex = clampedStartIndex - clampedStartIndex % BRIEFING_VISIBLE_STEP_COUNT
    return drop(windowStartIndex).take(BRIEFING_VISIBLE_STEP_COUNT)
}

internal fun List<LowVisionRouteBriefingStepUiState>.nextBriefingWindowStart(
    startIndex: Int,
): Int? {
    val nextStartIndex =
        if (startIndex < 0) {
            0
        } else {
            startIndex - startIndex % BRIEFING_VISIBLE_STEP_COUNT + BRIEFING_VISIBLE_STEP_COUNT
        }

    return nextStartIndex.takeIf { it < size }
}

internal fun List<LowVisionRouteBriefingStepUiState>.briefingSpeechTextFrom(
    startIndex: Int,
): String =
    visibleBriefingSteps(startIndex).toBriefingSpeechText()

internal fun List<LowVisionRouteBriefingStepUiState>.toBriefingSpeechText(): String {
    val title = "\uACBD\uB85C \uBE0C\uB9AC\uD551"
    if (isEmpty()) return title

    return buildList {
        add(title)
        this@toBriefingSpeechText.forEach { step ->
            add("${step.sequence}\uBC88")
            add(step.instruction.trim().trimEnd('.'))
        }
    }.joinToString(separator = ". ", postfix = ".")
}

class LowVisionRouteBriefingViewModel(
    private val routeRepository: RouteRepository,
    private val destinationSelectionRepository: DestinationSelectionRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LowVisionRouteBriefingUiState())
    val uiState = mutableUiState.asStateFlow()

    fun loadBriefing(origin: RouteWaypoint) {
        mutableUiState.update { state ->
            state.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                routeRepository.buildLowVisionNavigationPlan(
                    destinationSelectionRepository = destinationSelectionRepository,
                    origin = origin,
                )
            }.onSuccess { plan ->
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        destinationName =
                            plan
                                ?.searchData
                                ?.result
                                ?.destination
                                ?.name
                                .orEmpty()
                                .ifBlank { "목적지" },
                        steps = plan?.selectedRoute?.toLowVisionRouteBriefingSteps().orEmpty(),
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        steps = emptyList(),
                        errorMessage = throwable.message ?: "경로 브리핑을 불러오지 못했습니다.",
                    )
                }
            }
        }
    }

    fun showLocationRequired() {
        mutableUiState.update { state ->
            state.copy(
                isLoading = false,
                steps = emptyList(),
                errorMessage =
                    "\uD604\uC7AC \uC704\uCE58\uB97C \uD655\uC778\uD55C \uB4A4 \uACBD\uB85C \uBE0C\uB9AC\uD551\uC744 \uBD88\uB7EC\uC62C\uAC8C\uC694.",
            )
        }
    }

    companion object {
        fun provideFactory(
            routeRepository: RouteRepository,
            destinationSelectionRepository: DestinationSelectionRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(LowVisionRouteBriefingViewModel::class.java)) {
                        return LowVisionRouteBriefingViewModel(
                            routeRepository = routeRepository,
                            destinationSelectionRepository = destinationSelectionRepository,
                        ) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

internal fun RouteCandidate.toLowVisionRouteBriefingSteps(): List<LowVisionRouteBriefingStepUiState> {
    val briefingSegments =
        segments.takeIf(List<RouteSegment>::isNotEmpty)
            ?: legs.toLowVisionBriefingSegments()

    return briefingSegments.mapIndexed { index, segment ->
        LowVisionRouteBriefingStepUiState(
            sequence = index + 1,
            instruction = segment.toDetailedBriefingInstruction(),
            icon = toNavigationGuidanceAction(segment).toBriefingStepIcon(),
        )
    }
}

private fun List<RouteLeg>.toLowVisionBriefingSegments(): List<RouteSegment> =
    buildList {
        var nextSequence = 1
        sortedBy(RouteLeg::sequence).forEach { leg ->
            if (leg.steps.isNotEmpty()) {
                leg.steps
                    .sortedBy(RouteStep::sequence)
                    .forEach { step ->
                        add(
                            step.toLowVisionBriefingSegment(
                                sequence = nextSequence++,
                                sourceLegSequence = leg.sequence,
                            ),
                        )
                    }
            } else {
                add(leg.toLowVisionBriefingSegment(sequence = nextSequence++))
            }
        }
    }

private fun RouteStep.toLowVisionBriefingSegment(
    sequence: Int,
    sourceLegSequence: Int,
): RouteSegment =
    RouteSegment(
        sequence = sequence,
        polyline = polyline,
        anchorCoordinate = anchorCoordinate,
        distanceMeters = distanceMeters,
        guidanceMessage = instruction,
        guidanceDirection = guidanceDirection,
        guidanceFeatures = guidanceFeatures,
        sourceLegSequence = sourceLegSequence,
        sourceStepSequence = this.sequence,
    )

private fun RouteLeg.toLowVisionBriefingSegment(sequence: Int): RouteSegment =
    RouteSegment(
        sequence = sequence,
        polyline = polyline,
        distanceMeters = distanceMeters ?: steps.sumOf(RouteStep::distanceMeters),
        guidanceMessage = instruction,
        sourceLegSequence = this.sequence,
    )

internal fun RouteSegment.toCompactBriefingInstruction(): String =
    toCompactNavigationInstruction()

internal fun RouteSegment.toDetailedBriefingInstruction(): String {
    val compactInstruction = toCompactNavigationInstruction()
    val message = guidanceMessage.trim().trimEnd('.', '。')
    if (message.isBlank()) return compactInstruction

    val distanceLabel = distanceMeters.toBriefingDistanceLabel()
    return when {
        distanceLabel.isBlank() -> message
        message.containsBriefingDistance() -> message
        else -> "$distanceLabel $message"
    }
}

private fun NavigationGuidanceAction.toBriefingStepIcon(): LowVisionRouteBriefingStepIcon =
    when (this) {
        NavigationGuidanceAction.TURN_LEFT,
        NavigationGuidanceAction.TURN_RIGHT,
            -> LowVisionRouteBriefingStepIcon.TURN

        NavigationGuidanceAction.BUS,
        NavigationGuidanceAction.SUBWAY,
        NavigationGuidanceAction.ALIGHT,
        NavigationGuidanceAction.CROSSWALK,
            -> LowVisionRouteBriefingStepIcon.TRANSIT

        NavigationGuidanceAction.STRAIGHT,
        NavigationGuidanceAction.START,
        NavigationGuidanceAction.ARRIVAL,
        NavigationGuidanceAction.TACTILE_GUIDE,
        NavigationGuidanceAction.ELEVATOR,
        NavigationGuidanceAction.CONSTRUCTION,
        NavigationGuidanceAction.CURB_GAP,
        NavigationGuidanceAction.STAIRS,
        NavigationGuidanceAction.FALLBACK,
            -> LowVisionRouteBriefingStepIcon.STRAIGHT
    }

private fun Int.toBriefingDistanceLabel(): String =
    when {
        this <= 0 -> ""
        this < 1_000 -> "${this}m"
        this % 1_000 == 0 -> "${this / 1_000}km"
        else -> String.format(Locale.US, "%.1fkm", this / 1_000.0)
    }

private fun String.containsBriefingDistance(): Boolean =
    briefingDistanceRegex.containsMatchIn(this)

private val briefingDistanceRegex =
    Regex("""\d+(?:\.\d+)?\s*(?:km|m|미터|킬로미터)""", RegexOption.IGNORE_CASE)
