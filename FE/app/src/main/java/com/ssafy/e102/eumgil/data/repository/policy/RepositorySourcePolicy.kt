package com.ssafy.e102.eumgil.data.repository.policy

import com.ssafy.e102.eumgil.core.config.AppEnvironment

enum class RepositoryDomain {
    PLACES,
    SEARCH,
    SETTINGS,
    VOICE_ANALYZE,
}

enum class RepositorySource {
    REMOTE,
    LOCAL,
    MOCK,
}

data class RepositoryReadPlan(
    val sources: List<RepositorySource>,
) {
    init {
        require(sources.isNotEmpty()) { "Repository read plan requires at least one source." }
    }

    companion object {
        fun remoteLocalMock(): RepositoryReadPlan =
            RepositoryReadPlan(
                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL, RepositorySource.MOCK),
            )

        fun remoteLocalOnly(): RepositoryReadPlan =
            RepositoryReadPlan(
                sources = listOf(RepositorySource.REMOTE, RepositorySource.LOCAL),
            )

        fun localOnly(): RepositoryReadPlan =
            RepositoryReadPlan(
                sources = listOf(RepositorySource.LOCAL),
            )

        fun mockOnly(): RepositoryReadPlan =
            RepositoryReadPlan(
                sources = listOf(RepositorySource.MOCK),
            )
    }
}

interface RepositorySourcePolicy {
    suspend fun readPlan(domain: RepositoryDomain): RepositoryReadPlan
}

class DefaultRepositorySourcePolicy : RepositorySourcePolicy {
    override suspend fun readPlan(domain: RepositoryDomain): RepositoryReadPlan =
        when (domain) {
            RepositoryDomain.SETTINGS -> RepositoryReadPlan.localOnly()
            RepositoryDomain.PLACES,
            RepositoryDomain.SEARCH ->
                if (AppEnvironment.isMockMode) {
                    RepositoryReadPlan.mockOnly()
                } else {
                    RepositoryReadPlan.remoteLocalOnly()
                }
            RepositoryDomain.VOICE_ANALYZE ->
                if (AppEnvironment.isMockMode && !AppEnvironment.voiceAlwaysRemote) {
                    RepositoryReadPlan.mockOnly()
                } else {
                    RepositoryReadPlan.remoteLocalOnly()
                }
        }
}
