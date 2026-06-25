package com.ssafy.e102.eumgil.data.local.datasource

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ssafy.e102.eumgil.core.model.AuthSession
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionLocalDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `server mode ignores stale local only auth session`() =
        runTest {
            val dataSource = createDataSource(allowLocalOnlySession = false)
            dataSource.saveAuthSession(
                authSession = AuthSession(accessToken = "local-only-auth-session"),
                isProfileCompleted = true,
            )

            val authGateState = dataSource.getAuthGateState()

            assertNull(authGateState.authSession)
            assertFalse(authGateState.isProfileCompleted)
        }

    @Test
    fun `local only auth session is blocked by default`() =
        runTest {
            val dataSource = createDataSource()
            dataSource.saveAuthSession(
                authSession = AuthSession(accessToken = "local-only-auth-session"),
                isProfileCompleted = true,
            )

            val authGateState = dataSource.getAuthGateState()

            assertNull(authGateState.authSession)
            assertFalse(authGateState.isProfileCompleted)
        }

    private fun TestScope.createDataSource(): AuthSessionLocalDataSource {
        val file = File(temporaryFolder.root, "auth_session_default.preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file },
            )

        return AuthSessionLocalDataSource(dataStore = dataStore)
    }

    private fun TestScope.createDataSource(allowLocalOnlySession: Boolean): AuthSessionLocalDataSource {
        val file = File(temporaryFolder.root, "auth_session.preferences_pb")
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { file },
            )

        return AuthSessionLocalDataSource(
            dataStore = dataStore,
            allowLocalOnlySession = allowLocalOnlySession,
        )
    }
}
