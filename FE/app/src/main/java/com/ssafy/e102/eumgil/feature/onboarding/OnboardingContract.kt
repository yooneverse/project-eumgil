package com.ssafy.e102.eumgil.feature.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ssafy.e102.eumgil.R

data class PrimaryUserTypeUiState(
    val selectedType: PrimaryUserType? = null,
)

data class MobilitySubtypeUiState(
    val selectedMobilitySubtype: MobilitySubtype? = null,
)

data class LocationTermsUiState(
    val isServiceTermsChecked: Boolean = false,
    val isSensitiveInfoTermsChecked: Boolean = false,
    val isPersonalLocationInfoTermsChecked: Boolean = false,
    val isOverFourteenChecked: Boolean = false,
    val hasRestrictionNotice: Boolean = false,
) {
    val checkedRequiredTermsCount: Int
        get() =
            listOf(
                isServiceTermsChecked,
                isSensitiveInfoTermsChecked,
                isPersonalLocationInfoTermsChecked,
                isOverFourteenChecked,
            ).count { it }

    val checkedTermsCount: Int
        get() = checkedRequiredTermsCount

    val isRequiredTermsChecked: Boolean
        get() =
            isServiceTermsChecked &&
                isSensitiveInfoTermsChecked &&
                isPersonalLocationInfoTermsChecked &&
                isOverFourteenChecked

    val isAllTermsChecked: Boolean
        get() = isRequiredTermsChecked

    val canProceed: Boolean
        get() = isRequiredTermsChecked

    val consentStatus: LocationTermsConsentStatus
        get() = when {
            canProceed -> LocationTermsConsentStatus.READY
            hasRestrictionNotice -> LocationTermsConsentStatus.RESTRICTED
            else -> LocationTermsConsentStatus.PENDING
        }

    fun toAgreement(): LocationTermsAgreement =
        LocationTermsAgreement(
            isLocationTermsAgreed = isRequiredTermsChecked,
            isPrivacyPolicyAgreed = false,
        )

    companion object {
        const val REQUIRED_TERMS_COUNT: Int = 4
        const val TOTAL_TERMS_COUNT: Int = 4
    }
}

data class LocationTermsAgreement(
    val isLocationTermsAgreed: Boolean,
    val isPrivacyPolicyAgreed: Boolean,
)

enum class LocationTermsConsentStatus {
    PENDING,
    RESTRICTED,
    READY,
}

enum class LocationTermsItem(
    @StringRes val titleRes: Int,
    val required: Boolean,
) {
    SERVICE_AND_LOCATION_BASED_SERVICE(
        titleRes = R.string.onboarding_terms_service_required_title,
        required = true,
    ),
    SENSITIVE_INFO(
        titleRes = R.string.onboarding_terms_sensitive_required_title,
        required = true,
    ),
    PERSONAL_LOCATION_INFO(
        titleRes = R.string.onboarding_terms_personal_location_required_title,
        required = true,
    ),
    OVER_FOURTEEN(
        titleRes = R.string.onboarding_terms_age_required_title,
        required = true,
    ),
}

enum class PrimaryUserType(
    val routeValue: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
) {
    LOW_VISION(
        routeValue = "low_vision",
        titleRes = R.string.onboarding_primary_user_type_low_vision_title,
        descriptionRes = R.string.onboarding_primary_user_type_low_vision_description,
        iconRes = R.drawable.ic_user_low_vision,
    ),
    MOBILITY_IMPAIRED(
        routeValue = "mobility_impaired",
        titleRes = R.string.onboarding_primary_user_type_mobility_title,
        descriptionRes = R.string.onboarding_primary_user_type_mobility_description,
        iconRes = R.drawable.ic_user_wheelchair,
    ),
    ;

    companion object {
        fun fromRouteValue(routeValue: String?): PrimaryUserType? =
            entries.firstOrNull { it.routeValue == routeValue }
    }
}

enum class MobilitySubtype(
    val routeValue: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    val iconSizeDp: Int = 84,
) {
    ELECTRIC_WHEELCHAIR(
        routeValue = "electric_wheelchair",
        titleRes = R.string.onboarding_mobility_subtype_electric_title,
        descriptionRes = R.string.onboarding_mobility_subtype_electric_description,
        iconRes = R.drawable.ic_user_electric_wheelchair,
        iconSizeDp = 96,
    ),
    MANUAL_WHEELCHAIR(
        routeValue = "manual_wheelchair",
        titleRes = R.string.onboarding_mobility_subtype_manual_title,
        descriptionRes = R.string.onboarding_mobility_subtype_manual_description,
        iconRes = R.drawable.ic_user_wheelchair_solid,
        iconSizeDp = 84,
    ),
    OTHER(
        routeValue = "other_mobility_impaired",
        titleRes = R.string.onboarding_mobility_subtype_other_title,
        descriptionRes = R.string.onboarding_mobility_subtype_other_description,
        iconRes = R.drawable.ic_user_walking_aid,
        iconSizeDp = 96,
    ),
    ;

    companion object {
        fun fromRouteValue(routeValue: String?): MobilitySubtype? =
            entries.firstOrNull { it.routeValue == routeValue }
    }
}
