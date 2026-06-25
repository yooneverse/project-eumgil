package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthApiException
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.SignupResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSignupRepositoryTest {
    @Test
    fun `pending signup completion saves service tokens and onboarding mirror`() =
        runTest {
            val authSessionRepository =
                RecordingSignupAuthSessionRepository(
                    authGateState = AuthGateState(signupToken = "signup-token"),
                )
            val settingsRepository =
                RecordingSignupSettingsRepository(
                    initialInitSettings =
                        InitSettings(
                            selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED,
                            selectedMobilitySubtype = "manual_wheelchair",
                        ),
                )
            val authRemoteDataSource =
                FakeSignupAuthRemoteDataSource(
                    signupResponse =
                        SignupResponseDto(
                            accessToken = "access-token",
                            refreshToken = "refresh-token",
                            userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                            selectedPrimaryUserType = "MOBILITY_IMPAIRED",
                            selectedMobilitySubtype = "MANUAL_WHEELCHAIR",
                        ),
                )
            val repository =
                ServerAuthSignupRepository(
                    authRemoteDataSource = authRemoteDataSource,
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            repository.completePendingSignup(requiredTermsAccepted = true)

            assertEquals("signup-token", authRemoteDataSource.latestSignupToken)
            assertEquals("MOBILITY_IMPAIRED", authRemoteDataSource.latestSelectedPrimaryUserType)
            assertEquals("MANUAL_WHEELCHAIR", authRemoteDataSource.latestSelectedMobilitySubtype)
            assertEquals(true, authRemoteDataSource.latestRequiredTermsAccepted)
            assertNotNull(authSessionRepository.savedAuthSession)
            assertTrue(authSessionRepository.savedIsProfileCompleted)
            assertEquals("access-token", authSessionRepository.savedAuthSession?.accessToken)
            assertEquals(
                ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED,
                settingsRepository.savedPrimaryUserType,
            )
            assertEquals("manual_wheelchair", settingsRepository.savedMobilitySubtype)
            assertEquals(false, settingsRepository.savedLowVisionFollowUpCompleted)
            assertTrue(settingsRepository.savedLocationTermsAgreed)
            assertTrue(settingsRepository.savedPrivacyPolicyAgreed)
        }

    @Test
    fun `signup completion without pending token keeps current local state`() =
        runTest {
            val authSessionRepository =
                RecordingSignupAuthSessionRepository(authGateState = AuthGateState())
            val settingsRepository =
                RecordingSignupSettingsRepository(
                    initialInitSettings =
                        InitSettings(
                            selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                        ),
                )
            val authRemoteDataSource =
                FakeSignupAuthRemoteDataSource(
                    signupResponse =
                        SignupResponseDto(
                            accessToken = "unused",
                            refreshToken = "unused",
                            userId = "unused",
                            selectedPrimaryUserType = "LOW_VISION",
                            selectedMobilitySubtype = null,
                        ),
                )
            val repository =
                ServerAuthSignupRepository(
                    authRemoteDataSource = authRemoteDataSource,
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            repository.completePendingSignup(requiredTermsAccepted = true)

            assertNull(authRemoteDataSource.latestSignupToken)
            assertNull(authSessionRepository.savedAuthSession)
            assertNull(settingsRepository.savedPrimaryUserType)
        }

    @Test
    fun `invalid signup token clears pending token and requires login again`() =
        runTest {
            val authSessionRepository =
                RecordingSignupAuthSessionRepository(
                    authGateState = AuthGateState(signupToken = "expired-signup-token"),
                )
            val settingsRepository =
                RecordingSignupSettingsRepository(
                    initialInitSettings =
                        InitSettings(
                            selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED,
                            selectedMobilitySubtype = "manual_wheelchair",
                        ),
                )
            val authRemoteDataSource =
                FakeSignupAuthRemoteDataSource(
                    signupFailure =
                        AuthApiException(
                            httpStatusCode = 401,
                            status = "A4013",
                            message = "회원가입 토큰이 유효하지 않습니다.",
                        ),
                )
            val repository =
                ServerAuthSignupRepository(
                    authRemoteDataSource = authRemoteDataSource,
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            val failure =
                runCatching {
                    repository.completePendingSignup(requiredTermsAccepted = true)
                }.exceptionOrNull()

            assertTrue(failure is PendingSignupTokenExpiredException)
            assertTrue(authSessionRepository.clearSignupTokenCalled)
            assertNull(authSessionRepository.savedAuthSession)
            assertFalse(authSessionRepository.savedIsProfileCompleted)
        }
}

private class FakeSignupAuthRemoteDataSource(
    private val signupResponse: SignupResponseDto? = null,
    private val signupFailure: Throwable? = null,
) : AuthRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "https://example.com")) {
    var latestSignupToken: String? = null
        private set
    var latestSelectedPrimaryUserType: String? = null
        private set
    var latestSelectedMobilitySubtype: String? = null
        private set
    var latestRequiredTermsAccepted: Boolean? = null
        private set

    override suspend fun signup(
        signupToken: String,
        selectedPrimaryUserType: String,
        selectedMobilitySubtype: String?,
        requiredTermsAccepted: Boolean,
    ): SignupResponseDto {
        latestSignupToken = signupToken
        latestSelectedPrimaryUserType = selectedPrimaryUserType
        latestSelectedMobilitySubtype = selectedMobilitySubtype
        latestRequiredTermsAccepted = requiredTermsAccepted
        signupFailure?.let { throw it }
        return checkNotNull(signupResponse)
    }
}

private class RecordingSignupAuthSessionRepository(
    private val authGateState: AuthGateState,
) : AuthSessionRepository {
    var savedAuthSession: AuthSession? = null
        private set
    var savedIsProfileCompleted: Boolean = false
        private set
    var clearSignupTokenCalled: Boolean = false
        private set

    override fun observeAuthGateState(): Flow<AuthGateState> = emptyFlow()

    override suspend fun getAuthGateState(): AuthGateState = authGateState

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) {
        savedAuthSession = authSession
        savedIsProfileCompleted = isProfileCompleted
    }

    override suspend fun saveSignupToken(signupToken: String) = Unit

    override suspend fun clearSignupToken() {
        clearSignupTokenCalled = true
    }

    override suspend fun markProfileCompleted() = Unit

    override suspend fun clearAuthSession() = Unit
}

