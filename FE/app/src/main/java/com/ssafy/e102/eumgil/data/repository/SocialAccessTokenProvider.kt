package com.ssafy.e102.eumgil.data.repository

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.ssafy.e102.eumgil.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface SocialAccessTokenProvider {
    suspend fun getAccessToken(provider: AuthSocialProvider): String
}

internal interface KakaoLoginClient {
    fun isKakaoTalkLoginAvailable(context: Context): Boolean

    fun loginWithKakaoTalk(
        context: Context,
        callback: (OAuthToken?, Throwable?) -> Unit,
    )

    fun loginWithKakaoAccount(
        context: Context,
        callback: (OAuthToken?, Throwable?) -> Unit,
    )
}

private object DefaultKakaoLoginClient : KakaoLoginClient {
    override fun isKakaoTalkLoginAvailable(context: Context): Boolean =
        UserApiClient.instance.isKakaoTalkLoginAvailable(context)

    override fun loginWithKakaoTalk(
        context: Context,
        callback: (OAuthToken?, Throwable?) -> Unit,
    ) {
        UserApiClient.instance.loginWithKakaoTalk(context, callback = callback)
    }

    override fun loginWithKakaoAccount(
        context: Context,
        callback: (OAuthToken?, Throwable?) -> Unit,
    ) {
        UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
    }
}

class CompositeSocialAccessTokenProvider(
    private val providersBySocialProvider: Map<AuthSocialProvider, SocialAccessTokenProvider>,
) : SocialAccessTokenProvider {
    override suspend fun getAccessToken(provider: AuthSocialProvider): String =
        providersBySocialProvider[provider]?.getAccessToken(provider)
            ?: throw IllegalStateException("${provider.displayName} Android login key and SDK connection are required.")
}

class UnavailableSocialAccessTokenProvider : SocialAccessTokenProvider {
    override suspend fun getAccessToken(provider: AuthSocialProvider): String {
        throw IllegalStateException("${provider.displayName} Android login key and SDK connection are required.")
    }
}

internal class KakaoSocialAccessTokenProvider(
    private val context: Context,
    private val activityContextProvider: () -> Context? = { null },
    private val kakaoLoginClient: KakaoLoginClient = DefaultKakaoLoginClient,
) : SocialAccessTokenProvider {
    override suspend fun getAccessToken(provider: AuthSocialProvider): String {
        requireKakaoProvider(provider)
        requireKakaoNativeAppKey()
        return suspendCancellableCoroutine { continuation ->
            startKakaoLogin(
                loginContext = resolveLoginContext(),
                callback =
                    createAccessTokenCallback(
                        onSuccess = { accessToken ->
                            if (continuation.isActive) {
                                continuation.resume(accessToken)
                            }
                        },
                        onFailure = { error ->
                            if (continuation.isActive) {
                                continuation.resumeWithException(error)
                            }
                        },
                    ),
            )
        }
    }

    private fun resolveLoginContext(): Context = activityContextProvider() ?: context

    private fun requireKakaoProvider(provider: AuthSocialProvider) {
        if (provider != AuthSocialProvider.KAKAO) {
            throw IllegalStateException("${provider.displayName} Android login key and SDK connection are required.")
        }
    }

    private fun requireKakaoNativeAppKey() {
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
            throw IllegalStateException("Kakao Native App Key is required.")
        }
    }

    private fun startKakaoLogin(
        loginContext: Context,
        callback: (OAuthToken?, Throwable?) -> Unit,
    ) {
        if (!kakaoLoginClient.isKakaoTalkLoginAvailable(loginContext)) {
            kakaoLoginClient.loginWithKakaoAccount(loginContext, callback = callback)
            return
        }

        kakaoLoginClient.loginWithKakaoTalk(loginContext) { token, error ->
            when {
                error is ClientError && error.reason == ClientErrorCause.Cancelled ->
                    callback(null, error)
                error != null ->
                    kakaoLoginClient.loginWithKakaoAccount(loginContext, callback = callback)
                else -> callback(token, null)
            }
        }
    }

    private fun createAccessTokenCallback(
        onSuccess: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): (OAuthToken?, Throwable?) -> Unit =
        { token, error ->
            when {
                error != null -> onFailure(error)
                token != null -> onSuccess(token.accessToken)
                else -> onFailure(IllegalStateException("Kakao login did not return an access token."))
            }
        }
}

