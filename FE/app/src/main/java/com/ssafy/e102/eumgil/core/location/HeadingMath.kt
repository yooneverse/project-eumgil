package com.ssafy.e102.eumgil.core.location

import kotlin.math.abs

fun normalizeHeadingDegrees(degrees: Double): Double {
    val normalized = degrees % FULL_ROTATION_DEGREES
    return if (normalized < 0.0) normalized + FULL_ROTATION_DEGREES else normalized
}

fun shortestHeadingDeltaDegrees(
    fromDegrees: Double,
    toDegrees: Double,
): Double {
    val delta = normalizeHeadingDegrees(toDegrees) - normalizeHeadingDegrees(fromDegrees)
    return when {
        delta > HALF_ROTATION_DEGREES -> delta - FULL_ROTATION_DEGREES
        delta <= -HALF_ROTATION_DEGREES -> delta + FULL_ROTATION_DEGREES
        else -> delta
    }
}

fun smoothHeadingDegrees(
    previousDegrees: Double?,
    nextDegrees: Double,
    smoothingFactor: Double = DEFAULT_HEADING_SMOOTHING_FACTOR,
    minimumDeltaDegrees: Double = DEFAULT_HEADING_MINIMUM_DELTA_DEGREES,
): Double {
    val normalizedNext = normalizeHeadingDegrees(nextDegrees)
    val previous = previousDegrees ?: return normalizedNext
    val delta = shortestHeadingDeltaDegrees(fromDegrees = previous, toDegrees = normalizedNext)
    if (abs(delta) < minimumDeltaDegrees) return normalizeHeadingDegrees(previous)
    return normalizeHeadingDegrees(previous + delta * smoothingFactor.coerceIn(0.0, 1.0))
}

const val DEFAULT_HEADING_SMOOTHING_FACTOR = 0.18
const val DEFAULT_HEADING_MINIMUM_DELTA_DEGREES = 2.0

private const val FULL_ROTATION_DEGREES = 360.0
private const val HALF_ROTATION_DEGREES = 180.0
