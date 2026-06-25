package com.ssafy.e102.eumgil.feature.mypage

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RouteBookmark
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDetail
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDraft
import com.ssafy.e102.eumgil.core.model.RouteBookmarkSaveRequest
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.data.repository.AccountWithdrawalRepository
import com.ssafy.e102.eumgil.data.repository.AccountWithdrawalResult
import com.ssafy.e102.eumgil.data.repository.AuthLogoutRepository
import com.ssafy.e102.eumgil.data.repository.AuthLogoutResult
import com.ssafy.e102.eumgil.data.repository.AuthSessionRepository
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.ReportHistoryData
import com.ssafy.e102.eumgil.data.repository.ReportHistoryDetailData
import com.ssafy.e102.eumgil.data.repository.ReportHistorySource
import com.ssafy.e102.eumgil.data.repository.ReportOutboxData
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.ReportSubmitResult
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.repository.SettingsRepository
import com.ssafy.e102.eumgil.data.repository.UserProfile
import com.ssafy.e102.eumgil.data.repository.UserProfileRepository
import com.ssafy.e102.eumgil.data.repository.UserProfileSyncResult
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlin.coroutines.resume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyPageViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `user type change action emits onboarding navigation event`() =
        runTest {
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = FakeAuthLogoutRepository(),
                    userProfileRepository = FakeUserProfileRepository(),
                )

            viewModel.onAction(MyPageUiAction.UserTypeChangeClicked)
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertSame(MyPageUiEvent.NavigateToUserTypePrimary, event)
        }

    @Test
    fun `text size menu action emits text size navigation event`() =
        runTest {
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = FakeAuthLogoutRepository(),
                    userProfileRepository = FakeUserProfileRepository(),
                )

            viewModel.onAction(MyPageUiAction.MainMenuClicked(MyPageMenuItem.TEXT_SIZE))
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertSame(MyPageUiEvent.NavigateToTextSizeSetting, event)
        }

    @Test
    fun `logout action exposes loading state and emits login navigation event on success`() =
        runTest {
            val logoutRepository = ControllableAuthLogoutRepository()
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = logoutRepository,
                    userProfileRepository = FakeUserProfileRepository(),
                )
            val event = backgroundScope.async { viewModel.uiEvent.first() }
            val loadingStates =
                backgroundScope.async {
                    viewModel.uiState
                        .map { state -> state.isLogoutLoading }
                        .take(3)
                        .toList()
                }
            runCurrent()

            viewModel.onAction(MyPageUiAction.LogoutClicked)
            runCurrent()

            assertEquals(1, logoutRepository.logoutCallCount)
            assertTrue(viewModel.uiState.value.isLogoutLoading)

            logoutRepository.complete(AuthLogoutResult.Success(message = "로그아웃되었습니다."))
            runCurrent()

            assertEquals(
                listOf(false, true, false),
                loadingStates.await(),
            )
            assertSame(MyPageUiEvent.NavigateToLogin, event.await())
        }

    @Test
    fun `logout failure keeps user on my page and emits snackbar message`() =
        runTest {
            val logoutRepository = ControllableAuthLogoutRepository()
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = logoutRepository,
                    userProfileRepository = FakeUserProfileRepository(),
                )
            val event = async { viewModel.uiEvent.first() }

            viewModel.onAction(MyPageUiAction.LogoutClicked)
            runCurrent()
            logoutRepository.complete(AuthLogoutResult.Failure(message = "로그아웃 처리에 실패했습니다."))
            runCurrent()

            assertEquals(false, viewModel.uiState.value.isLogoutLoading)
            assertEquals(
                MyPageUiEvent.ShowSnackbar(message = "로그아웃 처리에 실패했습니다."),
                event.await(),
            )
        }

    @Test
    fun `logout authentication failure emits login navigation event`() =
        runTest {
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository =
                        FakeAuthLogoutRepository(
                            result = AuthLogoutResult.AuthenticationFailed,
                        ),
                    userProfileRepository = FakeUserProfileRepository(),
                )

            viewModel.onAction(MyPageUiAction.LogoutClicked)
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertSame(MyPageUiEvent.NavigateToLogin, event)
        }

    @Test
    fun `logout missing session emits login navigation event`() =
        runTest {
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository =
                        FakeAuthLogoutRepository(
                            result = AuthLogoutResult.MissingSession,
                        ),
                    userProfileRepository = FakeUserProfileRepository(),
                )

            viewModel.onAction(MyPageUiAction.LogoutClicked)
            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertSame(MyPageUiEvent.NavigateToLogin, event)
        }

    @Test
    fun `logout ignores duplicate taps while request is in progress`() =
        runTest {
            val logoutRepository = ControllableAuthLogoutRepository()
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = logoutRepository,
                    userProfileRepository = FakeUserProfileRepository(),
                )

            viewModel.onAction(MyPageUiAction.LogoutClicked)
            runCurrent()
            viewModel.onAction(MyPageUiAction.LogoutClicked)
            runCurrent()

            assertEquals(1, logoutRepository.logoutCallCount)
            assertTrue(viewModel.uiState.value.isLogoutLoading)
        }

    @Test
    fun `withdraw action exposes loading state and emits login navigation event on success`() =
        runTest {
            val withdrawalRepository = ControllableAccountWithdrawalRepository()
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = FakeAuthLogoutRepository(),
                    userProfileRepository = FakeUserProfileRepository(),
                    accountWithdrawalRepository = withdrawalRepository,
                )
            val event = async { viewModel.uiEvent.first() }
            val loadingStates =
                async {
                    viewModel.uiState
                        .map { state -> state.isWithdrawLoading }
                        .take(3)
                        .toList()
                }
            runCurrent()

            viewModel.onAction(MyPageUiAction.WithdrawClicked)
            runCurrent()

            assertEquals(1, withdrawalRepository.withdrawCallCount)
            assertTrue(viewModel.uiState.value.isWithdrawLoading)

            withdrawalRepository.complete(AccountWithdrawalResult.Success(message = "회원탈퇴가 완료되었습니다."))
            runCurrent()

            assertEquals(
                listOf(false, true, false),
                loadingStates.await(),
            )
            assertSame(MyPageUiEvent.NavigateToLogin, event.await())
        }

    @Test
    fun `withdraw failure keeps user on my page and emits snackbar message`() =
        runTest {
            val withdrawalRepository = ControllableAccountWithdrawalRepository()
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = FakeAuthLogoutRepository(),
                    userProfileRepository = FakeUserProfileRepository(),
                    accountWithdrawalRepository = withdrawalRepository,
                )
            val event = async { viewModel.uiEvent.first() }

            viewModel.onAction(MyPageUiAction.WithdrawClicked)
            runCurrent()
            withdrawalRepository.complete(AccountWithdrawalResult.Failure(message = "회원탈퇴 처리에 실패했습니다."))
            runCurrent()

            assertEquals(false, viewModel.uiState.value.isWithdrawLoading)
            assertEquals(
                MyPageUiEvent.ShowSnackbar(message = "회원탈퇴 처리에 실패했습니다."),
                event.await(),
            )
        }

    @Test
    fun `profile sync success updates ui state from synchronized local mirror`() =
        runTest {
            val settingsRepository = FakeSettingsRepository()
            val viewModel =
                MyPageViewModel(
                    settingsRepository = settingsRepository,
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = FakeAuthLogoutRepository(),
                    userProfileRepository =
                        FakeUserProfileRepository(
                            onSync = {
                                settingsRepository.savePrimaryUserType("mobility_impaired")
                                settingsRepository.saveMobilitySubtype("manual_wheelchair")
                                UserProfileSyncResult.Success(
                                    UserProfile(
                                        userId = "018f7f6c-2b7e-7c3a-9f4a-8b4e3b7c9a01",
                                        socialProvider = "KAKAO",
                                        selectedPrimaryUserType = "MOBILITY_IMPAIRED",
                                        selectedMobilitySubtype = "MANUAL_WHEELCHAIR",
                                    ),
                                )
                            },
                        ),
                )

            advanceUntilIdle()

            assertEquals(MyPageUserMode.MOBILITY_IMPAIRED, viewModel.uiState.value.userMode)
            assertEquals(MyPageMobilitySubtype.MANUAL_WHEELCHAIR, viewModel.uiState.value.mobilitySubtype)
        }

    @Test
    fun `profile sync auth failure clears session and emits login navigation event`() =
        runTest {
            val authSessionRepository = FakeAuthSessionRepository()
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = authSessionRepository,
                    authLogoutRepository = FakeAuthLogoutRepository(),
                    userProfileRepository =
                        FakeUserProfileRepository(
                            result = UserProfileSyncResult.AuthenticationFailed,
                        ),
                )

            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertTrue(authSessionRepository.clearAuthSessionCalled)
            assertSame(MyPageUiEvent.NavigateToLogin, event)
        }

    @Test
    fun `profile sync network failure keeps local fallback and emits error message event`() =
        runTest {
            val viewModel =
                MyPageViewModel(
                    settingsRepository =
                        FakeSettingsRepository(
                            initSettings =
                                InitSettings(
                                    selectedPrimaryUserType = "low_vision",
                                    isLowVisionFollowUpCompleted = true,
                                ),
                        ),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = FakeAuthLogoutRepository(),
                    userProfileRepository =
                        FakeUserProfileRepository(
                            result = UserProfileSyncResult.Failure(message = "network error"),
                        ),
                )

            advanceUntilIdle()

            val event =
                withTimeoutOrNull(100) {
                    viewModel.uiEvent.first()
                }

            assertEquals(MyPageUserMode.LOW_VISION, viewModel.uiState.value.userMode)
            assertSame(MyPageUiEvent.ShowProfileSyncFailedMessage, event)
        }

    @Test
    fun `my page stats are derived from reports bookmarks and recent destinations`() =
        runTest {
            val viewModel =
                MyPageViewModel(
                    settingsRepository = FakeSettingsRepository(),
                    authSessionRepository = FakeAuthSessionRepository(),
                    authLogoutRepository = FakeAuthLogoutRepository(),
                    userProfileRepository = FakeUserProfileRepository(),
                    bookmarkRepository = FakeBookmarkRepository(count = 2),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(count = 3),
                    reportRepository = FakeReportRepository(count = 4),
                    searchRepository = FakeSearchRepository(recentDestinationCount = 5),
                )

            advanceUntilIdle()

            assertEquals(4, viewModel.uiState.value.reportHistoryCount)
            assertEquals(2, viewModel.uiState.value.placeBookmarkCount)
            assertEquals(3, viewModel.uiState.value.routeBookmarkCount)
            assertEquals(5, viewModel.uiState.value.recentNavigationCount)
        }
}

