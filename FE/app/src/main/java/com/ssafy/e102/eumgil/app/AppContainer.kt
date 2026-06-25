package com.ssafy.e102.eumgil.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ssafy.e102.eumgil.core.config.AppEnvironment
import com.ssafy.e102.eumgil.core.location.AndroidAddressSearchResolver
import com.ssafy.e102.eumgil.core.location.AndroidCurrentHeadingManager
import com.ssafy.e102.eumgil.core.location.AndroidCurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.AndroidCurrentLocationManager
import com.ssafy.e102.eumgil.core.location.AndroidLocationPermissionManager
import com.ssafy.e102.eumgil.core.location.CurrentHeadingManager
import com.ssafy.e102.eumgil.core.location.CurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.model.resolveAccountScopeKey
import com.ssafy.e102.eumgil.core.network.AndroidNetworkMonitor
import com.ssafy.e102.eumgil.core.network.NetworkMonitor
import com.ssafy.e102.eumgil.data.local.datasource.AuthSessionLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.FacilitySeedLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.InitSettingsLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.PlacesLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.RouteLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.SearchLocalDataSource
import com.ssafy.e102.eumgil.data.local.datastore.initSettingsDataStore
import com.ssafy.e102.eumgil.data.local.db.EumgilDatabase
import com.ssafy.e102.eumgil.data.mock.datasource.FacilitySeedMockDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.MockVoiceAnalyzeRemoteDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.PlacesMockDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.SearchMockDataSource
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.HttpJsonTimeoutConfig
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.BookmarksRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.FavoriteRoutesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.HazardReportImagesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.HazardReportsRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.KtorVoiceAnalyzeRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.PlacesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.RouteRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.SearchRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.UserRemoteDataSource
import com.ssafy.e102.eumgil.data.repository.AuthLoginRepository
import com.ssafy.e102.eumgil.data.repository.AuthLogoutRepository
import com.ssafy.e102.eumgil.data.repository.AuthSessionRepository
import com.ssafy.e102.eumgil.data.repository.AuthSignupRepository
import com.ssafy.e102.eumgil.data.repository.AuthSocialProvider
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapRepository
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.CompositeSocialAccessTokenProvider
import com.ssafy.e102.eumgil.data.repository.DefaultHazardReportImageUploader
import com.ssafy.e102.eumgil.data.repository.DestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.DestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.FacilitySeedRepository
import com.ssafy.e102.eumgil.data.repository.GoogleSocialAccessTokenProvider
import com.ssafy.e102.eumgil.data.repository.KakaoSocialAccessTokenProvider
import com.ssafy.e102.eumgil.data.repository.NaverSocialAccessTokenProvider
import com.ssafy.e102.eumgil.data.repository.PlacesRepository
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.repository.SettingsRepository
import com.ssafy.e102.eumgil.data.repository.TextSizePreferenceRepository
import com.ssafy.e102.eumgil.data.repository.UserProfileRepository
import com.ssafy.e102.eumgil.data.repository.VoiceAnalyzeRepository
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySourcePolicy
import com.ssafy.e102.eumgil.di.RepositoryModule

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val localDatabase: EumgilDatabase by lazy(LazyThreadSafetyMode.NONE) {
        EumgilDatabase.getInstance(appContext)
    }

    private val initSettingsLocalDataSource by lazy(LazyThreadSafetyMode.NONE) {
        InitSettingsLocalDataSource(dataStore = appContext.initSettingsDataStore)
    }

    private val authSessionDataStore by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("auth_session") },
        )
    }

    private val searchDataStore by lazy(LazyThreadSafetyMode.NONE) {
        PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile("search_local") },
        )
    }

    private val authSessionLocalDataSource by lazy(LazyThreadSafetyMode.NONE) {
        AuthSessionLocalDataSource(
            dataStore = authSessionDataStore,
            allowLocalOnlySession = AppEnvironment.isMockMode,
        )
    }

    private val placesLocalDataSource by lazy(LazyThreadSafetyMode.NONE) {
        PlacesLocalDataSource(
            currentAccountScopeProvider = {
                authSessionRepository.getAuthGateState().authSession?.resolveAccountScopeKey()
            },
        )
    }
    private val facilitySeedLocalDataSource by lazy(LazyThreadSafetyMode.NONE) { FacilitySeedLocalDataSource() }
    private val routeLocalDataSource by lazy(LazyThreadSafetyMode.NONE) { RouteLocalDataSource() }
    private val searchLocalDataSource by lazy(LazyThreadSafetyMode.NONE) {
        SearchLocalDataSource(
            dataStore = searchDataStore,
            currentUserScopeProvider = {
                authSessionRepository.getAuthGateState().authSession?.resolveAccountScopeKey()
            },
        )
    }

    private val httpJsonClient by lazy(LazyThreadSafetyMode.NONE) {
        HttpJsonClient(baseUrl = AppEnvironment.baseUrl)
    }
    private val authRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        AuthRemoteDataSource(httpJsonClient = httpJsonClient)
    }
    private val bookmarksRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        BookmarksRemoteDataSource(httpJsonClient = httpJsonClient)
    }
    private val favoriteRoutesRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        FavoriteRoutesRemoteDataSource(httpJsonClient = httpJsonClient)
    }
    private val hazardReportsRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        HazardReportsRemoteDataSource(httpJsonClient = httpJsonClient)
    }
    private val hazardReportImagesRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        HazardReportImagesRemoteDataSource(httpJsonClient = httpJsonClient)
    }
    private val placesRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        PlacesRemoteDataSource(
            baseUrl = AppEnvironment.baseUrl,
            accessTokenProvider = {
                authSessionRepository.getAuthGateState().authSession?.accessToken
            },
        )
    }
    private val searchRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        SearchRemoteDataSource(
            baseUrl = AppEnvironment.baseUrl,
            accessTokenProvider = {
                authSessionRepository.getAuthGateState().authSession?.accessToken
            },
        )
    }
    private val routeRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        RouteRemoteDataSource(
            baseUrl = AppEnvironment.baseUrl,
            accessTokenProvider = {
                authSessionRepository.getAuthGateState().authSession?.accessToken
            },
            timeoutConfig =
                HttpJsonTimeoutConfig(
                    connectTimeoutMillis = ROUTE_CONNECT_TIMEOUT_MILLIS,
                    readTimeoutMillis = ROUTE_READ_TIMEOUT_MILLIS,
                ),
        )
    }
    private val userRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        UserRemoteDataSource(httpJsonClient = httpJsonClient)
    }
    private val voiceAnalyzeRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        KtorVoiceAnalyzeRemoteDataSource(
            httpJsonClient = httpJsonClient,
            accessTokenProvider = {
                authSessionRepository.getAuthGateState().authSession?.accessToken
            },
        )
    }

    private val placesMockDataSource by lazy(LazyThreadSafetyMode.NONE) { PlacesMockDataSource() }
    private val facilitySeedMockDataSource by lazy(LazyThreadSafetyMode.NONE) { FacilitySeedMockDataSource() }
    private val searchMockDataSource by lazy(LazyThreadSafetyMode.NONE) { SearchMockDataSource() }
    private val mockVoiceAnalyzeRemoteDataSource by lazy(LazyThreadSafetyMode.NONE) {
        MockVoiceAnalyzeRemoteDataSource()
    }

    private val repositorySourcePolicy: RepositorySourcePolicy by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideRepositorySourcePolicy()
    }

    val destinationSelectionRepository: DestinationSelectionRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideDestinationSelectionRepository()
    }

    val destinationPreviewRepository: DestinationPreviewRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideDestinationPreviewRepository()
    }

    val approvedReportMapRepository: ApprovedReportMapRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideApprovedReportMapRepository()
    }

    val authSessionRepository: AuthSessionRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideAuthSessionRepository(
            authSessionLocalDataSource = authSessionLocalDataSource,
        )
    }

    val authLoginRepository: AuthLoginRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideAuthLoginRepository(
            authRemoteDataSource = authRemoteDataSource,
            socialAccessTokenProvider =
                CompositeSocialAccessTokenProvider(
                    providersBySocialProvider =
                        mapOf(
                            AuthSocialProvider.KAKAO to
                                KakaoSocialAccessTokenProvider(
                                    context = appContext,
                                    activityContextProvider = { ForegroundActivityProvider.currentActivity },
                                ),
                            AuthSocialProvider.GOOGLE to
                                GoogleSocialAccessTokenProvider(
                                    activityProvider = { ForegroundActivityProvider.currentActivity },
                                ),
                            AuthSocialProvider.NAVER to
                                NaverSocialAccessTokenProvider(
                                    activityProvider = { ForegroundActivityProvider.currentActivity },
                                ),
                        ),
                ),
            authSessionRepository = authSessionRepository,
            settingsRepository = settingsRepository,
        )
    }

    val authSignupRepository: AuthSignupRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideAuthSignupRepository(
            authRemoteDataSource = authRemoteDataSource,
            authSessionRepository = authSessionRepository,
            settingsRepository = settingsRepository,
        )
    }

    val authLogoutRepository: AuthLogoutRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideAuthLogoutRepository(
            authRemoteDataSource = authRemoteDataSource,
            authSessionRepository = authSessionRepository,
            bookmarkDao = localDatabase.bookmarkDao(),
            favoriteRouteDao = localDatabase.favoriteRouteDao(),
            reportOutboxDao = localDatabase.reportOutboxDao(),
            placesLocalDataSource = placesLocalDataSource,
            destinationSelectionRepository = destinationSelectionRepository,
            destinationPreviewRepository = destinationPreviewRepository,
        )
    }

    val userProfileRepository: UserProfileRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideUserProfileRepository(
            userRemoteDataSource = userRemoteDataSource,
            authRemoteDataSource = authRemoteDataSource,
            authSessionRepository = authSessionRepository,
            settingsRepository = settingsRepository,
        )
    }

    val bookmarkRepository: BookmarkRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideBookmarkRepository(
            bookmarkDao = localDatabase.bookmarkDao(),
            authSessionRepository = authSessionRepository,
            bookmarksRemoteDataSource =
                if (AppEnvironment.isMockMode) null else bookmarksRemoteDataSource,
            accessTokenProvider = {
                authSessionRepository.getAuthGateState().authSession?.accessToken
            },
        )
    }

    val routeBookmarkRepository: RouteBookmarkRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideRouteBookmarkRepository(
            favoriteRouteDao = localDatabase.favoriteRouteDao(),
            authSessionRepository = authSessionRepository,
            favoriteRoutesRemoteDataSource =
                if (AppEnvironment.isMockMode) null else favoriteRoutesRemoteDataSource,
            accessTokenProvider = {
                authSessionRepository.getAuthGateState().authSession?.accessToken
            },
        )
    }

    val settingsRepository: SettingsRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideSettingsRepository(
            initSettingsLocalDataSource = initSettingsLocalDataSource,
            authSessionRepository = authSessionRepository,
        )
    }

    val textSizePreferenceRepository: TextSizePreferenceRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideTextSizePreferenceRepository(
            appSettingDao = localDatabase.appSettingDao(),
        )
    }

    val placesRepository: PlacesRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.providePlacesRepository(
            remoteDataSource = placesRemoteDataSource,
            localDataSource = placesLocalDataSource,
            mockDataSource = placesMockDataSource,
            sourcePolicy = repositorySourcePolicy,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )
    }

    val facilitySeedRepository: FacilitySeedRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideFacilitySeedRepository(
            localDataSource = facilitySeedLocalDataSource,
            mockDataSource = facilitySeedMockDataSource,
        )
    }

    val routeRepository: RouteRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideRouteRepository(
            localDataSource = routeLocalDataSource,
            remoteDataSource = routeRemoteDataSource,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )
    }

    val searchRepository: SearchRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideSearchRepository(
            remoteDataSource = searchRemoteDataSource,
            localDataSource = searchLocalDataSource,
            mockDataSource = searchMockDataSource,
            sourcePolicy = repositorySourcePolicy,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
            addressSearchResolver = AndroidAddressSearchResolver(context = appContext),
        )
    }

    val reportRepository: ReportRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideReportRepository(
            reportDraftDao = localDatabase.reportDraftDao(),
            reportOutboxDao = localDatabase.reportOutboxDao(),
            hazardReportsRemoteDataSource =
                if (AppEnvironment.isMockMode) null else hazardReportsRemoteDataSource,
            accessTokenProvider = {
                authSessionRepository.getAuthGateState().authSession?.accessToken
            },
            // Task 5.5 — 제보 제출 직전 사진 presigned 업로드 흐름.
            imageUploader =
                DefaultHazardReportImageUploader(
                    contentResolver = appContext.contentResolver,
                    remoteDataSource = hazardReportImagesRemoteDataSource,
                ),
            // Task 5.9 — 401(A4010) 발생 시 /auth/reissue 후 동일 요청을 1회 재시도하기 위해 인증 인프라 주입.
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )
    }

    val voiceAnalyzeRepository: VoiceAnalyzeRepository by lazy(LazyThreadSafetyMode.NONE) {
        RepositoryModule.provideVoiceAnalyzeRepository(
            remoteDataSource = voiceAnalyzeRemoteDataSource,
            mockDataSource = mockVoiceAnalyzeRemoteDataSource,
            sourcePolicy = repositorySourcePolicy,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )
    }

    val locationPermissionManager: LocationPermissionManager by lazy(LazyThreadSafetyMode.NONE) {
        AndroidLocationPermissionManager(context = appContext)
    }

    val currentLocationManager: CurrentLocationManager by lazy(LazyThreadSafetyMode.NONE) {
        AndroidCurrentLocationManager(context = appContext)
    }

    val currentLocationAddressResolver: CurrentLocationAddressResolver by lazy(LazyThreadSafetyMode.NONE) {
        AndroidCurrentLocationAddressResolver(context = appContext)
    }

    val currentHeadingManager: CurrentHeadingManager by lazy(LazyThreadSafetyMode.NONE) {
        AndroidCurrentHeadingManager(context = appContext)
    }

    // Task 4.1 — 단말 네트워크 가용성을 관찰해 ReportViewModel이 오프라인 시 제출 버튼을 자동 비활성화하도록 한다.
    val networkMonitor: NetworkMonitor by lazy(LazyThreadSafetyMode.NONE) {
        AndroidNetworkMonitor(context = appContext)
    }

    private companion object {
        private const val ROUTE_CONNECT_TIMEOUT_MILLIS = 5_000
        private const val ROUTE_READ_TIMEOUT_MILLIS = 7_000
    }
}
