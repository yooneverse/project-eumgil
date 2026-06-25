package com.ssafy.e102.eumgil.di

import com.ssafy.e102.eumgil.core.config.AppEnvironment
import com.ssafy.e102.eumgil.core.location.AddressSearchResolver
import com.ssafy.e102.eumgil.core.location.NoOpAddressSearchResolver
import com.ssafy.e102.eumgil.data.local.dao.BookmarkDao
import com.ssafy.e102.eumgil.data.local.dao.FavoriteRouteDao
import com.ssafy.e102.eumgil.data.local.dao.AppSettingDao
import com.ssafy.e102.eumgil.data.local.dao.ReportDraftDao
import com.ssafy.e102.eumgil.data.local.dao.ReportOutboxDao
import com.ssafy.e102.eumgil.data.local.datasource.AuthSessionLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.FacilitySeedLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.InitSettingsLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.PlacesLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.RouteLocalDataSource
import com.ssafy.e102.eumgil.data.local.datasource.SearchLocalDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.FacilitySeedMockDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.MockVoiceAnalyzeRemoteDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.PlacesMockDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.SearchMockDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.BookmarksRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.FavoriteRoutesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.HazardReportsRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.PlacesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.RouteRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.SearchRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.UserRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.VoiceAnalyzeRemoteDataSource
import com.ssafy.e102.eumgil.data.repository.AuthLoginRepository
import com.ssafy.e102.eumgil.data.repository.AuthLogoutRepository
import com.ssafy.e102.eumgil.data.repository.AuthSignupRepository
import com.ssafy.e102.eumgil.data.repository.AuthSessionRepository
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapRepository
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.DefaultAuthSessionRepository
import com.ssafy.e102.eumgil.data.repository.DefaultBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.DefaultRouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.DefaultFacilitySeedRepository
import com.ssafy.e102.eumgil.data.repository.DefaultPlacesRepository
import com.ssafy.e102.eumgil.data.repository.DefaultReportRepository
import com.ssafy.e102.eumgil.data.repository.HazardReportImageUploader
import com.ssafy.e102.eumgil.data.repository.NoOpHazardReportImageUploader
import com.ssafy.e102.eumgil.data.repository.DefaultRouteRepository
import com.ssafy.e102.eumgil.data.repository.DefaultSearchRepository
import com.ssafy.e102.eumgil.data.repository.DefaultSettingsRepository
import com.ssafy.e102.eumgil.data.repository.DefaultTextSizePreferenceRepository
import com.ssafy.e102.eumgil.data.repository.DestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.FacilitySeedRepository
import com.ssafy.e102.eumgil.data.repository.DestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.EmptyApprovedReportMapRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.LocalOnlyAuthLoginRepository
import com.ssafy.e102.eumgil.data.repository.LocalOnlyAuthSignupRepository
import com.ssafy.e102.eumgil.data.repository.LocalOnlyUserProfileRepository
import com.ssafy.e102.eumgil.data.repository.PlacesRepository
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.DefaultVoiceAnalyzeRepository
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.repository.VoiceAnalyzeRepository
import com.ssafy.e102.eumgil.data.repository.ServerAuthSignupRepository
import com.ssafy.e102.eumgil.data.repository.ServerAuthLoginRepository
import com.ssafy.e102.eumgil.data.repository.SettingsRepository
import com.ssafy.e102.eumgil.data.repository.SocialAccessTokenProvider
import com.ssafy.e102.eumgil.data.repository.ServerUserProfileRepository
import com.ssafy.e102.eumgil.data.repository.TextSizePreferenceRepository
import com.ssafy.e102.eumgil.data.repository.UserProfileRepository
import com.ssafy.e102.eumgil.data.repository.policy.DefaultRepositorySourcePolicy
import com.ssafy.e102.eumgil.data.repository.policy.RepositorySourcePolicy
import com.ssafy.e102.eumgil.data.repository.provideAuthLogoutRepository as provideAuthLogoutRepositoryImpl

object RepositoryModule {
    fun provideDestinationSelectionRepository(): DestinationSelectionRepository =
        InMemoryDestinationSelectionRepository()

    fun provideDestinationPreviewRepository(): DestinationPreviewRepository =
        InMemoryDestinationPreviewRepository()

    fun provideApprovedReportMapRepository(): ApprovedReportMapRepository =
        EmptyApprovedReportMapRepository

