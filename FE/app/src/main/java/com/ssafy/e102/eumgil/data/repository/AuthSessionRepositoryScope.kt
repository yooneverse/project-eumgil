package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.resolveAccountScopeKey
import com.ssafy.e102.eumgil.core.model.resolveOnboardingScopeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal fun AuthSessionRepository.observeAccountScopeKey(): Flow<String?> =
    observeAuthGateState()
        .map { authGateState -> authGateState.authSession?.resolveAccountScopeKey() }
        .distinctUntilChanged()

internal suspend fun AuthSessionRepository.getAccountScopeKey(): String? =
    getAuthGateState().authSession?.resolveAccountScopeKey()

internal fun AuthSessionRepository.observeOnboardingScopeKey(): Flow<String> =
    observeAuthGateState()
        .map { authGateState -> authGateState.resolveOnboardingScopeKey() }
        .distinctUntilChanged()

internal suspend fun AuthSessionRepository.getOnboardingScopeKey(): String =
    getAuthGateState().resolveOnboardingScopeKey()

internal suspend fun AuthSessionRepository.getCurrentAuthSession(): AuthSession? =
    getAuthGateState().authSession
