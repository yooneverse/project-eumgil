package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeHistoryItem
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeIntent
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeMode
import com.ssafy.e102.eumgil.core.model.VoiceAnalyzeResult
import com.ssafy.e102.eumgil.data.mock.datasource.MockVoiceAnalyzeRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.VoiceAnalyzeApiException
import com.ssafy.e102.eumgil.data.remote.datasource.VoiceAnalyzeRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.VoiceAnalyzeHistoryDto
import com.ssafy.e102.eumgil.data.repository.policy.RepositoryDomain
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySource
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySourcePolicy

interface VoiceAnalyzeRepository {
    suspend fun analyze(
        text: String,
        mode: VoiceAnalyzeMode,
        history: List<VoiceAnalyzeHistoryItem> = emptyList(),
        currentRoute: String? = null,
    ): VoiceAnalyzeResult
}

class DefaultVoiceAnalyzeRepository(
    private val remoteDataSource: VoiceAnalyzeRemoteDataSource,
    private val mockDataSource: MockVoiceAnalyzeRemoteDataSource,
    private val sourcePolicy: RepositorySourcePolicy,
    authSessionRepository: AuthSessionRepository? = null,
    authRemoteDataSource: AuthRemoteDataSource? = null,
) : VoiceAnalyzeRepository {
    private val authenticatedRequestRunner =
        if (authSessionRepository != null && authRemoteDataSource != null) {
            AuthenticatedRequestRunner(
                authSessionRepository = authSessionRepository,
                authRemoteDataSource = authRemoteDataSource,
            )
        } else {
            null
        }

    override suspend fun analyze(
        text: String,
        mode: VoiceAnalyzeMode,
        history: List<VoiceAnalyzeHistoryItem>,
        currentRoute: String?,
    ): VoiceAnalyzeResult {
        val readPlan = sourcePolicy.readPlan(RepositoryDomain.VOICE_ANALYZE)
        val historyDtos = history.map { VoiceAnalyzeHistoryDto(role = it.role, content = it.content) }
        var remoteFailure: Throwable? = null

        for (source in readPlan.sources) {
            when (source) {
                RepositorySource.REMOTE -> {
                    val result = runCatching {
                        runAuthenticatedRemoteRequest {
                            remoteDataSource.analyze(
                                text = text,
                                mode = mode.name,
                                history = historyDtos,
                                currentRoute = currentRoute,
                            )
                        }
                    }
                    if (result.isSuccess) {
                        return result.getOrThrow().toVoiceAnalyzeResult()
                    }
                    remoteFailure = result.exceptionOrNull()
                }

                RepositorySource.MOCK -> {
                    return mockDataSource
                        .analyze(
                            text = text,
                            mode = mode.name,
                            history = historyDtos,
                            currentRoute = currentRoute,
                        )
                        .toVoiceAnalyzeResult()
                }

                RepositorySource.LOCAL -> {
                    // LOCAL source is not supported for voice analyze — skip
                }
            }
        }

        throw remoteFailure
            ?: IllegalStateException("No voice analyze data source matched the current policy.")
    }

    private suspend fun <T> runAuthenticatedRemoteRequest(execute: suspend () -> T): T {
        val runner = authenticatedRequestRunner ?: return execute()

        return when (
            val result =
                runner.run(
                    execute = { execute() },
                    isAuthenticationFailure = ::isAuthenticationFailure,
                )
        ) {
            AuthenticatedRequestResult.MissingSession ->
                throw VoiceAnalyzeApiException(
                    httpStatusCode = HTTP_UNAUTHORIZED,
                    message = AUTH_REQUIRED_MESSAGE,
                )

            AuthenticatedRequestResult.AuthenticationFailed ->
                throw VoiceAnalyzeApiException(
                    httpStatusCode = HTTP_UNAUTHORIZED,
                    message = AUTH_REQUIRED_MESSAGE,
                )

            is AuthenticatedRequestResult.Success -> result.value
        }
    }

    private fun isAuthenticationFailure(throwable: Throwable): Boolean =
        throwable is VoiceAnalyzeApiException &&
            throwable.httpStatusCode == HTTP_UNAUTHORIZED

    private fun com.ssafy.e102.eumgil.data.remote.dto.VoiceAnalyzeResponseDto.toVoiceAnalyzeResult(): VoiceAnalyzeResult =
        VoiceAnalyzeResult(
            intent = runCatching { enumValueOf<VoiceAnalyzeIntent>(intent) }.getOrDefault(VoiceAnalyzeIntent.UNKNOWN),
            placeName = placeName,
            category = category,
            bookmarkAction = bookmarkAction,
            departure = departure,
            destination = destination,
            reportType = reportType,
            description = description,
            confirmed = confirmed,
            confirmationMessage = confirmationMessage,
        )

    private companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val AUTH_REQUIRED_MESSAGE = "인증이 필요합니다."
    }
}