class GoogleSocialAccessTokenProvider(
    private val activityProvider: () -> Activity?,
) : SocialAccessTokenProvider {
    private var authorizationLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var launcherOwnerIdentity: Int? = null
    private var authorizationResultHandler: ((Result<AuthorizationResult>) -> Unit)? = null

    override suspend fun getAccessToken(provider: AuthSocialProvider): String {
        if (provider != AuthSocialProvider.GOOGLE) {
            throw IllegalStateException("${provider.displayName} Android login key and SDK connection are required.")
        }
        val activityContext =
            activityProvider() as? ComponentActivity
                ?: throw IllegalStateException("Google login requires a foreground ComponentActivity.")

        return requestGoogleAccessToken(activityContext)
    }

    private suspend fun requestGoogleAccessToken(activity: ComponentActivity): String {
        val authorizationResult =
            requestAuthorization(
                activity = activity,
                request =
                    AuthorizationRequest.builder()
                        .setRequestedScopes(GOOGLE_USERINFO_SCOPES)
                        .build(),
            )

        return authorizationResult.accessToken
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google login did not return an access token.")
    }

    private suspend fun requestAuthorization(
        activity: ComponentActivity,
        request: AuthorizationRequest,
    ): AuthorizationResult =
        suspendCancellableCoroutine { continuation ->
            val launcher = resolveAuthorizationLauncher(activity)
            val authorizationClient = Identity.getAuthorizationClient(activity)

            authorizationResultHandler = { result ->
                if (continuation.isActive) {
                    result.fold(
                        onSuccess = { authorizationResult ->
                            continuation.resume(authorizationResult)
                        },
                        onFailure = { exception ->
                            continuation.resumeWithException(exception)
                        },
                    )
                }
            }

            authorizationClient
                .authorize(request)
                .addOnSuccessListener { authorizationResult ->
                    if (!continuation.isActive) {
                        clearAuthorizationResultHandler()
                        return@addOnSuccessListener
                    }

                    if (authorizationResult.hasResolution()) {
                        val pendingIntent = authorizationResult.pendingIntent
                        if (pendingIntent == null) {
                            clearAuthorizationResultHandler()
                            continuation.resumeWithException(
                                IllegalStateException("Google login requires user consent, but no resolution was returned."),
                            )
                        } else {
                            launcher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                            )
                        }
                    } else {
                        clearAuthorizationResultHandler()
                        continuation.resume(authorizationResult)
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        clearAuthorizationResultHandler()
                        continuation.resumeWithException(
                            IllegalStateException("Google login failed.", exception),
                        )
                    }
                }

            continuation.invokeOnCancellation {
                clearAuthorizationResultHandler()
            }
        }

    private fun resolveAuthorizationLauncher(activity: ComponentActivity): ActivityResultLauncher<IntentSenderRequest> {
        val activityIdentity = System.identityHashCode(activity)
        val currentLauncher = authorizationLauncher
        if (currentLauncher != null && launcherOwnerIdentity == activityIdentity) {
            return currentLauncher
        }

        clearAuthorizationLauncher()

        val key = "google_authorization_$activityIdentity"
        launcherOwnerIdentity = activityIdentity
        authorizationLauncher =
            activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { activityResult ->
                val handler = authorizationResultHandler ?: return@register
                try {
                    clearAuthorizationResultHandler()
                    handler(
                        Result.success(
                            Identity.getAuthorizationClient(activity)
                                .getAuthorizationResultFromIntent(activityResult.data),
                        ),
                    )
                } catch (exception: ApiException) {
                    clearAuthorizationResultHandler()
                    handler(Result.failure(IllegalStateException("Google login failed.", exception)))
                }
            }

        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (launcherOwnerIdentity == activityIdentity) {
                        authorizationResultHandler?.invoke(
                            Result.failure(
                                IllegalStateException("Google login was interrupted because the activity was destroyed."),
                            ),
                        )
                        clearAuthorizationLauncher()
                    }
                    owner.lifecycle.removeObserver(this)
                }
            },
        )

        return checkNotNull(authorizationLauncher)
    }

    private fun clearAuthorizationResultHandler() {
        authorizationResultHandler = null
    }

    private fun clearAuthorizationLauncher() {
        authorizationLauncher?.unregister()
        authorizationLauncher = null
        launcherOwnerIdentity = null
        clearAuthorizationResultHandler()
    }

    private companion object {
        val GOOGLE_USERINFO_SCOPES =
            listOf(
                Scope("openid"),
                Scope("profile"),
                Scope("email"),
            )
    }
}

class NaverSocialAccessTokenProvider(
    private val activityProvider: () -> Context?,
) : SocialAccessTokenProvider {
    override suspend fun getAccessToken(provider: AuthSocialProvider): String {
        if (provider != AuthSocialProvider.NAVER) {
            throw IllegalStateException("${provider.displayName} Android login key and SDK connection are required.")
        }
        if (!isNaverLoginConfigured()) {
            throw IllegalStateException("Naver Client ID, Client Secret, and Client Name are required.")
        }
        val activityContext =
            activityProvider()
                ?: throw IllegalStateException("Naver login requires a foreground Activity.")

        return suspendCancellableCoroutine { continuation ->
            val callback =
                object : OAuthLoginCallback {
                    override fun onSuccess() {
                        val accessToken = NaverIdLoginSDK.getAccessToken()
                        when {
                            !continuation.isActive -> Unit
                            accessToken.isNullOrBlank() ->
                                continuation.resumeWithException(
                                    IllegalStateException("Naver login did not return an access token."),
                                )
                            else -> continuation.resume(accessToken)
                        }
                    }

                    override fun onFailure(
                        httpStatus: Int,
                        message: String,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Naver login failed: $httpStatus $message"),
                            )
                        }
                    }

                    override fun onError(
                        errorCode: Int,
                        message: String,
                    ) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Naver login error: $errorCode $message"),
                            )
                        }
                    }
                }

            NaverIdLoginSDK.authenticate(activityContext, callback)
        }
    }

    private fun isNaverLoginConfigured(): Boolean =
        BuildConfig.NAVER_CLIENT_ID.isNotBlank() &&
            BuildConfig.NAVER_CLIENT_SECRET.isNotBlank() &&
            BuildConfig.NAVER_CLIENT_NAME.isNotBlank()
}

enum class AuthSocialProvider(
    val serverValue: String,
    val displayName: String,
) {
    GOOGLE(serverValue = "GOOGLE", displayName = "Google"),
    NAVER(serverValue = "NAVER", displayName = "Naver"),
    KAKAO(serverValue = "KAKAO", displayName = "Kakao"),
    ;

    companion object {
        fun fromProviderKey(providerKey: String): AuthSocialProvider? =
            when (providerKey) {
                "auth-ui-google" -> GOOGLE
                "auth-ui-naver" -> NAVER
                "auth-ui-kakao" -> KAKAO
                else -> null
            }
    }
}