    fun provideAuthSessionRepository(
        authSessionLocalDataSource: AuthSessionLocalDataSource,
    ): AuthSessionRepository =
        DefaultAuthSessionRepository(authSessionLocalDataSource = authSessionLocalDataSource)

    fun provideAuthLoginRepository(
        authRemoteDataSource: AuthRemoteDataSource,
        socialAccessTokenProvider: SocialAccessTokenProvider,
        authSessionRepository: AuthSessionRepository,
        settingsRepository: SettingsRepository,
    ): AuthLoginRepository =
        if (AppEnvironment.isMockMode) {
            LocalOnlyAuthLoginRepository(authSessionRepository = authSessionRepository)
        } else {
            ServerAuthLoginRepository(
                authRemoteDataSource = authRemoteDataSource,
                socialAccessTokenProvider = socialAccessTokenProvider,
                authSessionRepository = authSessionRepository,
                settingsRepository = settingsRepository,
            )
        }

    fun provideAuthSignupRepository(
        authRemoteDataSource: AuthRemoteDataSource,
        authSessionRepository: AuthSessionRepository,
        settingsRepository: SettingsRepository,
    ): AuthSignupRepository =
        if (AppEnvironment.isMockMode) {
            LocalOnlyAuthSignupRepository()
        } else {
            ServerAuthSignupRepository(
                authRemoteDataSource = authRemoteDataSource,
                authSessionRepository = authSessionRepository,
                settingsRepository = settingsRepository,
            )
        }

    fun provideAuthLogoutRepository(
        authRemoteDataSource: AuthRemoteDataSource,
        authSessionRepository: AuthSessionRepository,
        bookmarkDao: BookmarkDao,
        favoriteRouteDao: FavoriteRouteDao,
        reportOutboxDao: ReportOutboxDao,
        placesLocalDataSource: PlacesLocalDataSource,
        destinationSelectionRepository: DestinationSelectionRepository,
        destinationPreviewRepository: DestinationPreviewRepository,
    ): AuthLogoutRepository =
        provideAuthLogoutRepositoryImpl(
            authRemoteDataSource = authRemoteDataSource,
            authSessionRepository = authSessionRepository,
            bookmarkDao = bookmarkDao,
            favoriteRouteDao = favoriteRouteDao,
            reportOutboxDao = reportOutboxDao,
            placesLocalDataSource = placesLocalDataSource,
            destinationSelectionRepository = destinationSelectionRepository,
            destinationPreviewRepository = destinationPreviewRepository,
            isMockMode = AppEnvironment.isMockMode,
        )

    fun provideUserProfileRepository(
        userRemoteDataSource: UserRemoteDataSource,
        authRemoteDataSource: AuthRemoteDataSource,
        authSessionRepository: AuthSessionRepository,
        settingsRepository: SettingsRepository,
    ): UserProfileRepository =
        if (AppEnvironment.isMockMode) {
            LocalOnlyUserProfileRepository()
        } else {
            ServerUserProfileRepository(
                userRemoteDataSource = userRemoteDataSource,
                authRemoteDataSource = authRemoteDataSource,
                authSessionRepository = authSessionRepository,
                settingsRepository = settingsRepository,
            )
        }

    fun provideBookmarkRepository(
        bookmarkDao: BookmarkDao,
        authSessionRepository: AuthSessionRepository? = null,
        bookmarksRemoteDataSource: BookmarksRemoteDataSource? = null,
        accessTokenProvider: suspend () -> String? = { null },
        initialBookmarks: List<BookmarkData> = emptyList(),
    ): BookmarkRepository =
        DefaultBookmarkRepository(
            bookmarkDao = bookmarkDao,
            authSessionRepository = authSessionRepository,
            bookmarksRemoteDataSource = bookmarksRemoteDataSource,
            accessTokenProvider = accessTokenProvider,
            initialBookmarks = initialBookmarks,
        )

    fun provideRouteBookmarkRepository(
        favoriteRouteDao: FavoriteRouteDao,
        authSessionRepository: AuthSessionRepository? = null,
        favoriteRoutesRemoteDataSource: FavoriteRoutesRemoteDataSource? = null,
        accessTokenProvider: suspend () -> String? = { null },
    ): RouteBookmarkRepository =
        DefaultRouteBookmarkRepository(
            favoriteRouteDao = favoriteRouteDao,
            authSessionRepository = authSessionRepository,
            favoriteRoutesRemoteDataSource = favoriteRoutesRemoteDataSource,
            accessTokenProvider = accessTokenProvider,
        )

