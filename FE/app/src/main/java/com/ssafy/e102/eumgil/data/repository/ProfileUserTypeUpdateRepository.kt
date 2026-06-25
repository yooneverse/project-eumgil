package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.UserTypeRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.UserTypeUpdateApiException
import com.ssafy.e102.eumgil.data.remote.dto.UserTypeResponseDto

sealed interface ProfileUserTypeUpdateResult {
    data class Success(
        val selectedPrimaryUserType: String,
        val selectedMobilitySubtype: String?,
    ) : ProfileUserTypeUpdateResult

    data object MissingSession : ProfileUserTypeUpdateResult

    data object AuthenticationFailed : ProfileUserTypeUpdateResult

    data class Failure(
        val message: String,
    ) : ProfileUserTypeUpdateResult
}

interface ProfileUserTypeUpdateRepository {
    suspend fun completeProfileEdit(
        selectedPrimaryUserType: String,
        selectedMobilitySubtype: String?,
    ): ProfileUserTypeUpdateResult
}

fun provideProfileUserTypeUpdateRepository(
    baseUrl: String,
    authSessionRepository: AuthSessionRepository,
    settingsRepository: SettingsRepository,
    isMockMode: Boolean,
): ProfileUserTypeUpdateRepository =
    if (isMockMode) {
        LocalOnlyProfileUserTypeUpdateRepository(
            authSessionRepository = authSessionRepository,
            settingsRepository = settingsRepository,
        )
    } else {
        ServerProfileUserTypeUpdateRepository(
            userTypeRemoteDataSource = UserTypeRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = baseUrl)),
            authRemoteDataSource = AuthRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = baseUrl)),
            authSessionRepository = authSessionRepository,
            settingsRepository = settingsRepository,
        )
    }

class LocalOnlyProfileUserTypeUpdateRepository(
    private val authSessionRepository: AuthSessionRepository,
    private val settingsRepository: SettingsRepository,
) : ProfileUserTypeUpdateRepository {
    override suspend fun completeProfileEdit(
        selectedPrimaryUserType: String,
        selectedMobilitySubtype: String?,
    ): ProfileUserTypeUpdateResult {
        val authGateState = authSessionRepository.getAuthGateState()
        val authSession = authGateState.authSession ?: return ProfileUserTypeUpdateResult.MissingSession
        return runCatching {
            val request = selectedPrimaryUserType.toUserTypeUpdateRequest(selectedMobilitySubtype)
            synchronizeLocalState(
                authSession = authSession,
                response =
                    UserTypeResponseDto(
                        userId = authSession.userId,
                        selectedPrimaryUserType = request.selectedPrimaryUserType,
                        selectedMobilitySubtype = request.selectedMobilitySubtype,
                    ),
            )
        }.getOrElse { throwable ->
            ProfileUserTypeUpdateResult.Failure(
                message = throwable.message ?: DEFAULT_PROFILE_USER_TYPE_UPDATE_ERROR_MESSAGE,
            )
        }
    }

    private suspend fun synchronizeLocalState(
        authSession: AuthSession,
        response: UserTypeResponseDto,
    ): ProfileUserTypeUpdateResult {
        val synchronizedSession =
            authSession.copy(
                userId = response.userId ?: authSession.userId,
                selectedPrimaryUserType = response.selectedPrimaryUserType,
                selectedMobilitySubtype = response.selectedMobilitySubtype,
            )

        authSessionRepository.saveAuthSession(
            authSession = synchronizedSession,
            isProfileCompleted = true,
        )
        settingsRepository.syncOnboardingStateFromServer(
            selectedPrimaryUserType = response.selectedPrimaryUserType,
            selectedMobilitySubtype = response.selectedMobilitySubtype,
        )

        return ProfileUserTypeUpdateResult.Success(
            selectedPrimaryUserType = response.selectedPrimaryUserType.toPrimaryUserTypeRouteValue(),
            selectedMobilitySubtype = response.selectedMobilitySubtype?.toMobilitySubtypeRouteValue(),
        )
    }
}

