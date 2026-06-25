package com.ssafy.e102.eumgil.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.data.local.datasource.InitSettingsLocalDataSource
import com.ssafy.e102.eumgil.feature.onboarding.MobilitySubtype
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `settings repository isolates onboarding progress by signup scope`() =
        runTest {
            val repository = createRepository()

            authSessionRepository.saveSignupToken("signup-token-a")
            repository.savePrimaryUserType(PrimaryUserType.LOW_VISION.routeValue)
            repository.saveLowVisionFollowUpCompleted(isCompleted = true)

            authSessionRepository.saveSignupToken("signup-token-b")
            assertEquals(InitSettings(), repository.getInitSettings())

            repository.savePrimaryUserType(PrimaryUserType.MOBILITY_IMPAIRED.routeValue)
            repository.saveMobilitySubtype(MobilitySubtype.MANUAL_WHEELCHAIR.routeValue)

            authSessionRepository.saveSignupToken("signup-token-a")
            val firstSettings = repository.getInitSettings()
            assertEquals(PrimaryUserType.LOW_VISION.routeValue, firstSettings.selectedPrimaryUserType)
            assertTrue(firstSettings.isLowVisionFollowUpCompleted)
            assertNull(firstSettings.selectedMobilitySubtype)

            authSessionRepository.saveSignupToken("signup-token-b")
            val secondSettings = repository.getInitSettings()
            assertEquals(PrimaryUserType.MOBILITY_IMPAIRED.routeValue, secondSettings.selectedPrimaryUserType)
            assertEquals(MobilitySubtype.MANUAL_WHEELCHAIR.routeValue, secondSettings.selectedMobilitySubtype)
        }

    @Test
    fun `settings repository falls back to authenticated session when scoped onboarding state is empty`() =
        runTest {
            val repository = createRepository()

            authSessionRepository.saveAuthSession(
                authSession =
                    AuthSession(
                        accessToken = "access-token",
                        refreshToken = "refresh-token",
                        userId = "user-1",
                        selectedPrimaryUserType = "LOW_VISION",
                    ),
                isProfileCompleted = true,
            )

            val settings = repository.getInitSettings()

            assertEquals(PrimaryUserType.LOW_VISION.routeValue, settings.selectedPrimaryUserType)
            assertNull(settings.selectedMobilitySubtype)
            assertTrue(settings.isLowVisionFollowUpCompleted)
            assertTrue(settings.isLocationTermsAgreed)
            assertTrue(settings.isPrivacyPolicyAgreed)
        }

    private lateinit var authSessionRepository: SettingsTestAuthSessionRepository

    private fun TestScope.createRepository(): SettingsRepository {
        val file = File(temporaryFolder.newFolder(), "init_settings.preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file },
            )

        authSessionRepository = SettingsTestAuthSessionRepository()
        return DefaultSettingsRepository(
            initSettingsLocalDataSource = InitSettingsLocalDataSource(dataStore = dataStore),
            authSessionRepository = authSessionRepository,
        )
    }
}

private class SettingsTestAuthSessionRepository : AuthSessionRepository {
    private val authGateState = MutableStateFlow(AuthGateState())

    override fun observeAuthGateState(): Flow<AuthGateState> = authGateState

    override suspend fun getAuthGateState(): AuthGateState = authGateState.value

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) {
        authGateState.value =
            AuthGateState(
                authSession = authSession,
                isProfileCompleted = isProfileCompleted,
            )
    }

    override suspend fun saveSignupToken(signupToken: String) {
        authGateState.value = AuthGateState(signupToken = signupToken)
    }

    override suspend fun clearSignupToken() {
        authGateState.value = authGateState.value.copy(signupToken = null)
    }

    override suspend fun markProfileCompleted() {
        authGateState.value = authGateState.value.copy(isProfileCompleted = true)
    }

    override suspend fun clearAuthSession() {
        authGateState.value = AuthGateState()
    }
}