private class FakeSettingsRepository(
    initSettings: InitSettings = InitSettings(),
) : SettingsRepository {
    private val initSettingsFlow = MutableStateFlow(initSettings)

    override fun observeInitSettings(): Flow<InitSettings> = initSettingsFlow

    override suspend fun getInitSettings(): InitSettings = initSettingsFlow.value

    override suspend fun savePrimaryUserType(selectedPrimaryUserType: String) {
        initSettingsFlow.value = initSettingsFlow.value.copy(selectedPrimaryUserType = selectedPrimaryUserType)
    }

    override suspend fun saveMobilitySubtype(selectedMobilitySubtype: String) {
        initSettingsFlow.value = initSettingsFlow.value.copy(selectedMobilitySubtype = selectedMobilitySubtype)
    }

    override suspend fun saveLowVisionFollowUpCompleted(isCompleted: Boolean) {
        initSettingsFlow.value = initSettingsFlow.value.copy(isLowVisionFollowUpCompleted = isCompleted)
    }

    override suspend fun saveLocationTermsAgreement(
        isLocationTermsAgreed: Boolean,
        isPrivacyPolicyAgreed: Boolean,
    ) {
        initSettingsFlow.value =
            initSettingsFlow.value.copy(
                isLocationTermsAgreed = isLocationTermsAgreed,
                isPrivacyPolicyAgreed = isPrivacyPolicyAgreed,
            )
    }

    override suspend fun clearInitSettings() {
        initSettingsFlow.value = InitSettings()
    }
}

