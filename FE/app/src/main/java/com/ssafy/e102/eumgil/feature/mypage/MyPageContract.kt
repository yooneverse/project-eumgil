package com.ssafy.e102.eumgil.feature.mypage

data class MyPageUiState(
    val displayName: String? = null,
    val userMode: MyPageUserMode = MyPageUserMode.UNKNOWN,
    val mobilitySubtype: MyPageMobilitySubtype? = null,
    val reportHistoryCount: Int = 0,
    val placeBookmarkCount: Int = 0,
    val routeBookmarkCount: Int = 0,
    val recentNavigationCount: Int = 0,
    val isLogoutLoading: Boolean = false,
    val isWithdrawLoading: Boolean = false,
)

enum class MyPageUserMode {
    LOW_VISION,
    MOBILITY_IMPAIRED,
    UNKNOWN,
}

enum class MyPageMobilitySubtype {
    ELECTRIC_WHEELCHAIR,
    MANUAL_WHEELCHAIR,
    OTHER,
}

enum class MyPageMenuItem {
    TEXT_SIZE,
    NOTICE,
    APP_HELP,
    PRIVACY_POLICY,
    SERVICE_TERMS,
}

sealed interface MyPageUiAction {
    data object UserTypeChangeClicked : MyPageUiAction

    data object LogoutClicked : MyPageUiAction

    data object WithdrawClicked : MyPageUiAction

    data class MainMenuClicked(
        val menuItem: MyPageMenuItem,
    ) : MyPageUiAction
}

sealed interface MyPageUiEvent {
    data object NavigateToUserTypePrimary : MyPageUiEvent

    data object NavigateToLogin : MyPageUiEvent

    data object NavigateToGuide : MyPageUiEvent

    data object NavigateToTextSizeSetting : MyPageUiEvent

    data object OpenPrivacyPolicy : MyPageUiEvent

    data object OpenServiceTerms : MyPageUiEvent

    data object ShowPreparingMessage : MyPageUiEvent

    data object ShowProfileSyncFailedMessage : MyPageUiEvent

    data class ShowSnackbar(
        val message: String,
    ) : MyPageUiEvent
}
