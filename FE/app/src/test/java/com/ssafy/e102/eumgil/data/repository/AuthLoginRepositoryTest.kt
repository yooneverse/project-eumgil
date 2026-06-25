package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.SocialLoginResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLoginRepositoryTest {
    @Test
    fun `existing user social login saves service tokens and user type mirror`() =
        runTest {
            val authSessionRepository = RecordingAuthSessionRepository()
            val settingsRepository = RecordingSettingsRepository()
            val repository =
                ServerAuthLoginRepository(
                    authRemoteDataSource =
                        FakeAuthRemoteDataSource(
                            socialLoginResponse =
                                SocialLoginResponseDto(
                                    signupRequired = false,
                                    signupToken = null,
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                    userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                                    selectedPrimaryUserType = "MOBILITY_IMPAIRED",
                                    selectedMobilitySubtype = "MANUAL_WHEELCHAIR",
                                ),
                        ),
                    socialAccessTokenProvider =
                        FakeSocialAccessTokenProvider(accessToken = "kakao-access-token"),
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            repository.login(AuthLoginRequest(providerKey = "auth-ui-kakao"))

            assertNotNull(authSessionRepository.savedAuthSession)
            assertTrue(authSessionRepository.savedIsProfileCompleted)
            assertNull(authSessionRepository.savedSignupToken)
            assertEquals(
                "access-token",
                authSessionRepository.savedAuthSession?.accessToken,
            )
            assertEquals("refresh-token", authSessionRepository.savedAuthSession?.refreshToken)
            assertEquals("mobility_impaired", settingsRepository.savedPrimaryUserType)
            assertEquals("manual_wheelchair", settingsRepository.savedMobilitySubtype)
            assertTrue(settingsRepository.savedLocationTermsAgreed)
        }

    @Test
    fun `new user social login stores signup token without service session`() =
        runTest {
            val authSessionRepository = RecordingAuthSessionRepository()
            val settingsRepository = RecordingSettingsRepository()
            val authRemoteDataSource =
                FakeAuthRemoteDataSource(
                    socialLoginResponse =
                        SocialLoginResponseDto(
                            signupRequired = true,
                            signupToken = "signup-token",
                            accessToken = null,
                            refreshToken = null,
                            userId = null,
                            selectedPrimaryUserType = null,
                            selectedMobilitySubtype = null,
                        ),
                )
            val repository =
                ServerAuthLoginRepository(
                    authRemoteDataSource = authRemoteDataSource,
                    socialAccessTokenProvider =
                        FakeSocialAccessTokenProvider(accessToken = "naver-access-token"),
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            repository.login(AuthLoginRequest(providerKey = "auth-ui-naver"))

            assertNull(authSessionRepository.savedAuthSession)
            assertEquals("signup-token", authSessionRepository.savedSignupToken)
            assertTrue(settingsRepository.clearInitSettingsCalled)
            assertEquals("NAVER", authRemoteDataSource.latestSocialProvider)
            assertEquals("naver-access-token", authRemoteDataSource.latestSocialAccessToken)
        }

    @Test
    fun `google social login sends google provider and access token to auth api`() =
        runTest {
            val authRemoteDataSource =
                FakeAuthRemoteDataSource(
                    socialLoginResponse =
                        SocialLoginResponseDto(
                            signupRequired = true,
                            signupToken = "google-signup-token",
                            accessToken = null,
                            refreshToken = null,
                            userId = null,
                            selectedPrimaryUserType = null,
                            selectedMobilitySubtype = null,
                        ),
                )
            val repository =
                ServerAuthLoginRepository(
                    authRemoteDataSource = authRemoteDataSource,
                    socialAccessTokenProvider =
                        FakeSocialAccessTokenProvider(accessToken = "google-access-token"),
                    authSessionRepository = RecordingAuthSessionRepository(),
                    settingsRepository = RecordingSettingsRepository(),
                )

            repository.login(AuthLoginRequest(providerKey = "auth-ui-google"))

            assertEquals("GOOGLE", authRemoteDataSource.latestSocialProvider)
            assertEquals("google-access-token", authRemoteDataSource.latestSocialAccessToken)
        }
}

private class FakeAuthRemoteDataSource(
    private val socialLoginResponse: SocialLoginResponseDto,
) : AuthRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "https://example.com")) {
    var latestSocialProvider: String? = null
        private set
    var latestSocialAccessToken: String? = null
        private set

    override suspend fun socialLogin(
        socialProvider: String,
        socialAccessToken: String,
    ): SocialLoginResponseDto {
        latestSocialProvider = socialProvider
        latestSocialAccessToken = socialAccessToken
        return socialLoginResponse
    }
}

private class FakeSocialAccessTokenProvider(
    private val accessToken: String,
) : SocialAccessTokenProvider {
    override suspend fun getAccessToken(provider: AuthSocialProvider): String = accessToken
}

private class RecordingAuthSessionRepository : AuthSessionRepository {
    var savedAuthSession: AuthSession? = null
        private set
    var savedIsProfileCompleted: Boolean = false
        private set
    var savedSignupToken: String? = null
        private set

    override fun observeAuthGateState(): Flow<AuthGateState> = emptyFlow()

    override suspend fun getAuthGateState(): AuthGateState = AuthGateState()

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) {
        savedAuthSession = authSession
        savedIsProfileCompleted = isProfileCompleted
    }

    override suspend fun saveSignupToken(signupToken: String) {
        savedSignupToken = signupToken
    }

    override suspend fun clearSignupToken() = Unit

    override suspend fun markProfileCompleted() = Unit

    override suspend fun clearAuthSession() = Unit
}

private class RecordingSettingsRepository : SettingsRepository {
    var savedPrimaryUserType: String? = null
        private set
    var savedMobilitySubtype: String? = null
        private set
    var savedLowVisionFollowUpCompleted: Boolean? = null
        private set
    var savedLocationTermsAgreed: Boolean = false
        private set
    var clearInitSettingsCalled: Boolean = false
        private set

    override fun observeInitSettings(): Flow<InitSettings> = flowOf(InitSettings())

    override suspend fun getInitSettings(): InitSettings = InitSettings()

    override suspend fun savePrimaryUserType(selectedPrimaryUserType: String) {
        savedPrimaryUserType = selectedPrimaryUserType
    }

    override suspend fun saveMobilitySubtype(selectedMobilitySubtype: String) {
        savedMobilitySubtype = selectedMobilitySubtype
    }

    override suspend fun saveLowVisionFollowUpCompleted(isCompleted: Boolean) {
        savedLowVisionFollowUpCompleted = isCompleted
    }

    override suspend fun saveLocationTermsAgreement(
        isLocationTermsAgreed: Boolean,
        isPrivacyPolicyAgreed: Boolean,
    ) {
        savedLocationTermsAgreed = isLocationTermsAgreed
    }

    override suspend fun clearInitSettings() {
        clearInitSettingsCalled = true
    }
}
