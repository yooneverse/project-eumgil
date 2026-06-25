package com.ssafy.e102.eumgil.feature.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.data.repository.AccountWithdrawalRepository
import com.ssafy.e102.eumgil.data.repository.AccountWithdrawalResult
import com.ssafy.e102.eumgil.data.repository.AuthLogoutRepository
import com.ssafy.e102.eumgil.data.repository.AuthLogoutResult
import com.ssafy.e102.eumgil.data.repository.AuthSessionRepository
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.repository.SettingsRepository
import com.ssafy.e102.eumgil.data.repository.UserProfileRepository
import com.ssafy.e102.eumgil.data.repository.UserProfileSyncResult
import com.ssafy.e102.eumgil.feature.onboarding.MobilitySubtype
import com.ssafy.e102.eumgil.feature.onboarding.PrimaryUserType
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MyPageViewModel(
    private val settingsRepository: SettingsRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val authLogoutRepository: AuthLogoutRepository,
    private val userProfileRepository: UserProfileRepository,
    private val bookmarkRepository: BookmarkRepository? = null,
    private val routeBookmarkRepository: RouteBookmarkRepository? = null,
    private val reportRepository: ReportRepository? = null,
    private val searchRepository: SearchRepository? = null,
    private val accountWithdrawalRepository: AccountWithdrawalRepository? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MyPageUiState())
    val uiState: StateFlow<MyPageUiState> = mutableUiState.asStateFlow()

    private val uiEventChannel = Channel<MyPageUiEvent>(capacity = Channel.BUFFERED)
    val uiEvent = uiEventChannel.receiveAsFlow()
    private var logoutJob: Job? = null
    private var withdrawJob: Job? = null

    init {
        observeInitSettings()
        observeMyPageStats()
        refreshRecentNavigationCount()
        refreshMyProfile()
    }

    fun onAction(action: MyPageUiAction) {
        when (action) {
            MyPageUiAction.UserTypeChangeClicked -> {
                viewModelScope.launch {
                    uiEventChannel.send(MyPageUiEvent.NavigateToUserTypePrimary)
                }
            }
            MyPageUiAction.LogoutClicked -> logout()
            MyPageUiAction.WithdrawClicked -> withdrawAccount()
            is MyPageUiAction.MainMenuClicked -> {
                viewModelScope.launch {
                    when (action.menuItem) {
                        MyPageMenuItem.TEXT_SIZE -> uiEventChannel.send(MyPageUiEvent.NavigateToTextSizeSetting)
                        MyPageMenuItem.APP_HELP -> uiEventChannel.send(MyPageUiEvent.NavigateToGuide)
                        MyPageMenuItem.PRIVACY_POLICY -> uiEventChannel.send(MyPageUiEvent.OpenPrivacyPolicy)
                        MyPageMenuItem.SERVICE_TERMS -> uiEventChannel.send(MyPageUiEvent.OpenServiceTerms)
                        MyPageMenuItem.NOTICE -> uiEventChannel.send(MyPageUiEvent.ShowPreparingMessage)
                    }
                }
            }
        }
    }

    private fun observeMyPageStats() {
        viewModelScope.launch {
            val placeBookmarkCountFlow =
                bookmarkRepository
                    ?.observeBookmarks()
                    ?.map { bookmarks -> bookmarks.size }
                    ?: flowOf(0)
            val routeBookmarkCountFlow =
                routeBookmarkRepository
                    ?.observeRouteBookmarks()
                    ?.map { bookmarks -> bookmarks.size }
                    ?: flowOf(0)
            val reportHistoryCountFlow =
                reportRepository
                    ?.observeReportHistoryEntries()
                    ?.map { reports -> reports.size }
                    ?: flowOf(0)

            combine(
                placeBookmarkCountFlow,
                routeBookmarkCountFlow,
                reportHistoryCountFlow,
            ) { placeBookmarkCount, routeBookmarkCount, reportHistoryCount ->
                Triple(placeBookmarkCount, routeBookmarkCount, reportHistoryCount)
            }.collectLatest { (placeBookmarkCount, routeBookmarkCount, reportHistoryCount) ->
                mutableUiState.update { state ->
                    state.copy(
                        placeBookmarkCount = placeBookmarkCount,
                        routeBookmarkCount = routeBookmarkCount,
                        reportHistoryCount = reportHistoryCount,
                    )
                }
            }
        }
    }

    private fun refreshRecentNavigationCount() {
        val repository = searchRepository ?: return
        viewModelScope.launch {
            val recentNavigationCount =
                runCatching { repository.getRecentDestinations().size }
                    .getOrDefault(0)

            mutableUiState.update { state ->
                state.copy(recentNavigationCount = recentNavigationCount)
            }
        }
    }

    private fun logout() {
        if (mutableUiState.value.isLogoutLoading) return

        mutableUiState.update { state -> state.copy(isLogoutLoading = true) }
        logoutJob?.cancel()
        logoutJob =
            viewModelScope.launch {
                when (val result = authLogoutRepository.logout()) {
                    is AuthLogoutResult.Success -> finishLogout()
                    AuthLogoutResult.MissingSession,
                    AuthLogoutResult.AuthenticationFailed
                    -> finishLogout()
                    is AuthLogoutResult.Failure -> {
                        mutableUiState.update { state -> state.copy(isLogoutLoading = false) }
                        uiEventChannel.send(MyPageUiEvent.ShowSnackbar(message = result.message))
                    }
                }
            }
    }

    private fun withdrawAccount() {
        val repository = accountWithdrawalRepository
        if (repository == null) {
            viewModelScope.launch {
                uiEventChannel.send(MyPageUiEvent.ShowPreparingMessage)
            }
            return
        }
        if (mutableUiState.value.isWithdrawLoading) return

        mutableUiState.update { state -> state.copy(isWithdrawLoading = true) }
        withdrawJob?.cancel()
        withdrawJob =
            viewModelScope.launch {
                when (val result = repository.withdraw()) {
                    is AccountWithdrawalResult.Success -> finishWithdrawal()
                    AccountWithdrawalResult.MissingSession,
                    AccountWithdrawalResult.AuthenticationFailed,
                    -> finishWithdrawal()
                    is AccountWithdrawalResult.Failure -> {
                        mutableUiState.update { state -> state.copy(isWithdrawLoading = false) }
                        uiEventChannel.send(MyPageUiEvent.ShowSnackbar(message = result.message))
                    }
                }
            }
    }

    private fun observeInitSettings() {
        viewModelScope.launch {
            settingsRepository.observeInitSettings().collectLatest { initSettings ->
                mutableUiState.update { currentState ->
                    currentState.copy(
                        userMode = initSettings.toMyPageUserMode(),
                        mobilitySubtype = initSettings.toMyPageMobilitySubtype(),
                    )
                }
            }
        }
    }

    private fun refreshMyProfile() {
        viewModelScope.launch {
            when (userProfileRepository.syncMyProfile()) {
                is UserProfileSyncResult.Success -> Unit
                UserProfileSyncResult.MissingSession,
                UserProfileSyncResult.AuthenticationFailed
                -> {
                    authSessionRepository.clearAuthSession()
                    uiEventChannel.send(MyPageUiEvent.NavigateToLogin)
                }
                is UserProfileSyncResult.Failure -> {
                    uiEventChannel.send(MyPageUiEvent.ShowProfileSyncFailedMessage)
                }
            }
        }
    }

    private suspend fun finishLogout() {
        mutableUiState.update { state -> state.copy(isLogoutLoading = false) }
        uiEventChannel.send(MyPageUiEvent.NavigateToLogin)
    }

    private suspend fun finishWithdrawal() {
        mutableUiState.update { state -> state.copy(isWithdrawLoading = false) }
        uiEventChannel.send(MyPageUiEvent.NavigateToLogin)
    }

    override fun onCleared() {
        logoutJob?.cancel()
        withdrawJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(
            settingsRepository: SettingsRepository,
            authSessionRepository: AuthSessionRepository,
            authLogoutRepository: AuthLogoutRepository,
            userProfileRepository: UserProfileRepository,
            bookmarkRepository: BookmarkRepository? = null,
            routeBookmarkRepository: RouteBookmarkRepository? = null,
            reportRepository: ReportRepository? = null,
            searchRepository: SearchRepository? = null,
            accountWithdrawalRepository: AccountWithdrawalRepository? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MyPageViewModel::class.java)) {
                        return MyPageViewModel(
                            settingsRepository = settingsRepository,
                            authSessionRepository = authSessionRepository,
                            authLogoutRepository = authLogoutRepository,
                            userProfileRepository = userProfileRepository,
                            bookmarkRepository = bookmarkRepository,
                            routeBookmarkRepository = routeBookmarkRepository,
                            reportRepository = reportRepository,
                            searchRepository = searchRepository,
                            accountWithdrawalRepository = accountWithdrawalRepository,
                        ) as T
                    }

                    error("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}

private fun InitSettings.toMyPageUserMode(): MyPageUserMode =
    when (PrimaryUserType.fromRouteValue(selectedPrimaryUserType)) {
        PrimaryUserType.LOW_VISION -> MyPageUserMode.LOW_VISION
        PrimaryUserType.MOBILITY_IMPAIRED -> MyPageUserMode.MOBILITY_IMPAIRED
        null -> MyPageUserMode.UNKNOWN
    }

private fun InitSettings.toMyPageMobilitySubtype(): MyPageMobilitySubtype? =
    when (MobilitySubtype.fromRouteValue(selectedMobilitySubtype)) {
        MobilitySubtype.ELECTRIC_WHEELCHAIR -> MyPageMobilitySubtype.ELECTRIC_WHEELCHAIR
        MobilitySubtype.MANUAL_WHEELCHAIR -> MyPageMobilitySubtype.MANUAL_WHEELCHAIR
        MobilitySubtype.OTHER -> MyPageMobilitySubtype.OTHER
        null -> null
    }
