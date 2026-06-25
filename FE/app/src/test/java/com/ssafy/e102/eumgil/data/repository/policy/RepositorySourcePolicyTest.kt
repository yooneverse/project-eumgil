package com.ssafy.e102.eumgil.data.repository.policy

import com.ssafy.e102.eumgil.core.config.AppEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositorySourcePolicyTest {
    @Test
    fun `settings domain always uses local only plan`() =
        runBlocking {
            val policy = DefaultRepositorySourcePolicy()

            assertEquals(
                RepositoryReadPlan.localOnly(),
                policy.readPlan(RepositoryDomain.SETTINGS),
            )
        }

    @Test
    fun `places and search domains depend only on build time mock mode`() =
        runBlocking {
            val policy = DefaultRepositorySourcePolicy()
            val expectedPlan =
                if (AppEnvironment.isMockMode) {
                    RepositoryReadPlan.mockOnly()
                } else {
                    RepositoryReadPlan(
                        sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
                    )
                }

            assertEquals(expectedPlan, policy.readPlan(RepositoryDomain.PLACES))
            assertEquals(expectedPlan, policy.readPlan(RepositoryDomain.SEARCH))
        }

    @Test
    fun `voice analyze domain stays remote when always remote flag is enabled even in mock mode`() =
        runBlocking {
            val policy = DefaultRepositorySourcePolicy()
            val expectedPlan =
                if (AppEnvironment.isMockMode && !AppEnvironment.voiceAlwaysRemote) {
                    RepositoryReadPlan.mockOnly()
                } else {
                    RepositoryReadPlan.remoteLocalOnly()
                }

            assertEquals(expectedPlan, policy.readPlan(RepositoryDomain.VOICE_ANALYZE))

            if (AppEnvironment.voiceAlwaysRemote) {
                // 플래그가 켜지면 isMockMode 값과 무관하게 항상 remoteLocalOnly() 여야 한다.
                assertEquals(
                    RepositoryReadPlan.remoteLocalOnly(),
                    policy.readPlan(RepositoryDomain.VOICE_ANALYZE),
                )
            } else {
                // 플래그가 꺼지면 PLACES/SEARCH 와 동일하게 isMockMode 에 따른다.
                assertEquals(
                    policy.readPlan(RepositoryDomain.PLACES),
                    policy.readPlan(RepositoryDomain.VOICE_ANALYZE),
                )
            }
        }
}