private class FakeAuthSessionRepository : AuthSessionRepository {
    var clearAuthSessionCalled = false
    private val authGateStateEvents = MutableSharedFlow<AuthGateState>()

    override fun observeAuthGateState(): Flow<AuthGateState> = authGateStateEvents

    override suspend fun getAuthGateState(): AuthGateState = AuthGateState()

    override suspend fun saveAuthSession(
        authSession: AuthSession,
        isProfileCompleted: Boolean,
    ) = Unit

    override suspend fun saveSignupToken(signupToken: String) = Unit

    override suspend fun clearSignupToken() = Unit

    override suspend fun markProfileCompleted() = Unit

    override suspend fun clearAuthSession() {
        clearAuthSessionCalled = true
    }
}

private class FakeAuthLogoutRepository(
    private val result: AuthLogoutResult = AuthLogoutResult.Success(message = "로그아웃되었습니다."),
) : AuthLogoutRepository {
    override suspend fun logout(): AuthLogoutResult = result
}

private class ControllableAuthLogoutRepository : AuthLogoutRepository {
    var logoutCallCount: Int = 0
        private set

    private var continuation: kotlinx.coroutines.CancellableContinuation<AuthLogoutResult>? = null

    override suspend fun logout(): AuthLogoutResult {
        logoutCallCount += 1
        return kotlinx.coroutines.suspendCancellableCoroutine { nextContinuation ->
            continuation = nextContinuation
        }
    }

    fun complete(result: AuthLogoutResult) {
        val currentContinuation = requireNotNull(continuation)
        continuation = null
        currentContinuation.resume(result)
    }
}

private class ControllableAccountWithdrawalRepository : AccountWithdrawalRepository {
    var withdrawCallCount: Int = 0
        private set

    private var continuation: kotlinx.coroutines.CancellableContinuation<AccountWithdrawalResult>? = null

