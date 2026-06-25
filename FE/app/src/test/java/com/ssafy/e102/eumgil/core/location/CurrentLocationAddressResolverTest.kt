package com.ssafy.e102.eumgil.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentLocationAddressResolverTest {
    @Test
    fun `current location address label prefers road address components over full geocoder line`() {
        val label =
            lowVisionCurrentLocationAddressLabel(
                addressLine = "\uB300\uD55C\uBBFC\uAD6D \uBD80\uC0B0\uAD11\uC5ED\uC2DC \uBD80\uC0B0\uC9C4\uAD6C \uBD80\uC804\uB3D9 123",
                adminArea = "\uBD80\uC0B0\uAD11\uC5ED\uC2DC",
                locality = null,
                subLocality = "\uBD80\uC0B0\uC9C4\uAD6C",
                thoroughfare = "\uC911\uC559\uB300\uB85C",
                subThoroughfare = "668",
                premises = null,
                featureName = "\uBD80\uC804\uB3D9",
            )

        assertEquals("\uBD80\uC0B0\uAD11\uC5ED\uC2DC \uBD80\uC0B0\uC9C4\uAD6C \uC911\uC559\uB300\uB85C 668", label)
    }

    @Test
    fun `current location address label falls back to sanitized geocoder line`() {
        val label =
            lowVisionCurrentLocationAddressLabel(
                addressLine = "\uB300\uD55C\uBBFC\uAD6D \uBD80\uC0B0\uAD11\uC5ED\uC2DC \uD574\uC6B4\uB300\uAD6C \uC13C\uD140\uC911\uC559\uB85C 79",
                adminArea = null,
                locality = null,
                subLocality = null,
                thoroughfare = null,
                subThoroughfare = null,
                premises = null,
                featureName = null,
            )

        assertEquals("\uBD80\uC0B0\uAD11\uC5ED\uC2DC \uD574\uC6B4\uB300\uAD6C \uC13C\uD140\uC911\uC559\uB85C 79", label)
    }
}
