package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.InitSettings
import kotlinx.coroutines.flow.Flow

interface InitSettingsRepository {
    fun observeInitSettings(): Flow<InitSettings>

    suspend fun getInitSettings(): InitSettings

    suspend fun savePrimaryUserType(selectedPrimaryUserType: String)

    suspend fun saveMobilitySubtype(selectedMobilitySubtype: String)

    suspend fun saveLowVisionFollowUpCompleted(isCompleted: Boolean)

    suspend fun saveLocationTermsAgreement(
        isLocationTermsAgreed: Boolean,
        isPrivacyPolicyAgreed: Boolean,
    )

    suspend fun clearInitSettings()
}