private class RecordingSignupSettingsRepository(
    initialInitSettings: InitSettings,
) : SettingsRepository {
    private var initSettings: InitSettings = initialInitSettings

    var savedPrimaryUserType: String? = null
        private set
    var savedMobilitySubtype: String? = null
        private set
    var savedLowVisionFollowUpCompleted: Boolean? = null
        private set
    var savedLocationTermsAgreed: Boolean = false
        private set
    var savedPrivacyPolicyAgreed: Boolean = false
        private set

    override fun observeInitSettings(): Flow<InitSettings> = flowOf(initSettings)

    override suspend fun getInitSettings(): InitSettings = initSettings

    override suspend fun savePrimaryUserType(selectedPrimaryUserType: String) {
        savedPrimaryUserType = selectedPrimaryUserType
        initSettings =
            initSettings.copy(
                selectedPrimaryUserType = selectedPrimaryUserType,
                selectedMobilitySubtype =
                    if (selectedPrimaryUserType == ROUTE_PRIMARY_USER_TYPE_LOW_VISION) {
                        null
                    } else {
                        initSettings.selectedMobilitySubtype
                    },
            )
    }

    override suspend fun saveMobilitySubtype(selectedMobilitySubtype: String) {
        savedMobilitySubtype = selectedMobilitySubtype
        initSettings = initSettings.copy(selectedMobilitySubtype = selectedMobilitySubtype)
    }

    override suspend fun saveLowVisionFollowUpCompleted(isCompleted: Boolean) {
        savedLowVisionFollowUpCompleted = isCompleted
        initSettings = initSettings.copy(isLowVisionFollowUpCompleted = isCompleted)
    }

    override suspend fun saveLocationTermsAgreement(
        isLocationTermsAgreed: Boolean,
        isPrivacyPolicyAgreed: Boolean,
    ) {
        savedLocationTermsAgreed = isLocationTermsAgreed
        savedPrivacyPolicyAgreed = isPrivacyPolicyAgreed
        initSettings =
            initSettings.copy(
                isLocationTermsAgreed = isLocationTermsAgreed,
                isPrivacyPolicyAgreed = isPrivacyPolicyAgreed,
            )
    }

    override suspend fun clearInitSettings() {
        initSettings = InitSettings()
    }
}