class ServerProfileUserTypeUpdateRepository(
    private val userTypeRemoteDataSource: UserTypeRemoteDataSource,
    authRemoteDataSource: AuthRemoteDataSource,
    private val authSessionRepository: AuthSessionRepository,
    private val settingsRepository: SettingsRepository,
) : ProfileUserTypeUpdateRepository {
    private val authenticatedRequestRunner =
        AuthenticatedRequestRunner(
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )

    override suspend fun completeProfileEdit(
        selectedPrimaryUserType: String,
        selectedMobilitySubtype: String?,
    ): ProfileUserTypeUpdateResult =
        try {
            when (
                val result =
                    authenticatedRequestRunner.run(
                        execute = { authSession ->
                            val request = selectedPrimaryUserType.toUserTypeUpdateRequest(selectedMobilitySubtype)
                            val response =
                                userTypeRemoteDataSource.updateUserType(
                                    accessToken = authSession.accessToken,
                                    selectedPrimaryUserType = request.selectedPrimaryUserType,
                                    selectedMobilitySubtype = request.selectedMobilitySubtype,
                                )

                            val synchronizedSession =
                                authSession.copy(
                                    userId = response.userId ?: authSession.userId,
                                    selectedPrimaryUserType = response.selectedPrimaryUserType,
                                    selectedMobilitySubtype = response.selectedMobilitySubtype,
                                )

                            authSessionRepository.saveAuthSession(
                                authSession = synchronizedSession,
                                isProfileCompleted = true,
                            )
                            settingsRepository.syncOnboardingStateFromServer(
                                selectedPrimaryUserType = response.selectedPrimaryUserType,
                                selectedMobilitySubtype = response.selectedMobilitySubtype,
                            )

                            ProfileUserTypeUpdateResult.Success(
                                selectedPrimaryUserType = response.selectedPrimaryUserType.toPrimaryUserTypeRouteValue(),
                                selectedMobilitySubtype = response.selectedMobilitySubtype?.toMobilitySubtypeRouteValue(),
                            )
                        },
                        isAuthenticationFailure = ::isAuthenticationFailure,
                    )
            ) {
                AuthenticatedRequestResult.MissingSession -> ProfileUserTypeUpdateResult.MissingSession
                AuthenticatedRequestResult.AuthenticationFailed -> ProfileUserTypeUpdateResult.AuthenticationFailed
                is AuthenticatedRequestResult.Success -> result.value
            }
        } catch (exception: UserTypeUpdateApiException) {
            ProfileUserTypeUpdateResult.Failure(message = exception.message)
        } catch (exception: Exception) {
            ProfileUserTypeUpdateResult.Failure(
                message = exception.message ?: DEFAULT_PROFILE_USER_TYPE_UPDATE_ERROR_MESSAGE,
            )
        }
}

private data class UserTypeUpdateRequest(
    val selectedPrimaryUserType: String,
    val selectedMobilitySubtype: String?,
)

private fun String.toUserTypeUpdateRequest(selectedMobilitySubtype: String?): UserTypeUpdateRequest =
    when (this) {
        ROUTE_PRIMARY_USER_TYPE_LOW_VISION ->
            UserTypeUpdateRequest(
                selectedPrimaryUserType = toPrimaryUserTypeServerValue(),
                selectedMobilitySubtype = null,
            )

        ROUTE_PRIMARY_USER_TYPE_MOBILITY_IMPAIRED ->
            UserTypeUpdateRequest(
                selectedPrimaryUserType = toPrimaryUserTypeServerValue(),
                selectedMobilitySubtype =
                    selectedMobilitySubtype?.toMobilitySubtypeServerValue()
                        ?: throw IllegalStateException("보행약자 세부 유형을 먼저 선택해주세요."),
            )

        else -> throw IllegalStateException("지원하지 않는 사용자 유형입니다.")
    }

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val DEFAULT_PROFILE_USER_TYPE_UPDATE_ERROR_MESSAGE = "프로필 변경에 실패했습니다. 다시 시도해주세요."

private fun isAuthenticationFailure(throwable: Throwable): Boolean =
    throwable is UserTypeUpdateApiException &&
        (throwable.httpStatusCode == HTTP_UNAUTHORIZED || throwable.httpStatusCode == HTTP_FORBIDDEN)