    override suspend fun withdraw(): AccountWithdrawalResult {
        withdrawCallCount += 1
        return kotlinx.coroutines.suspendCancellableCoroutine { nextContinuation ->
            continuation = nextContinuation
        }
    }

    fun complete(result: AccountWithdrawalResult) {
        val currentContinuation = requireNotNull(continuation)
        continuation = null
        currentContinuation.resume(result)
    }
}

private class FakeUserProfileRepository(
    private val result: UserProfileSyncResult =
        UserProfileSyncResult.Success(
            UserProfile(
                userId = null,
                socialProvider = null,
                selectedPrimaryUserType = null,
                selectedMobilitySubtype = null,
            ),
        ),
    private val onSync: (suspend () -> UserProfileSyncResult)? = null,
) : UserProfileRepository {
    override suspend fun syncMyProfile(): UserProfileSyncResult = onSync?.invoke() ?: result
}

private class FakeBookmarkRepository(
    private val count: Int,
) : BookmarkRepository {
    override fun observeBookmarks(): Flow<List<BookmarkData>> =
        flowOf(
            List(count) { index ->
                BookmarkData(
                    placeId = "place-$index",
                    placeName = "장소 $index",
                    address = null,
                    latitude = 35.0,
                    longitude = 129.0,
                    category = null,
                )
            },
        )

    override suspend fun isBookmarked(placeId: String): Boolean = false

    override suspend fun saveBookmark(bookmark: BookmarkData): BookmarkData = bookmark

    override suspend fun deleteBookmark(placeId: String) = Unit
}

private class FakeRouteBookmarkRepository(
    private val count: Int,
) : RouteBookmarkRepository {
    override fun observeRouteBookmarks(): Flow<List<RouteBookmark>> =
        flowOf(
            List(count) { index ->
                RouteBookmark(
                    bookmarkId = "route-$index",
                    routeName = "경로 $index",
                    startLabel = "출발",
                    endLabel = "도착",
                    startPoint = GeoCoordinate(latitude = 35.0, longitude = 129.0),
                    endPoint = GeoCoordinate(latitude = 35.1, longitude = 129.1),
                    routeOption = RouteOption.SAFE,
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                )
            },
        )

    override suspend fun isBookmarked(draft: RouteBookmarkDraft): Boolean = false

    override suspend fun getRouteBookmarkDetail(bookmarkId: String): RouteBookmarkDetail? = null

    override suspend fun saveRouteBookmark(request: RouteBookmarkSaveRequest): RouteBookmark =
        error("not used")

    override suspend fun deleteRouteBookmark(bookmarkId: String) = Unit
}

private class FakeReportRepository(
    private val count: Int,
) : ReportRepository {
    override fun observeReportHistory(): Flow<List<ReportOutboxData>> = flowOf(emptyList())

    override fun observeReportHistoryEntries(): Flow<List<ReportHistoryData>> =
        flowOf(
            List(count) { index ->
                ReportHistoryData(
                    historyId = "report-$index",
                    reportCategory = "OTHER_OBSTACLE",
                    processingStatus = null,
                    description = null,
                    address = null,
                    latitude = 35.0,
                    longitude = 129.0,
                    photoUri = null,
                    imageUrl = null,
                    source = ReportHistorySource.LocalOutbox,
                    serverReportId = null,
                    createdAtMillis = index.toLong(),
                    updatedAtMillis = index.toLong(),
                )
            },
        )

    override suspend fun getReportHistoryDetail(historyId: String): ReportHistoryDetailData? = null

    override suspend fun getLatestDraft() = null

    override suspend fun saveDraft(draft: com.ssafy.e102.eumgil.data.repository.ReportDraftData) = draft

    override suspend fun deleteDraft(draftId: String) = Unit

    override suspend fun saveOutbox(outbox: ReportOutboxData): ReportOutboxData = outbox

    override suspend fun submitOutboxToServer(outboxId: String): ReportSubmitResult = ReportSubmitResult.Skipped
}

private class FakeSearchRepository(
    private val recentDestinationCount: Int,
) : SearchRepository {
    override suspend fun search(query: SearchQuery): List<SearchResult> = emptyList()

    override suspend fun getRecentSearches() = emptyList<com.ssafy.e102.eumgil.core.model.RecentSearch>()

    override suspend fun saveRecentSearch(keyword: String) = Unit

    override suspend fun getRecentDestinations(): List<RecentDestination> =
        List(recentDestinationCount) { index ->
            RecentDestination(
                placeId = "recent-$index",
                name = "최근 목적지 $index",
                address = null,
                latitude = 35.0,
                longitude = 129.0,
            )
        }

    override suspend fun saveRecentDestination(destination: RecentDestination) = Unit
}
