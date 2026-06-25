package com.ssafy.e102.eumgil.app

import android.app.Application
import android.util.Log
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk
import com.navercorp.nid.NaverIdLoginSDK
import com.ssafy.e102.eumgil.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BusanEumgilApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        configureAppAudioPlaybackCapturePolicy(this)
        registerActivityLifecycleCallbacks(ForegroundActivityProvider)
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
            KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
        if (isNaverLoginConfigured()) {
            NaverIdLoginSDK.initialize(
                context = this,
                clientId = BuildConfig.NAVER_CLIENT_ID,
                clientSecret = BuildConfig.NAVER_CLIENT_SECRET,
                clientName = BuildConfig.NAVER_CLIENT_NAME,
            )
        }
        appContainer = AppContainer(context = this)
        recoverStaleReportOutboxes()
    }

    /**
     * Task 4.2 — 직전 프로세스가 비정상 종료되어 `Submitting` 상태로 굳어있던 outbox row를
     * `Pending`으로 되돌린다. 사용자가 같은 제보를 영원히 재시도 못하는 dead-end를 막는다.
     */
    private fun recoverStaleReportOutboxes() {
        applicationScope.launch {
            runCatching { appContainer.reportRepository.resetStaleSubmittingOutboxes() }
                .onSuccess { resetCount ->
                    if (resetCount > 0) {
                        Log.i(APP_LOG_TAG, "Reset $resetCount stale Submitting outbox(es) to Pending")
                    }
                }
                .onFailure { error ->
                    Log.w(APP_LOG_TAG, "Failed to reset stale Submitting outboxes", error)
                }
        }
    }

    private fun isNaverLoginConfigured(): Boolean =
        BuildConfig.NAVER_CLIENT_ID.isNotBlank() &&
            BuildConfig.NAVER_CLIENT_SECRET.isNotBlank() &&
            BuildConfig.NAVER_CLIENT_NAME.isNotBlank()

    private companion object {
        private const val APP_LOG_TAG = "BusanEumgilApp"
    }
}
