package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.data.local.dao.BookmarkDao
import com.ssafy.e102.eumgil.data.local.dao.FavoriteRouteDao
import com.ssafy.e102.eumgil.data.local.dao.ReportOutboxDao
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.UserApiException
import com.ssafy.e102.eumgil.data.remote.datasource.UserRemoteDataSource

sealed interface AccountWithdrawalResult {
    data class Success(
        val message: String,
    ) : AccountWithdrawalResult

    data object MissingSession : AccountWithdrawalResult

    data object AuthenticationFailed : AccountWithdrawalResult

    data class Failure(
        val message: String,
    ) : AccountWithdrawalResult
}

interface AccountWithdrawalRepository {
    suspend fun withdraw(): AccountWithdrawalResult
}

interface AccountWithdrawalLocalDataCleaner {
    suspend fun clearAfterWithdrawal()
}

fun provideAccountWithdrawalRepository(
    baseUrl: String,
    authSessionRepository: AuthSessionRepository,
    initSettingsRepository: InitSettingsRepository,
    bookmarkDao: BookmarkDao,
    favoriteRouteDao: FavoriteRouteDao,
    reportOutboxDao: ReportOutboxDao,
    isMockMode: Boolean,
): AccountWithdrawalRepository {
    val accountScopedLocalCacheCleaner =
        DefaultAccountScopedLocalCacheCleaner(
            authSessionRepository = authSessionRepository,
            bookmarkDao = bookmarkDao,
            favoriteRouteDao = favoriteRouteDao,
            reportOutboxDao = reportOutboxDao,
        )
    val localDataCleaner =
        DefaultAccountWithdrawalLocalDataCleaner(
            accountScopedLocalCacheCleaner = accountScopedLocalCacheCleaner,
            initSettingsRepository = initSettingsRepository,
        )

    return if (isMockMode) {
        LocalOnlyAccountWithdrawalRepository(
            authSessionRepository = authSessionRepository,
            localDataCleaner = localDataCleaner,
        )
    } else {
        ServerAccountWithdrawalRepository(
            userRemoteDataSource = UserRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = baseUrl)),
            authRemoteDataSource = AuthRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = baseUrl)),
            authSessionRepository = authSessionRepository,
            localDataCleaner = localDataCleaner,
        )
    }
}

class DefaultAccountWithdrawalLocalDataCleaner(
    private val accountScopedLocalCacheCleaner: AccountScopedLocalCacheCleaner,
    private val initSettingsRepository: InitSettingsRepository,
) : AccountWithdrawalLocalDataCleaner {
    override suspend fun clearAfterWithdrawal() {
        accountScopedLocalCacheCleaner.clearCurrentAccountCache()
        initSettingsRepository.clearInitSettings()
    }
}

class LocalOnlyAccountWithdrawalRepository(
    private val authSessionRepository: AuthSessionRepository,
    private val localDataCleaner: AccountWithdrawalLocalDataCleaner,
) : AccountWithdrawalRepository {
    override suspend fun withdraw(): AccountWithdrawalResult {
        if (authSessionRepository.getAuthGateState().authSession == null) {
            return AccountWithdrawalResult.MissingSession
        }

        runCatching { localDataCleaner.clearAfterWithdrawal() }
        authSessionRepository.clearAuthSession()

        return AccountWithdrawalResult.Success(message = DEFAULT_WITHDRAW_SUCCESS_MESSAGE)
    }
}

class ServerAccountWithdrawalRepository(
    private val userRemoteDataSource: UserRemoteDataSource,
    authRemoteDataSource: AuthRemoteDataSource,
    private val authSessionRepository: AuthSessionRepository,
    private val localDataCleaner: AccountWithdrawalLocalDataCleaner,
) : AccountWithdrawalRepository {
    private val authenticatedRequestRunner =
        AuthenticatedRequestRunner(
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )

    override suspend fun withdraw(): AccountWithdrawalResult =
        try {
            when (
                val result =
                    authenticatedRequestRunner.run(
                        execute = { authSession ->
                            val message = userRemoteDataSource.withdraw(accessToken = authSession.accessToken)
                            runCatching { localDataCleaner.clearAfterWithdrawal() }
                            authSessionRepository.clearAuthSession()
                            AccountWithdrawalResult.Success(message = message)
                        },
                        isAuthenticationFailure = ::isAuthenticationFailure,
                    )
            ) {
                AuthenticatedRequestResult.MissingSession -> AccountWithdrawalResult.MissingSession
                AuthenticatedRequestResult.AuthenticationFailed -> AccountWithdrawalResult.AuthenticationFailed
                is AuthenticatedRequestResult.Success -> result.value
            }
        } catch (exception: UserApiException) {
            if (exception.isAlreadyWithdrawn()) {
                runCatching { localDataCleaner.clearAfterWithdrawal() }
                authSessionRepository.clearAuthSession()
                AccountWithdrawalResult.Success(message = DEFAULT_WITHDRAW_SUCCESS_MESSAGE)
            } else {
                AccountWithdrawalResult.Failure(message = exception.message)
            }
        } catch (exception: Exception) {
            AccountWithdrawalResult.Failure(
                message = exception.message ?: DEFAULT_WITHDRAW_ERROR_MESSAGE,
            )
        }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val USER_NOT_FOUND_STATUS = "U4040"
private const val DEFAULT_WITHDRAW_SUCCESS_MESSAGE = "회원탈퇴가 완료되었습니다."
private const val DEFAULT_WITHDRAW_ERROR_MESSAGE = "회원탈퇴 처리에 실패했습니다. 다시 시도해주세요."

private fun isAuthenticationFailure(throwable: Throwable): Boolean =
    throwable is UserApiException &&
        (throwable.httpStatusCode == HTTP_UNAUTHORIZED || throwable.httpStatusCode == HTTP_FORBIDDEN)

private fun UserApiException.isAlreadyWithdrawn(): Boolean =
    httpStatusCode == HTTP_NOT_FOUND && status == USER_NOT_FOUND_STATUS
