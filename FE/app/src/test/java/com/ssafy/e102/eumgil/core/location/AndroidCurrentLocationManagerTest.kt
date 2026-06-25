package com.ssafy.e102.eumgil.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidCurrentLocationManagerTest {
    @Test
    fun `refresh keeps previous fresh location when fused last location is unavailable`() {
        val previous =
            LocationSnapshot(
                latitude = 35.1798,
                longitude = 129.0762,
                accuracyMeters = 5f,
                recordedAtEpochMillis = 10_000L,
            )

        val resolved =
            resolveCurrentLocationRefreshSnapshot(
                previous = previous,
                candidate = null,
                nowEpochMillis = 11_000L,
            )

        assertEquals(previous, resolved)
    }

    @Test
    fun `refresh keeps previous fresh location when fused last location is stale`() {
        val previous =
            LocationSnapshot(
                latitude = 35.1798,
                longitude = 129.0762,
                accuracyMeters = 5f,
                recordedAtEpochMillis = 199_000L,
            )
        val staleCandidate =
            LocationSnapshot(
                latitude = 35.1700,
                longitude = 129.0500,
                accuracyMeters = 20f,
                recordedAtEpochMillis = 1_000L,
            )

        val resolved =
            resolveCurrentLocationRefreshSnapshot(
                previous = previous,
                candidate = staleCandidate,
                nowEpochMillis = 200_000L,
            )

        assertEquals(previous, resolved)
    }

    @Test
    fun `refresh replaces cache with fresh fused last location`() {
        val previous =
            LocationSnapshot(
                latitude = 35.1798,
                longitude = 129.0762,
                accuracyMeters = 5f,
                recordedAtEpochMillis = 10_000L,
            )
        val candidate =
            LocationSnapshot(
                latitude = 35.1800,
                longitude = 129.0770,
                accuracyMeters = 3f,
                recordedAtEpochMillis = 10_500L,
            )

        val resolved =
            resolveCurrentLocationRefreshSnapshot(
                previous = previous,
                candidate = candidate,
                nowEpochMillis = 11_000L,
            )

        assertEquals(candidate, resolved)
    }

    @Test
    fun `refresh clears stale cache when no fresh location is available`() {
        val previous =
            LocationSnapshot(
                latitude = 35.1798,
                longitude = 129.0762,
                accuracyMeters = 5f,
                recordedAtEpochMillis = 1_000L,
            )

        val resolved =
            resolveCurrentLocationRefreshSnapshot(
                previous = previous,
                candidate = null,
                nowEpochMillis = 130_000L,
            )

        assertNull(resolved)
    }

    @Test
    fun `navigation profile requests denser walking location updates`() {
        val config = LocationUpdateProfile.NAVIGATION.toLocationRequestConfig()

        assertEquals(1_000L, config.intervalMillis)
        assertEquals(500L, config.fastestIntervalMillis)
        assertEquals(1f, config.minDistanceMeters)
    }

    @Test
    fun `default profile keeps existing location update cadence`() {
        val config = LocationUpdateProfile.DEFAULT.toLocationRequestConfig()

        assertEquals(2_000L, config.intervalMillis)
        assertEquals(1_000L, config.fastestIntervalMillis)
        assertEquals(5f, config.minDistanceMeters)
    }
}
