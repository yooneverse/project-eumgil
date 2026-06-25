package com.ssafy.e102.eumgil.core.config

import com.ssafy.e102.eumgil.BuildConfig

object AppEnvironment {
    val isDebugBuild: Boolean = BuildConfig.DEBUG
    val baseUrl: String = BuildConfig.BASE_URL
    val isMockMode: Boolean = BuildConfig.IS_MOCK_MODE
    val isDemoMode: Boolean = BuildConfig.IS_DEMO_MODE
    val voiceAlwaysRemote: Boolean = BuildConfig.VOICE_ALWAYS_REMOTE
}
