package com.ssafy.e102.eumgil.feature.auth

import androidx.annotation.StringRes
import com.ssafy.e102.eumgil.R

data class AuthUiState(
    val loginStatus: AuthLoginStatus = AuthLoginStatus.Idle,
    val providers: List<AuthLoginProviderUiModel> = DefaultAuthLoginProviders,
) {
    val isLoading: Boolean
        get() = loginStatus is AuthLoginStatus.Loading

    val loadingProviderKey: String?
        get() = (loginStatus as? AuthLoginStatus.Loading)?.providerKey

    val errorMessage: String?
        get() = (loginStatus as? AuthLoginStatus.Error)?.message
}

data class AuthLoginProviderUiModel(
    val key: String,
    @StringRes val providerNameRes: Int,
    @StringRes val actionLabelRes: Int,
    val mark: String,
)

sealed interface AuthLoginStatus {
    data object Idle : AuthLoginStatus

    data class Loading(
        val providerKey: String,
    ) : AuthLoginStatus

    data class Error(
        val message: String,
    ) : AuthLoginStatus
}

sealed interface AuthUiAction {
    data class SocialLoginClicked(
        val providerKey: String,
    ) : AuthUiAction
}

sealed interface AuthUiEvent {
    data object EvaluateNextGate : AuthUiEvent
}

object AuthLoginProviderUiKeys {
    const val GOOGLE: String = "auth-ui-google"
    const val NAVER: String = "auth-ui-naver"
    const val KAKAO: String = "auth-ui-kakao"
}

val DefaultAuthLoginProviders =
    listOf(
        AuthLoginProviderUiModel(
            key = AuthLoginProviderUiKeys.GOOGLE,
            providerNameRes = R.string.auth_login_provider_google,
            actionLabelRes = R.string.auth_login_action_google,
            mark = "G",
        ),
        AuthLoginProviderUiModel(
            key = AuthLoginProviderUiKeys.NAVER,
            providerNameRes = R.string.auth_login_provider_naver,
            actionLabelRes = R.string.auth_login_action_naver,
            mark = "N",
        ),
        AuthLoginProviderUiModel(
            key = AuthLoginProviderUiKeys.KAKAO,
            providerNameRes = R.string.auth_login_provider_kakao,
            actionLabelRes = R.string.auth_login_action_kakao,
            mark = "K",
        ),
    )

const val DEFAULT_AUTH_LOGIN_ERROR_MESSAGE = "로그인을 시작하지 못했습니다. 잠시 후 다시 시도해주세요."
