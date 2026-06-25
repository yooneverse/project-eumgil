package com.ssafy.e102.eumgil.feature.map.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoMapViewportLoadingOverlayPolicyTest {
    @Test
    fun `initial renderer loading overlay stays hidden during grace period`() {
        assertFalse(
            shouldShowKakaoRendererFallbackOverlay(
                isRendererReady = false,
                isRendererError = false,
                isAutomaticRetryLoading = false,
                hasLoadingGracePeriodElapsed = false,
            ),
        )
    }

    @Test
    fun `initial renderer loading overlay appears after grace period elapses`() {
        assertTrue(
            shouldShowKakaoRendererFallbackOverlay(
                isRendererReady = false,
                isRendererError = false,
                isAutomaticRetryLoading = false,
                hasLoadingGracePeriodElapsed = true,
            ),
        )
    }

    @Test
    fun `renderer error overlay bypasses grace period`() {
        assertTrue(
            shouldShowKakaoRendererFallbackOverlay(
                isRendererReady = false,
                isRendererError = true,
                isAutomaticRetryLoading = false,
                hasLoadingGracePeriodElapsed = false,
            ),
        )
    }

    @Test
    fun `automatic retry overlay bypasses grace period`() {
        assertTrue(
            shouldShowKakaoRendererFallbackOverlay(
                isRendererReady = false,
                isRendererError = false,
                isAutomaticRetryLoading = true,
                hasLoadingGracePeriodElapsed = false,
            ),
        )
    }

    @Test
    fun `ready renderer never shows fallback overlay`() {
        assertFalse(
            shouldShowKakaoRendererFallbackOverlay(
                isRendererReady = true,
                isRendererError = false,
                isAutomaticRetryLoading = false,
                hasLoadingGracePeriodElapsed = true,
            ),
        )
    }
}
