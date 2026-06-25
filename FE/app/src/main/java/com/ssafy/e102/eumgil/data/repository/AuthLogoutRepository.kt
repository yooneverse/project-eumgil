package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.data.remote.datasource.AuthApiException
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource

sealed interface AuthLogoutResult {
    data class Success(
        val message: String,
    ) : AuthLogoutResult

    data object MissingSession : AuthLogoutResult

    data object AuthenticationFailed : AuthLogoutResult

    data class Failure(
        val message: String,
    ) : AuthLogoutResult
}

interface AuthLogoutRepository {
    suspend fun logout(): AuthLogoutResult
}

fun provideAuthLogoutRepository(
    authRemoteDataSource: AuthRemoteDataSource,
    authSessionRepository: AuthSessionRepository,
    bookmarkDao: com.ssafy.e102.eumgil.data.local.dao.BookmarkDao,
    favoriteRouteDao: com.ssafy.e102.eumgil.data.local.dao.FavoriteRouteDao,
    reportOutboxDao: com.ssafy.e102.eumgil.data.local.dao.ReportOutboxDao,
    placesLocalDataSource: com.ssafy.e102.eumgil.data.local.datasource.PlacesLocalDataSource,
    destinationSelectionRepository: DestinationSelectionRepository,
    destinationPreviewRepository: DestinationPreviewRepository,
    isMockMode: Boolean,
): AuthLogoutRepository {
    val localCacheCleaner =
        DefaultAccountScopedLocalCacheCleaner(
            authSessionRepository = authSessionRepository,
            bookmarkDao = bookmarkDao,
            favoriteRouteDao = favoriteRouteDao,
            reportOutboxDao = reportOutboxDao,
            placesLocalDataSource = placesLocalDataSource,
            destinationSelectionRepository = destinationSelectionRepository,
            destinationPreviewRepository = destinationPreviewRepository,
        )
    return if (isMockMode) {
        LocalOnlyAuthLogoutRepository(
            authSessionRepository = authSessionRepository,
            localCacheCleaner = localCacheCleaner,
        )
    } else {
        ServerAuthLogoutRepository(
            authRemoteDataSource = authRemoteDataSource,
            authSessionRepository = authSessionRepository,
            localCacheCleaner = localCacheCleaner,
        )
    }
}

class LocalOnlyAuthLogoutRepository(
    private val authSessionRepository: AuthSessionRepository,
    private val localCacheCleaner: AccountScopedLocalCacheCleaner,
) : AuthLogoutRepository {
    override suspend fun logout(): AuthLogoutResult {
        if (authSessionRepository.getAuthGateState().authSession == null) {
            return AuthLogoutResult.MissingSession
        }

        localCacheCleaner.clearCurrentAccountCache()
        authSessionRepository.clearAuthSession()
        return AuthLogoutResult.Success(message = DEFAULT_LOGOUT_SUCCESS_MESSAGE)
    }
}

class ServerAuthLogoutRepository(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val authSessionRepository: AuthSessionRepository,
    private val localCacheCleaner: AccountScopedLocalCacheCleaner,
) : AuthLogoutRepository {
    override suspend fun logout(): AuthLogoutResult {
        val authSession = authSessionRepository.getAuthGateState().authSession ?: return AuthLogoutResult.MissingSession

        return try {
            val message = authRemoteDataSource.logout(accessToken = authSession.accessToken)
            localCacheCleaner.clearCurrentAccountCache()
            authSessionRepository.clearAuthSession()
            AuthLogoutResult.Success(message = message)
        } catch (exception: AuthApiException) {
            if (exception.httpStatusCode == HTTP_UNAUTHORIZED || exception.httpStatusCode == HTTP_FORBIDDEN) {
                localCacheCleaner.clearCurrentAccountCache()
                authSessionRepository.clearAuthSession()
                AuthLogoutResult.AuthenticationFailed
            } else {
                AuthLogoutResult.Failure(message = exception.message)
            }
        } catch (exception: Exception) {
            AuthLogoutResult.Failure(
                message = exception.message ?: DEFAULT_LOGOUT_ERROR_MESSAGE,
            )
        }
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val DEFAULT_LOGOUT_SUCCESS_MESSAGE = "로그아웃되었습니다."
private const val DEFAULT_LOGOUT_ERROR_MESSAGE = "로그아웃 처리에 실패했습니다. 다시 시도해 주세요."
