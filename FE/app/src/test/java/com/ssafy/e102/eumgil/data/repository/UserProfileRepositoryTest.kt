package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.UserApiException
import com.ssafy.e102.eumgil.data.remote.dto.ReissueResponseDto
import com.ssafy.e102.eumgil.data.remote.datasource.UserRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.UserMeResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileRepositoryTest {
    @Test
    fun `sync my profile uses access token and updates auth session plus onboarding mirror`() =
        runTest {
            val authSessionRepository =
                RecordingUserProfileAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                    userId = "local-user-id",
                                    selectedPrimaryUserType = "LOW_VISION",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val settingsRepository =
                RecordingUserProfileSettingsRepository(
                    initialInitSettings =
                        InitSettings(
                            selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                            isLowVisionFollowUpCompleted = true,
                            isLocationTermsAgreed = true,
                            isPrivacyPolicyAgreed = true,
                        ),
                )
            val remoteDataSource =
                FakeUserRemoteDataSource(
                    userMeResponse =
                        UserMeResponseDto(
                            userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                            socialProvider = "KAKAO",
                            selectedPrimaryUserType = "MOBILITY_IMPAIRED",
                            selectedMobilitySubtype = "MANUAL_WHEELCHAIR",
                        ),
                )
            val repository =
                ServerUserProfileRepository(
                    userRemoteDataSource = remoteDataSource,
                    authRemoteDataSource = FakeUserProfileAuthRemoteDataSource(),
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            val result = repository.syncMyProfile()

            assertEquals("access-token", remoteDataSource.latestAccessToken)
            assertTrue(result is UserProfileSyncResult.Success)
            assertEquals(
                "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                authSessionRepository.savedAuthSession?.userId,
            )
            assertEquals("MOBILITY_IMPAIRED", authSessionRepository.savedAuthSession?.selectedPrimaryUserType)
            assertEquals("MANUAL_WHEELCHAIR", authSessionRepository.savedAuthSession?.selectedMobilitySubtype)
            assertEquals(ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED, settingsRepository.savedPrimaryUserType)
            assertEquals("manual_wheelchair", settingsRepository.savedMobilitySubtype)
            assertEquals(false, settingsRepository.savedLowVisionFollowUpCompleted)
            assertTrue(settingsRepository.savedLocationTermsAgreed)
            assertTrue(settingsRepository.savedPrivacyPolicyAgreed)
        }

    @Test
    fun `sync my profile without session returns missing session`() =
        runTest {
            val repository =
                ServerUserProfileRepository(
                    userRemoteDataSource =
                        FakeUserRemoteDataSource(
                            userMeResponse =
                                UserMeResponseDto(
                                    userId = "unused",
                                    socialProvider = "KAKAO",
                                    selectedPrimaryUserType = "LOW_VISION",
                                    selectedMobilitySubtype = null,
                                ),
                        ),
                    authRemoteDataSource = FakeUserProfileAuthRemoteDataSource(),
                    authSessionRepository =
                        RecordingUserProfileAuthSessionRepository(
                            authGateState = AuthGateState(),
                        ),
                    settingsRepository =
                        RecordingUserProfileSettingsRepository(
                            initialInitSettings = InitSettings(),
                        ),
                )

            val result = repository.syncMyProfile()

            assertSame(UserProfileSyncResult.MissingSession, result)
        }

    @Test
    fun `sync my profile reissue failure preserves local state and returns auth failure`() =
        runTest {
            val authSessionRepository =
                RecordingUserProfileAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "expired-access-token",
                                    refreshToken = "refresh-token",
                                    userId = "local-user-id",
                                    selectedPrimaryUserType = "LOW_VISION",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val settingsRepository =
                RecordingUserProfileSettingsRepository(
                    initialInitSettings =
                        InitSettings(
                            selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                            isLowVisionFollowUpCompleted = true,
                        ),
                )
            val repository =
                ServerUserProfileRepository(
                    userRemoteDataSource =
                        FakeUserRemoteDataSource(
                            failure =
                                UserApiException(
                                    httpStatusCode = 401,
                                    status = "UNAUTHORIZED",
                                    message = "unauthorized",
                                ),
                        ),
                    authRemoteDataSource =
                        FakeUserProfileAuthRemoteDataSource(
                            reissueThrowable = IllegalStateException("reissue failed"),
                        ),
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            val result = repository.syncMyProfile()

            assertSame(UserProfileSyncResult.AuthenticationFailed, result)
            assertTrue(authSessionRepository.clearAuthSessionCalled)
            assertNull(authSessionRepository.savedAuthSession)
            assertNull(settingsRepository.savedPrimaryUserType)
            assertNull(settingsRepository.savedMobilitySubtype)
        }

    @Test
    fun `sync my profile retries once after reissue success and stores rotated tokens`() =
        runTest {
            val authSessionRepository =
                RecordingUserProfileAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "expired-access-token",
                                    refreshToken = "refresh-token",
                                    userId = "local-user-id",
                                    selectedPrimaryUserType = "LOW_VISION",
                                ),
                            isProfileCompleted = true,
                        ),
                )
            val settingsRepository =
                RecordingUserProfileSettingsRepository(
                    initialInitSettings =
                        InitSettings(
                            selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_LOW_VISION,
                            isLowVisionFollowUpCompleted = true,
                        ),
                )
            val userRemoteDataSource =
                FakeUserRemoteDataSource(
                    queuedResults =
                        listOf(
                            Result.failure(
                                UserApiException(
                                    httpStatusCode = 401,
                                    status = "A4010",
                                    message = "expired",
                                ),
                            ),
                            Result.success(
                                UserMeResponseDto(
                                    userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                                    socialProvider = "KAKAO",
                                    selectedPrimaryUserType = "MOBILITY_IMPAIRED",
                                    selectedMobilitySubtype = "MANUAL_WHEELCHAIR",
                                ),
                            ),
                        ),
                )
            val authRemoteDataSource =
                FakeUserProfileAuthRemoteDataSource(
                    reissueResponse =
                        ReissueResponseDto(
                            accessToken = "new-access-token",
                            refreshToken = "new-refresh-token",
                        ),
                )
            val repository =
                ServerUserProfileRepository(
                    userRemoteDataSource = userRemoteDataSource,
                    authRemoteDataSource = authRemoteDataSource,
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            val result = repository.syncMyProfile()

            assertTrue(result is UserProfileSyncResult.Success)
            assertEquals(
                listOf("expired-access-token", "new-access-token"),
                userRemoteDataSource.requestedAccessTokens,
            )
            assertEquals("refresh-token", authRemoteDataSource.latestRefreshToken)
            assertEquals("new-access-token", authSessionRepository.savedAuthSession?.accessToken)
            assertEquals("new-refresh-token", authSessionRepository.savedAuthSession?.refreshToken)
        }

    @Test
    fun `sync my profile clears stale mobility subtype when server user type is low vision`() =
        runTest {
            val authSessionRepository =
                RecordingUserProfileAuthSessionRepository(
                    authGateState =
                        AuthGateState(
                            authSession =
                                AuthSession(
                                    accessToken = "access-token",
                                    refreshToken = "refresh-token",
                                    userId = "local-user-id",
                                    selectedPrimaryUserType = "MOBILITY_IMPAIRED",
                                    selectedMobilitySubtype = "MANUAL_WHEELCHAIR",
                                ),
                            isProfileCompleted = false,
                        ),
                )
            val settingsRepository =
                RecordingUserProfileSettingsRepository(
                    initialInitSettings =
                        InitSettings(
                            selectedPrimaryUserType = ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED,
                            selectedMobilitySubtype = "manual_wheelchair",
                        ),
                )
            val repository =
                ServerUserProfileRepository(
                    userRemoteDataSource =
                        FakeUserRemoteDataSource(
                            userMeResponse =
                                UserMeResponseDto(
                                    userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                                    socialProvider = "KAKAO",
                                    selectedPrimaryUserType = "LOW_VISION",
                                    selectedMobilitySubtype = null,
                                ),
                        ),
                    authRemoteDataSource = FakeUserProfileAuthRemoteDataSource(),
                    authSessionRepository = authSessionRepository,
                    settingsRepository = settingsRepository,
                )

            repository.syncMyProfile()

            assertEquals("LOW_VISION", authSessionRepository.savedAuthSession?.selectedPrimaryUserType)
            assertNull(authSessionRepository.savedAuthSession?.selectedMobilitySubtype)
            assertTrue(authSessionRepository.savedIsProfileCompleted)
            assertEquals(ROUTE_PRIMARY_USER_TYPE_LOW_VISION, settingsRepository.savedPrimaryUserType)
            assertEquals(true, settingsRepository.savedLowVisionFollowUpCompleted)
        }
}

private class FakeUserRemoteDataSource(
    private val userMeResponse: UserMeResponseDto? = null,
    private val failure: Throwable? = null,
    private val queuedResults: List<Result<UserMeResponseDto>> = emptyList(),
) : UserRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "https://example.com")) {
    var latestAccessToken: String? = null
        private set
    val requestedAccessTokens = mutableListOf<String>()

    override suspend fun getMe(accessToken: String): UserMeResponseDto {
        latestAccessToken = accessToken
        requestedAccessTokens += accessToken
        if (queuedResults.isNotEmpty()) {
            val result = queuedResults[requestedAccessTokens.lastIndex]
            return result.getOrThrow()
        }
        failure?.let { throw it }
        return checkNotNull(userMeResponse)
    }
}

private class FakeUserProfileAuthRemoteDataSource(
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

private class RecordingUserProfileAuthSessionRepository(
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

private class RecordingUserProfileSettingsRepository(
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
