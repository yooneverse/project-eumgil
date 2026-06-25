package com.ssafy.e102.eumgil.data.repository

import android.app.Application
import android.content.Context
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SocialAccessTokenProviderTest {
    @Test
    fun `kakao login prefers foreground activity context when available`() =
        runTest {
            val appContext = NamedMockContext("app")
            val activityContext = NamedMockContext("activity")
            val kakaoLoginClient =
                FakeKakaoLoginClient(
                    isKakaoTalkLoginAvailable = false,
                    accountToken = fakeOAuthToken(accessToken = "kakao-access-token"),
                )
            val provider =
                KakaoSocialAccessTokenProvider(
                    context = appContext,
                    activityContextProvider = { activityContext },
                    kakaoLoginClient = kakaoLoginClient,
                )

            val accessToken = provider.getAccessToken(AuthSocialProvider.KAKAO)

            assertEquals("kakao-access-token", accessToken)
            assertSame(activityContext, kakaoLoginClient.lastAvailabilityContext)
            assertSame(activityContext, kakaoLoginClient.lastAccountLoginContext)
        }

    @Test
    fun `kakao login falls back to application context when no activity is available`() =
        runTest {
            val appContext = NamedMockContext("app")
            val kakaoLoginClient =
                FakeKakaoLoginClient(
                    isKakaoTalkLoginAvailable = false,
                    accountToken = fakeOAuthToken(accessToken = "kakao-access-token"),
                )
            val provider =
                KakaoSocialAccessTokenProvider(
                    context = appContext,
                    activityContextProvider = { null },
                    kakaoLoginClient = kakaoLoginClient,
                )

            provider.getAccessToken(AuthSocialProvider.KAKAO)

            assertSame(appContext, kakaoLoginClient.lastAvailabilityContext)
            assertSame(appContext, kakaoLoginClient.lastAccountLoginContext)
        }

    @Test
    fun `kakao talk login is preferred when available`() =
        runTest {
            val activityContext = NamedMockContext("activity")
            val kakaoLoginClient =
                FakeKakaoLoginClient(
                    isKakaoTalkLoginAvailable = true,
                    talkToken = fakeOAuthToken(accessToken = "kakao-talk-access-token"),
                    talkError = null,
                )
            val provider =
                KakaoSocialAccessTokenProvider(
                    context = NamedMockContext("app"),
                    activityContextProvider = { activityContext },
                    kakaoLoginClient = kakaoLoginClient,
                )

            val accessToken = provider.getAccessToken(AuthSocialProvider.KAKAO)

            assertEquals("kakao-talk-access-token", accessToken)
            assertSame(activityContext, kakaoLoginClient.lastTalkLoginContext)
            assertEquals(1, kakaoLoginClient.talkLoginCallCount)
            assertEquals(0, kakaoLoginClient.accountLoginCallCount)
        }

    @Test
    fun `kakao talk login falls back to kakao account on non cancellation error`() =
        runTest {
            val activityContext = NamedMockContext("activity")
            val kakaoLoginClient =
                FakeKakaoLoginClient(
                    isKakaoTalkLoginAvailable = true,
                    talkError = IllegalStateException("talk login failed"),
                    accountToken = fakeOAuthToken(accessToken = "kakao-account-access-token"),
                )
            val provider =
                KakaoSocialAccessTokenProvider(
                    context = NamedMockContext("app"),
                    activityContextProvider = { activityContext },
                    kakaoLoginClient = kakaoLoginClient,
                )

            val accessToken = provider.getAccessToken(AuthSocialProvider.KAKAO)

            assertEquals("kakao-account-access-token", accessToken)
            assertSame(activityContext, kakaoLoginClient.lastTalkLoginContext)
            assertSame(activityContext, kakaoLoginClient.lastAccountLoginContext)
            assertEquals(1, kakaoLoginClient.talkLoginCallCount)
            assertEquals(1, kakaoLoginClient.accountLoginCallCount)
        }

    @Test
    fun `kakao talk cancellation does not fall back to kakao account`() =
        runTest {
            val cancelError = createCancelledClientError()
            val kakaoLoginClient =
                FakeKakaoLoginClient(
                    isKakaoTalkLoginAvailable = true,
                    talkError = cancelError,
                    accountToken = fakeOAuthToken(accessToken = "unused-account-token"),
                )
            val provider =
                KakaoSocialAccessTokenProvider(
                    context = NamedMockContext("app"),
                    activityContextProvider = { NamedMockContext("activity") },
                    kakaoLoginClient = kakaoLoginClient,
                )

            val result =
                runCatching {
                    provider.getAccessToken(AuthSocialProvider.KAKAO)
                }

            assertSame(cancelError, result.exceptionOrNull())
            assertEquals(1, kakaoLoginClient.talkLoginCallCount)
            assertEquals(0, kakaoLoginClient.accountLoginCallCount)
            assertNull(kakaoLoginClient.lastAccountLoginContext)
        }
}

private class FakeKakaoLoginClient(
    private val isKakaoTalkLoginAvailable: Boolean,
    private val talkToken: OAuthToken? = null,
    private val talkError: Throwable? = IllegalStateException("Talk login should not be called in this test."),
    private val accountToken: OAuthToken? = null,
    private val accountError: Throwable? = null,
) : KakaoLoginClient {
    var lastAvailabilityContext: Context? = null
        private set
    var lastTalkLoginContext: Context? = null
        private set
    var lastAccountLoginContext: Context? = null
        private set
    var talkLoginCallCount: Int = 0
        private set
    var accountLoginCallCount: Int = 0
        private set

    override fun isKakaoTalkLoginAvailable(context: Context): Boolean {
        lastAvailabilityContext = context
        return isKakaoTalkLoginAvailable
    }

    override fun loginWithKakaoTalk(
        context: Context,
        callback: (OAuthToken?, Throwable?) -> Unit,
    ) {
        talkLoginCallCount += 1
        lastTalkLoginContext = context
        callback(talkToken, talkError)
    }

    override fun loginWithKakaoAccount(
        context: Context,
        callback: (OAuthToken?, Throwable?) -> Unit,
    ) {
        accountLoginCallCount += 1
        lastAccountLoginContext = context
        callback(accountToken, accountError)
    }
}

private class NamedMockContext(
    private val label: String,
) : Application() {
    override fun toString(): String = "NamedMockContext($label)"
}

private fun fakeOAuthToken(accessToken: String): OAuthToken =
    OAuthToken(
        accessToken,
        Date(0),
        "refresh-token",
        Date(0),
        null,
        emptyList(),
    )

private fun createCancelledClientError(): ClientError {
    val constructors = ClientError::class.java.declaredConstructors
    for (constructor in constructors) {
        constructor.isAccessible = true
        val args =
            constructor.parameterTypes.map { parameterType ->
                when (parameterType) {
                    ClientErrorCause::class.java -> ClientErrorCause.Cancelled
                    String::class.java -> "cancelled"
                    Throwable::class.java -> null
                    Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> false
                    Int::class.javaPrimitiveType, Int::class.javaObjectType -> 0
                    Long::class.javaPrimitiveType, Long::class.javaObjectType -> 0L
                    else -> null
                }
            }.toTypedArray()
        val instance = runCatching { constructor.newInstance(*args) as ClientError }.getOrNull()
        if (instance != null) {
            return instance
        }
    }
    error("Unable to instantiate ClientError for cancellation test.")
}
