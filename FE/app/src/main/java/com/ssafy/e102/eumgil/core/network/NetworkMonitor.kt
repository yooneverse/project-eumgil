package com.ssafy.e102.eumgil.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Task 4.1 — 단말 네트워크 가용성 관찰자.
 *
 * 책임 분리: 이 인터페이스는 단말 차원의 연결성(transport availability)만 다룬다.
 * 서버 200 OK 같은 종단 연결성은 보장하지 않으며, 호출자가 "오프라인일 때는 굳이 서버 호출을 시도하지 않는다"
 * 정도의 단순 가드로 사용한다.
 */
interface NetworkMonitor {
    /**
     * 현재 시점 기준 온라인 여부. UI 첫 paint 직전에 즉시 읽어서 disabled 초기값을 정할 때 사용.
     * Flow 구독을 기다리지 않아도 되어 race condition을 줄인다.
     */
    val isCurrentlyOnline: Boolean

    /**
     * 온라인/오프라인 상태 변화를 emit한다. 동일 값은 [distinctUntilChanged]로 dedup된다.
     */
    fun observeOnlineState(): Flow<Boolean>
}

/**
 * 항상 온라인으로 보고하는 no-op 구현. 단위 테스트의 기본 더블, 또는 mock 모드에서 사용.
 */
object AlwaysOnlineNetworkMonitor : NetworkMonitor {
    override val isCurrentlyOnline: Boolean = true

    override fun observeOnlineState(): Flow<Boolean> = kotlinx.coroutines.flow.flowOf(true)
}

class AndroidNetworkMonitor(
    context: Context,
) : NetworkMonitor {
    private val connectivityManager: ConnectivityManager? = context.getSystemService()

    override val isCurrentlyOnline: Boolean
        get() = connectivityManager?.hasUsableNetwork() ?: false

    override fun observeOnlineState(): Flow<Boolean> =
        callbackFlow {
            val manager = connectivityManager
            if (manager == null) {
                trySend(false)
                close()
                return@callbackFlow
            }

            // 첫 emit으로 현재 상태를 즉시 흘려서 UI가 늦게 구독해도 정확한 초기값을 받게 한다.
            trySend(manager.hasUsableNetwork())

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        trySend(manager.hasUsableNetwork())
                    }

                    override fun onLost(network: Network) {
                        trySend(manager.hasUsableNetwork())
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        trySend(manager.hasUsableNetwork())
                    }
                }

            val request =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            manager.registerNetworkCallback(request, callback)

            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()

    /**
     * INTERNET capability를 가진 active network가 있는지 확인.
     * `NET_CAPABILITY_VALIDATED`는 추가로 점검하지 않는다 — 캡티브 포털/공유기 미인증 상황에서도
     * 사용자가 시도 자체를 막히지 않는 게 더 자연스럽기 때문.
     */
    private fun ConnectivityManager.hasUsableNetwork(): Boolean {
        val network = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
