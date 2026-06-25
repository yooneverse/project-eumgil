package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.LOCAL_ONLY_AUTH_SESSION_MARKER
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.SocialLoginResponseDto
import kotlinx.coroutines.delay

data class AuthLoginRequest(
    val providerKey: String,
)

interface AuthLoginRepository {
    suspend fun login(request: AuthLoginRequest)
}

class LocalOnlyAuthLoginRepository(
    private val authSessionRepository: AuthSessionRepository,
    private val handoffDelayMillis: Long = LOCAL_ONLY_LOGIN_DELAY_MILLIS,
) : AuthLoginRepository {
    override suspend fun login(request: AuthLoginRequest) {
        require(request.providerKey.isNotBlank()) { "로그인 방식을 다시 선택해주세요." }

        delay(handoffDelayMillis)
        // Social login is not wired yet, so any supported provider click advances the
        // user through the app's next gate with a local mock session.
        authSessionRepository.saveAuthSession(
            authSession = AuthSession(accessToken = LOCAL_ONLY_AUTH_SESSION_MARKER),
            isProfileCompleted = true,
        )
    }

    private companion object {
        private const val LOCAL_ONLY_LOGIN_DELAY_MILLIS = 450L
    }
}

class ServerAuthLoginRepository(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val socialAccessTokenProvider: SocialAccessTokenProvider,
    private val authSessionRepository: AuthSessionRepository,
    private val settingsRepository: SettingsRepository,
) : AuthLoginRepository {
    override suspend fun login(request: AuthLoginRequest) {
        val provider =
            AuthSocialProvider.fromProviderKey(request.providerKey)
                ?: throw IllegalArgumentException("로그인 방식을 다시 선택해주세요.")
        val socialAccessToken = socialAccessTokenProvider.getAccessToken(provider)
        val response =
            authRemoteDataSource.socialLogin(
                socialProvider = provider.serverValue,
                socialAccessToken = socialAccessToken,
            )

        if (response.signupRequired) {
            val signupToken =
                response.signupToken
                    ?: throw IllegalStateException("회원가입 토큰을 받지 못했습니다.")
            settingsRepository.clearInitSettings()
            authSessionRepository.saveSignupToken(signupToken = signupToken)
            return
        }

        saveExistingUserSession(response)
    }

    private suspend fun saveExistingUserSession(response: SocialLoginResponseDto) {
        val accessToken =
            response.accessToken
                ?: throw IllegalStateException("서비스 access token을 받지 못했습니다.")
        val refreshToken =
            response.refreshToken
                ?: throw IllegalStateException("서비스 refresh token을 받지 못했습니다.")
        val selectedPrimaryUserType =
            response.selectedPrimaryUserType
                ?: throw IllegalStateException("사용자 유형을 받지 못했습니다.")

        authSessionRepository.saveAuthSession(
            authSession =
                AuthSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = response.userId,
                    selectedPrimaryUserType = selectedPrimaryUserType,
                    selectedMobilitySubtype = response.selectedMobilitySubtype,
                ),
            isProfileCompleted = true,
        )
        settingsRepository.syncOnboardingStateFromServer(
            selectedPrimaryUserType = selectedPrimaryUserType,
            selectedMobilitySubtype = response.selectedMobilitySubtype,
        )
    }
}
