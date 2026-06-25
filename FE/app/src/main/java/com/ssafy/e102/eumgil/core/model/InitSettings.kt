package com.ssafy.e102.eumgil.core.model

data class InitSettings(
    val selectedPrimaryUserType: String? = null,
    val selectedMobilitySubtype: String? = null,
    val isLowVisionFollowUpCompleted: Boolean = false,
    val isLocationTermsAgreed: Boolean = false,
    val isPrivacyPolicyAgreed: Boolean = false,
) {
    val isOnboardingCompleted: Boolean
        get() =
            selectedPrimaryUserType != null &&
                isLocationTermsAgreed &&
                (isLowVisionFollowUpCompleted || selectedMobilitySubtype != null)
}