    fun provideSettingsRepository(
        initSettingsLocalDataSource: InitSettingsLocalDataSource,
        authSessionRepository: AuthSessionRepository,
    ): SettingsRepository =
        DefaultSettingsRepository(
            initSettingsLocalDataSource = initSettingsLocalDataSource,
            authSessionRepository = authSessionRepository,
        )

    fun provideTextSizePreferenceRepository(
        appSettingDao: AppSettingDao,
    ): TextSizePreferenceRepository =
        DefaultTextSizePreferenceRepository(appSettingDao = appSettingDao)

    fun provideRepositorySourcePolicy(): RepositorySourcePolicy = DefaultRepositorySourcePolicy()

    fun providePlacesRepository(
        remoteDataSource: PlacesRemoteDataSource,
        localDataSource: PlacesLocalDataSource,
        mockDataSource: PlacesMockDataSource,
        sourcePolicy: RepositorySourcePolicy,
        authSessionRepository: AuthSessionRepository? = null,
        authRemoteDataSource: AuthRemoteDataSource? = null,
    ): PlacesRepository =
        DefaultPlacesRepository(
            remoteDataSource = remoteDataSource,
            localDataSource = localDataSource,
            mockDataSource = mockDataSource,
            sourcePolicy = sourcePolicy,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )

    fun provideFacilitySeedRepository(
        localDataSource: FacilitySeedLocalDataSource,
        mockDataSource: FacilitySeedMockDataSource,
    ): FacilitySeedRepository =
        DefaultFacilitySeedRepository(
            localDataSource = localDataSource,
            mockDataSource = mockDataSource,
        )

    fun provideRouteRepository(
        localDataSource: RouteLocalDataSource,
        remoteDataSource: RouteRemoteDataSource,
        authSessionRepository: AuthSessionRepository? = null,
        authRemoteDataSource: AuthRemoteDataSource? = null,
    ): RouteRepository =
        DefaultRouteRepository(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )

    fun provideSearchRepository(
        remoteDataSource: SearchRemoteDataSource,
        localDataSource: SearchLocalDataSource,
        mockDataSource: SearchMockDataSource,
        sourcePolicy: RepositorySourcePolicy,
        authSessionRepository: AuthSessionRepository? = null,
        authRemoteDataSource: AuthRemoteDataSource? = null,
        addressSearchResolver: AddressSearchResolver = NoOpAddressSearchResolver,
    ): SearchRepository =
        DefaultSearchRepository(
            remoteDataSource = remoteDataSource,
            localDataSource = localDataSource,
            mockDataSource = mockDataSource,
            sourcePolicy = sourcePolicy,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
            addressSearchResolver = addressSearchResolver,
        )

    fun provideReportRepository(
        reportDraftDao: ReportDraftDao,
        reportOutboxDao: ReportOutboxDao,
        hazardReportsRemoteDataSource: HazardReportsRemoteDataSource? = null,
        accessTokenProvider: suspend () -> String? = { null },
        imageUploader: HazardReportImageUploader = NoOpHazardReportImageUploader,
        // Task 5.9 — 401 + A4010 자동 재발급/재시도용 인프라. 둘 다 주입되면 Repository가 runner를 사용한다.
        authSessionRepository: AuthSessionRepository? = null,
        authRemoteDataSource: AuthRemoteDataSource? = null,
    ): ReportRepository =
        DefaultReportRepository(
            reportDraftDao = reportDraftDao,
            reportOutboxDao = reportOutboxDao,
            hazardReportsRemoteDataSource = hazardReportsRemoteDataSource,
            accessTokenProvider = accessTokenProvider,
            imageUploader = imageUploader,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )

    fun provideVoiceAnalyzeRepository(
        remoteDataSource: VoiceAnalyzeRemoteDataSource,
        mockDataSource: MockVoiceAnalyzeRemoteDataSource,
        sourcePolicy: RepositorySourcePolicy,
        authSessionRepository: AuthSessionRepository? = null,
        authRemoteDataSource: AuthRemoteDataSource? = null,
    ): VoiceAnalyzeRepository =
        DefaultVoiceAnalyzeRepository(
            remoteDataSource = remoteDataSource,
            mockDataSource = mockDataSource,
            sourcePolicy = sourcePolicy,
            authSessionRepository = authSessionRepository,
            authRemoteDataSource = authRemoteDataSource,
        )
}
