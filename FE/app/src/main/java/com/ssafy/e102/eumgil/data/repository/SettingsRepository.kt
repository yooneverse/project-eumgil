package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.core.model.resolveOnboardingScopeKey
import com.ssafy.e102.eumgil.data.local.datasource.InitSettingsLocalDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

interface SettingsRepository : InitSettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSettingsRepository(
    private val initSettingsLocalDataSource: InitSettingsLocalDataSource,
    private val authSessionRepository: AuthSessionRepository,
) : SettingsRepository {

    override fun observeInitSettings(): Flow<InitSettings> =
        authSessionRepository.observeAuthGateState().flatMapLatest { authGateState ->
            val scopeKey = authGateState.resolveOnboardingScopeKey()
            initSettingsLocalDataSource
                .observeInitSettings(scopeKey = scopeKey)
                .map { initSettings ->
                    initSettings.withAuthenticatedFallback(authGateState = authGateState)
                }
        }

    override suspend fun getInitSettings(): InitSettings {
        val authGateState = authSessionRepository.getAuthGateState()
        val scopeKey = authGateState.resolveOnboardingScopeKey()
        return initSettingsLocalDataSource
            .getInitSettings(scopeKey = scopeKey)
            .withAuthenticatedFallback(authGateState = authGateState)
    }

    override suspend fun savePrimaryUserType(selectedPrimaryUserType: String) {
        initSettingsLocalDataSource.savePrimaryUserType(
            scopeKey = authSessionRepository.getOnboardingScopeKey(),
            selectedPrimaryUserType = selectedPrimaryUserType,
        )
    }

    override suspend fun saveMobilitySubtype(selectedMobilitySubtype: String) {
        initSettingsLocalDataSource.saveMobilitySubtype(
            scopeKey = authSessionRepository.getOnboardingScopeKey(),
            selectedMobilitySubtype = selectedMobilitySubtype,
        )
    }

    override suspend fun saveLowVisionFollowUpCompleted(isCompleted: Boolean) {
        initSettingsLocalDataSource.saveLowVisionFollowUpCompleted(
            scopeKey = authSessionRepository.getOnboardingScopeKey(),
            isCompleted = isCompleted,
        )
    }

    override suspend fun saveLocationTermsAgreement(
        isLocationTermsAgreed: Boolean,
        isPrivacyPolicyAgreed: Boolean,
    ) {
        initSettingsLocalDataSource.saveLocationTermsAgreement(
            scopeKey = authSessionRepository.getOnboardingScopeKey(),
            isLocationTermsAgreed = isLocationTermsAgreed,
            isPrivacyPolicyAgreed = isPrivacyPolicyAgreed,
        )
    }

    override suspend fun clearInitSettings() {
        initSettingsLocalDataSource.clearInitSettings(scopeKey = authSessionRepository.getOnboardingScopeKey())
    }

    private fun InitSettings.withAuthenticatedFallback(authGateState: AuthGateState): InitSettings {
        if (selectedPrimaryUserType != null || !authGateState.isProfileCompleted) {
            return this
        }

        val authSession = authGateState.authSession ?: return this
        val selectedPrimaryUserType = authSession.selectedPrimaryUserType?.toPrimaryUserTypeRouteValueOrNull() ?: return this
        val selectedMobilitySubtype =
            if (selectedPrimaryUserType == ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED) {
                authSession.selectedMobilitySubtype?.toMobilitySubtypeRouteValueOrNull()
            } else {
                null
            }

        return copy(
            selectedPrimaryUserType = selectedPrimaryUserType,
            selectedMobilitySubtype = selectedMobilitySubtype,
            isLowVisionFollowUpCompleted = selectedPrimaryUserType == ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
            isLocationTermsAgreed = true,
            isPrivacyPolicyAgreed = true,
        )
    }
}
