package com.ssafy.e102.eumgil.core.model

data class AuthSessionSnapshot(
    val isAuthenticated: Boolean,
    val isProfileCompleted: Boolean,
    val source: AuthSessionSource = AuthSessionSource.LOCAL_MOCK,
) {
    companion object {
        val LocalMockReady: AuthSessionSnapshot =
            AuthSessionSnapshot(
                isAuthenticated = true,
                isProfileCompleted = true,
            )
    }
}

enum class AuthSessionSource {
    LOCAL_MOCK,
}
