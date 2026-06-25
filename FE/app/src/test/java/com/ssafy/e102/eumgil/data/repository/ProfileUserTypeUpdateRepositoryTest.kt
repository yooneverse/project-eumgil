package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.UserTypeRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.UserTypeUpdateApiException
import com.ssafy.e102.eumgil.data.remote.dto.ReissueResponseDto
import com.ssafy.e102.eumgil.data.remote.dto.UserTypeResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUserTypeUpdateRepositoryTest {
    @Test
    fun `low vision completion patches server and synchronizes local state from response`() =
        runTest {
            val authSessionRepository =
                RecordingProfileAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                    userId = "existing-user-id",
                                    selectedPrimaryUserType = "MOBILITY_IMPAIRED",
                                    selectedMobilitySubtype = "MANUAL_WHEELCHAIR",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val settingsRepository =
                RecordingProfileSettingsRepository(
                    initialInitSettings =
                        InitSettings(
                            selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED,
                            selectedMobilitySubtype = "manual_wheelchair",
                            isLocationTermsAgreed = true,
                            isPrivacyPolicyAgreed = true,
                        ),
                )
            val remoteDataSource =
                FakeUserTypeRemoteDataSource(
                    response =
                        UserTypeResponseDto(
                            userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                            selectedPrimaryUserType = "LOW_VISION",
                            selectedMobilitySubtype = null,
                        ),
                )
            val repository =
                ServerProfileUserTypeUpdateRepository(
                    userTypeRemoteDataSource = remoteDataSource,
                    authRemoteDataSource = FakeProfileAuthRemoteDataSource(),
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            val result =
                repository.completeProfileEdit(
                    selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                    selectedMobilitySubtype = null,
                )

            assertEquals("access-token", remoteDataSource.latestAccessToken)
            assertEquals("LOW_VISION", remoteDataSource.latestSelectedPrimaryUserType)
            assertNull(remoteDataSource.latestSelectedMobilitySubtype)
            assertTrue(authSessionRepository.savedIsProfileCompleted)
            assertEquals("LOW_VISION", authSessionRepository.savedAuthSession?.selectedPrimaryUserType)
            assertNull(authSessionRepository.savedAuthSession?.selectedMobilitySubtype)
            assertEquals(ROUTE_PRIMARY_USER_TYPE_LOW_VISION, settingsRepository.savedPrimaryUserType)
            assertNull(settingsRepository.savedMobilitySubtype)
            assertEquals(true, settingsRepository.savedLowVisionFollowUpCompleted)
            assertTrue(settingsRepository.savedLocationTermsAgreed)
            assertTrue(settingsRepository.savedPrivacyPolicyAgreed)
            assertEquals(
                ProfileUserTypeUpdateResult.Success(
                    selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                    selectedMobilitySubtype = null,
                ),
                result,
            )
        }

    @Test
    fun `mobility completion patches server and synchronizes local state from response`() =
        runTest {
            val authSessionRepository =
                RecordingProfileAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                    userId = "existing-user-id",
                                    selectedPrimaryUserType = "LOW_VISION",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val settingsRepository = RecordingProfileSettingsRepository(initialInitSettings = InitSettings())
            val remoteDataSource =
                FakeUserTypeRemoteDataSource(
                    response =
                        UserTypeResponseDto(
                            userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                            selectedPrimaryUserType = "MOBILITY_IMPAIRED",
                            selectedMobilitySubtype = "POWER_WHEELCHAIR",
                        ),
                )
            val repository =
                ServerProfileUserTypeUpdateRepository(
                    userTypeRemoteDataSource = remoteDataSource,
                    authRemoteDataSource = FakeProfileAuthRemoteDataSource(),
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            val result =
                repository.completeProfileEdit(
                    selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED,
                    selectedMobilitySubtype = "electric_wheelchair",
                )

            assertEquals("access-token", remoteDataSource.latestAccessToken)
            assertEquals("MOBILITY_IMPAIRED", remoteDataSource.latestSelectedPrimaryUserType)
            assertEquals("POWER_WHEELCHAIR", remoteDataSource.latestSelectedMobilitySubtype)
            assertTrue(authSessionRepository.savedIsProfileCompleted)
            assertEquals("MOBILITY_IMPAIRED", authSessionRepository.savedAuthSession?.selectedPrimaryUserType)
            assertEquals("POWER_WHEELCHAIR", authSessionRepository.savedAuthSession?.selectedMobilitySubtype)
            assertEquals(ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED, settingsRepository.savedPrimaryUserType)
            assertEquals("electric_wheelchair", settingsRepository.savedMobilitySubtype)
            assertEquals(false, settingsRepository.savedLowVisionFollowUpCompleted)
            assertEquals(
                ProfileUserTypeUpdateResult.Success(
                    selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED,
                    selectedMobilitySubtype = "electric_wheelchair",
                ),
                result,
            )
        }

    @Test
    fun `authentication failure clears session and does not commit local onboarding state`() =
        runTest {
            val authSessionRepository =
                RecordingProfileAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "expired-access-token",
                                    refreshToken = "refresh-token",
                                    userId = "existing-user-id",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val settingsRepository = RecordingProfileSettingsRepository(initialInitSettings = InitSettings())
            val remoteDataSource =
                FakeUserTypeRemoteDataSource(
                    exception =
                        UserTypeUpdateApiException(
                            httpStatusCode = 401,
                            status = "UNAUTHORIZED",
                            message = "expired",
                        ),
                )
            val repository =
                ServerProfileUserTypeUpdateRepository(
                    userTypeRemoteDataSource = remoteDataSource,
                    authRemoteDataSource =
                        FakeProfileAuthRemoteDataSource(
                            reissueThrowable = IllegalStateException("reissue failed"),
                        ),
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            val result =
                repository.completeProfileEdit(
                    selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                    selectedMobilitySubtype = null,
                )

            assertSame(ProfileUserTypeUpdateResult.AuthenticationFailed, result)
            assertTrue(authSessionRepository.clearAuthSessionCalled)
            assertNull(authSessionRepository.savedAuthSession)
            assertNull(settingsRepository.savedPrimaryUserType)
            assertNull(settingsRepository.savedMobilitySubtype)
            assertNull(settingsRepository.savedLowVisionFollowUpCompleted)
        }

    @Test
    fun `profile edit retries once after reissue success and uses refreshed access token`() =
        runTest {
            val authSessionRepository =
                RecordingProfileAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "expired-access-token",
                                    refreshToken = "refresh-token",
                                    userId = "existing-user-id",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val settingsRepository = RecordingProfileSettingsRepository(initialInitSettings = InitSettings())
            val userTypeRemoteDataSource =
                FakeUserTypeRemoteDataSource(
                    queuedResults =
                        listOf(
                            Result.failure(
                                UserTypeUpdateApiException(
                                    httpStatusCode = 403,
                                    status = "A4030",
                                    message = "forbidden",
                                ),
                            ),
                            Result.success(
                                UserTypeResponseDto(
                                    userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                                    selectedPrimaryUserType = "LOW_VISION",
                                    selectedMobilitySubtype = null,
                                ),
                            ),
                        ),
                )
            val authRemoteDataSource =
                FakeProfileAuthRemoteDataSource(
                    reissueResponse =
                        ReissueResponseDto(
                            accessToken = "new-access-token",
                            refreshToken = "new-refresh-token",
                        ),
                )
            val repository =
                ServerProfileUserTypeUpdateRepository(
                    userTypeRemoteDataSource = userTypeRemoteDataSource,
                    authRemoteDataSource = authRemoteDataSource,
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            val result =
                repository.completeProfileEdit(
                    selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                    selectedMobilitySubtype = null,
                )

            assertEquals(
                ProfileUserTypeUpdateResult.Success(
                    selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                    selectedMobilitySubtype = null,
                ),
                result,
            )
            assertEquals(
                listOf("expired-access-token", "new-access-token"),
                userTypeRemoteDataSource.requestedAccessTokens,
            )
            assertEquals("refresh-token", authRemoteDataSource.latestRefreshToken)
            assertEquals("new-access-token", authSessionRepository.savedAuthSession?.accessToken)
            assertEquals("new-refresh-token", authSessionRepository.savedAuthSession?.refreshToken)
        }
}

private class FakeUserTypeRemoteDataSource(
    private val response: UserTypeResponseDto? = null,
    private val exception: Throwable? = null,
    private val queuedResults: List<Result<UserTypeResponseDto>> = emptyList(),
) : UserTypeRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "https://example.com")) {
    var latestAccessToken: String? = null
        private set
    var latestSelectedPrimaryUserType: String? = null
        private set
    var latestSelectedMobilitySubtype: String? = null
        private set
    val requestedAccessTokens = mutableListOf<String>()

    override suspend fun updateUserType(
        accessToken: String,
        selectedPrimaryUserType: String,
        selectedMobilitySubtype: String?,
    ): UserTypeResponseDto {
        latestAccessToken = accessToken
        requestedAccessTokens += accessToken
        latestSelectedPrimaryUserType = selectedPrimaryUserType
        latestSelectedMobilitySubtype = selectedMobilitySubtype
        if (queuedResults.isNotEmpty()) {
            val result = queuedResults[requestedAccessTokens.lastIndex]
            return result.getOrThrow()
        }
        exception?.let { throw it }
        return checkNotNull(response)
    }
}

private class FakeProfileAuthRemoteDataSource(
    private val reissueResponse: ReissueResponseDto =
        ReissueResponseDto(
            accessToken = "unused-access-token",
            refreshToken = "unused-refresh-token",
        ),
    private val reissueThrowable: Throwable? = null,
) : AuthRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "https://example.com")) {
    var latestRefreshToken: String? = null
        private set

    override suspend fun reissue(refreshToken: String): ReissueResponseDto {
        latestRefreshToken = refreshToken
        reissueThrowable?.let { throw it }
        return reissueResponse
    }
}

private class RecordingProfileAuthSessionRepository(
    private val authGateState: AuthGateState,
) : AuthSessionRepository {
    var savedAuthSession: AuthSession? = null
        private set
    var savedIsProfileCompleted: Boolean = false
        private set
    var clearAuthSessionCalled: Boolean = false
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

    override suspend fun clearSignupToken() = Unit

    override suspend fun markProfileCompleted() = Unit

    override suspend fun clearAuthSession() {
        clearAuthSessionCalled = true
    }
}

private class RecordingProfileSettingsRepository(
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
