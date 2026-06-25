package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.data.local.dao.ReportDraftDao
import com.ssafy.e102.eumgil.data.local.dao.ReportOutboxDao
import com.ssafy.e102.eumgil.data.local.entity.ReportDraftEntity
import com.ssafy.e102.eumgil.data.local.entity.ReportOutboxEntity
import com.ssafy.e102.eumgil.data.remote.datasource.AuthRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.datasource.HazardReportsApiException
import com.ssafy.e102.eumgil.data.remote.datasource.HazardReportsRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.CreateHazardReportRequestDto
import com.ssafy.e102.eumgil.data.remote.dto.CreateHazardReportResponseDto
import com.ssafy.e102.eumgil.data.remote.dto.HazardReportDetailDto
import com.ssafy.e102.eumgil.data.remote.dto.HazardReportListItemDto
import com.ssafy.e102.eumgil.data.remote.dto.HazardReportPointDto
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.route.DefaultRouteGeometryParser
import com.ssafy.e102.eumgil.data.route.toRouteCandidate
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

interface ReportRepository {
    fun observeReportHistory(): Flow<List<ReportOutboxData>>

    suspend fun getApprovedHazardMarkers(bounds: ApprovedHazardMarkerBounds): List<ApprovedHazardMarker> = emptyList()

    suspend fun rerouteAfterHazardReport(
        reportId: Long,
        routeId: String,
        currentPoint: GeoCoordinate,
        activeLegSequence: Int? = null,
    ): HazardReportRerouteResult = HazardReportRerouteResult(rerouted = false, route = null)

    fun observeReportHistoryEntries(): Flow<List<ReportHistoryData>> =
        observeReportHistory().map { outboxItems ->
            outboxItems.map(ReportOutboxData::toLocalHistoryData)
        }

    fun observeReportProcessingCounts(): Flow<ReportProcessingCounts> =
        observeReportHistoryEntries().map(::countReportProcessingStatus)

    suspend fun getReportHistoryDetail(historyId: String): ReportHistoryDetailData? = null

    suspend fun getLatestDraft(): ReportDraftData?

    suspend fun saveDraft(draft: ReportDraftData): ReportDraftData

    suspend fun deleteDraft(draftId: String)

    suspend fun saveOutbox(outbox: ReportOutboxData): ReportOutboxData

    suspend fun submitOutboxToServer(outboxId: String): ReportSubmitResult

    /**
     * Task 4.2 — 앱이 비정상 종료되어 `Submitting` 상태로 멈춰있던 outbox row를 다시 `Pending`으로 되돌린다.
     * 앱 시작 시 1회 호출. 영향 row 수를 반환한다. 기본 구현은 no-op (테스트 더블 호환).
     */
    suspend fun resetStaleSubmittingOutboxes(): Int = 0
}

data class ReportDraftData(
    val draftId: String,
    val reportCategory: String?,
    val description: String,
    // 좌표 → RGC 자동 변환 결과(도로명/지번). 사용자가 손대지 않는 객관 정보.
    val address: String?,
    // 사용자가 "건물명·주변 장소" 입력란에 직접 적은 현장 맥락 보충 메모 (v8 신설).
    // 기존 단일 address 필드만 사용하던 코드/테스트 호환을 위해 default null.
    val addressDetail: String? = null,
    val latitude: Double?,
    val longitude: Double?,
    val locationSource: String?,
    // v7부터 사진 다장(최대 5장) 보존. 단일 사진 시절(v6 이전)에 저장된 draft를 read 시점에 자동
    // 1-item list로 변환하여 자연스럽게 호환된다.
    val photos: List<ReportDraftPhotoData>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/**
 * 제보 임시저장에 포함된 단일 사진 메타데이터.
 * `ReportPhoto` (UI 모델)와 동일한 shape이지만 도메인 분리를 위해 별도 데이터 클래스로 둔다.
 */
data class ReportDraftPhotoData(
    val localUri: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)

data class ReportOutboxData(
    val outboxId: String,
    val reportCategory: String,
    val description: String,
    // 자동 RGC 결과. 서버 submit DTO에는 address 필드 자체가 없어 로컬에서만 사용된다.
    val address: String?,
    // 사용자 직접 보충 메모. 마찬가지로 로컬 전용 (v8 신설).
    val addressDetail: String? = null,
    val latitude: Double,
    val longitude: Double,
    // legacy v9 이전 단일 사진 필드. 새 코드는 photos 리스트를 source of truth로 사용한다.
    val photoUri: String?,
    val photoMimeType: String?,
    val photoSizeBytes: Long?,
    // v10 (Task 5.5) — 업로드 대상 사진 메타데이터 다장 보존.
    val photos: List<ReportOutboxPhotoData> = emptyList(),
    // v10 (Task 5.5) — presigned 업로드 성공한 S3 object key 목록. 제출 시 BE에 전달.
    val imageObjectKeys: List<String> = emptyList(),
    val thumbnailObjectKeys: List<String> = emptyList(),
    val status: ReportOutboxStatus = ReportOutboxStatus.Pending,
    val serverReportId: Long? = null,
    val lastFailureReason: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/**
 * Outbox에 보존되는 사진 메타데이터 (Task 5.5).
 *
 * `ReportDraftPhotoData`와 동일 shape이지만 단계(draft vs outbox 제출 직전)가 달라 별도 데이터 클래스로 둔다.
 */
data class ReportOutboxPhotoData(
    val localUri: String,
    val mimeType: String?,
    val sizeBytes: Long?,
)

enum class ReportOutboxStatus {
    Pending,
    Submitting,
    Submitted,
    Failed,
}

enum class ReportHistorySource {
    Server,
    LocalOutbox,
}

enum class ReportProcessingStatus {
    PENDING,
    APPROVED,
    REJECTED,
}

data class ReportProcessingCounts(
    val pending: Int = 0,
    val approved: Int = 0,
)

data class ReportHistoryData(
    val historyId: String,
    val reportCategory: String,
    val processingStatus: ReportProcessingStatus?,
    val description: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val photoUri: String?,
    val imageUrl: String?,
    val source: ReportHistorySource,
    val serverReportId: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class ReportHistoryDetailData(
    val historyId: String,
    val reportCategory: String,
    val processingStatus: ReportProcessingStatus?,
    val description: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val imageRefs: List<String>,
    val source: ReportHistorySource,
    val serverReportId: Long?,
    val createdAtMillis: Long,
)

sealed interface ReportSubmitResult {
    data class Success(
        val outboxId: String,
        val serverReportId: Long,
    ) : ReportSubmitResult

    data class Failure(
        val outboxId: String,
        val reason: ReportSubmitFailureReason,
    ) : ReportSubmitResult

    data object Skipped : ReportSubmitResult
}

data class HazardReportRerouteResult(
    val rerouted: Boolean,
    val route: com.ssafy.e102.eumgil.core.model.RouteCandidate?,
)

enum class ReportSubmitFailureReason {
    Unauthorized,
    InvalidInput,
    Network,
    Unknown,
}

class DefaultReportRepository(
    private val reportDraftDao: ReportDraftDao,
    private val reportOutboxDao: ReportOutboxDao,
    private val hazardReportsRemoteDataSource: HazardReportsRemoteDataSource? = null,
    private val accessTokenProvider: suspend () -> String? = { null },
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = { System.currentTimeMillis() },
    // Task 5.5 — outbox 사진들을 BE submit 직전에 presigned URL로 업로드. null/NoOp이면 업로드 skip.
    private val imageUploader: HazardReportImageUploader = NoOpHazardReportImageUploader,
    // Task 5.9 — 401(A4010) 발생 시 /auth/reissue로 토큰 갱신 후 동일 요청을 1회 재시도하기 위한 인프라.
    // 둘 다 주입되면 AuthenticatedRequestRunner를 사용하고, 아니면 기존 accessTokenProvider fallback.
    authSessionRepository: AuthSessionRepository? = null,
    authRemoteDataSource: AuthRemoteDataSource? = null,
) : ReportRepository {
    private val serverReportHistory = MutableStateFlow(emptyList<ReportHistoryData>())
    private val routeGeometryParser = DefaultRouteGeometryParser()

    private val authenticatedRequestRunner =
        if (authSessionRepository != null && authRemoteDataSource != null) {
            AuthenticatedRequestRunner(
                authSessionRepository = authSessionRepository,
                authRemoteDataSource = authRemoteDataSource,
            )
        } else {
            null
        }

    override fun observeReportHistory(): Flow<List<ReportOutboxData>> =
        reportOutboxDao.observeReportOutboxItems().map { outboxItems ->
            outboxItems.map(ReportOutboxEntity::toData)
        }

    override fun observeReportHistoryEntries(): Flow<List<ReportHistoryData>> =
        combine(
            reportOutboxDao.observeReportOutboxItems(),
            serverReportHistory,
        ) { localOutboxItems, serverItems ->
            mergeServerAndLocalReportHistory(
                serverItems = serverItems,
                localOutboxItems = localOutboxItems.map(ReportOutboxEntity::toData),
            )
        }.onStart {
            refreshReportHistoryFromServerIfPossible()
        }

    override suspend fun getReportHistoryDetail(historyId: String): ReportHistoryDetailData? {
        val localOutboxId = historyId.removePrefixOrNull(LOCAL_HISTORY_PREFIX)
        val localOutbox = localOutboxId?.let { reportOutboxDao.getReportOutbox(it) }?.toData()
        val serverReportId = historyId.removePrefixOrNull(SERVER_HISTORY_PREFIX)?.toLongOrNull() ?: localOutbox?.serverReportId

        if (serverReportId != null) {
            val serverDetail = fetchServerReportDetail(serverReportId)
            if (serverDetail != null) return serverDetail
        }

        return localOutbox?.toDetailData()
    }

    override suspend fun getLatestDraft(): ReportDraftData? =
        reportDraftDao.getLatestReportDraft()?.toData()

    override suspend fun getApprovedHazardMarkers(bounds: ApprovedHazardMarkerBounds): List<ApprovedHazardMarker> {
        val datasource = hazardReportsRemoteDataSource ?: return emptyList()
        val response =
            runAuthenticated { token ->
                datasource.getApprovedHazardMarkers(
                    swLat = bounds.swLat,
                    swLng = bounds.swLng,
                    neLat = bounds.neLat,
                    neLng = bounds.neLng,
                    accessToken = token,
                )
            } ?: return emptyList()
        return response.markers.map { marker ->
            ApprovedHazardMarker(
                reportId = marker.reportId,
                reportType = marker.reportType,
                coordinate =
                    GeoCoordinate(
                        latitude = marker.lat,
                        longitude = marker.lng,
                    ),
                description = marker.description,
                thumbnailUrls = marker.thumbnailUrls,
                imageUrls = marker.imageUrls,
            )
        }
    }

    override suspend fun rerouteAfterHazardReport(
        reportId: Long,
        routeId: String,
        currentPoint: GeoCoordinate,
        activeLegSequence: Int?,
    ): HazardReportRerouteResult {
        val datasource = hazardReportsRemoteDataSource ?: return HazardReportRerouteResult(rerouted = false, route = null)
        val response =
            runCatching {
                runAuthenticated { token ->
                    datasource.rerouteAfterHazardReport(
                        reportId = reportId,
                        accessToken = token,
                        routeId = routeId,
                        currentPoint =
                            HazardReportPointDto(
                                lat = currentPoint.latitude,
                                lng = currentPoint.longitude,
                            ),
                        activeLegSequence = activeLegSequence,
                    )
                }
            }.getOrNull() ?: return HazardReportRerouteResult(rerouted = false, route = null)
        if (response == null) {
            return HazardReportRerouteResult(rerouted = false, route = null)
        }
        return HazardReportRerouteResult(
            rerouted = response.rerouted && response.route != null,
            route = response.route?.toRouteCandidate(geometryParser = routeGeometryParser),
        )
    }

    override suspend fun saveDraft(draft: ReportDraftData): ReportDraftData {
        val now = clock()
        val draftId = draft.draftId.ifBlank(idFactory)
        val createdAtMillis = draft.createdAtMillis.takeIf { it > 0L } ?: now
        val savedDraft =
            draft.copy(
                draftId = draftId,
                createdAtMillis = createdAtMillis,
                updatedAtMillis = now,
            )

        reportDraftDao.upsertReportDraft(savedDraft.toEntity())
        return savedDraft
    }

    override suspend fun deleteDraft(draftId: String) {
        reportDraftDao.deleteReportDraft(draftId)
    }

    override suspend fun resetStaleSubmittingOutboxes(): Int =
        reportOutboxDao.resetSubmittingOutboxesToPending(now = clock())

    override suspend fun saveOutbox(outbox: ReportOutboxData): ReportOutboxData {
        val now = clock()
        val outboxId = outbox.outboxId.ifBlank(idFactory)
        val createdAtMillis = outbox.createdAtMillis.takeIf { it > 0L } ?: now
        val savedOutbox =
            outbox.copy(
                outboxId = outboxId,
                createdAtMillis = createdAtMillis,
                updatedAtMillis = now,
            )

        reportOutboxDao.upsertReportOutbox(savedOutbox.toEntity())
        return savedOutbox
    }

    override suspend fun submitOutboxToServer(outboxId: String): ReportSubmitResult {
        val datasource = hazardReportsRemoteDataSource ?: return ReportSubmitResult.Skipped

        val outboxEntity =
            reportOutboxDao.getReportOutbox(outboxId) ?: return ReportSubmitResult.Skipped

        if (outboxEntity.status == ReportOutboxStatus.Submitted.name && outboxEntity.serverReportId != null) {
            return ReportSubmitResult.Success(
                outboxId = outboxEntity.outboxId,
                serverReportId = outboxEntity.serverReportId,
            )
        }

        markOutboxStatus(outboxEntity, ReportOutboxStatus.Submitting, lastFailureReason = null)

        // Task 5.9 — 업로드+생성을 한 단위로 runner.run에 감싸서 401(A4010) 발생 시
        // /auth/reissue 후 같은 흐름을 1회 재시도한다. 이미 업로드된 사진은 outbox에 보존된 objectKey 덕분에
        // 재시도 시 alreadyUploadedCount로 skip되므로 재시도 비용도 작다.
        val runResult =
            runCatching {
                runAuthenticated { token ->
                    performSubmit(
                        datasource = datasource,
                        outboxEntity = outboxEntity,
                        token = token,
                    )
                }
            }

        return runResult.fold(
            onSuccess = { outcome ->
                if (outcome == null) {
                    // 인증 세션 없음/재발급 실패 — UnAuthorized로 분류.
                    failOutbox(outboxId, ReportSubmitFailureReason.Unauthorized)
                } else if (outcome.partialUploadFailure) {
                    // 사진 업로드 부분 실패(인증과 무관한 IO/네트워크 오류).
                    failOutbox(outboxId, ReportSubmitFailureReason.Network)
                } else {
                    val now = clock()
                    reportOutboxDao.upsertReportOutbox(
                        outcome.outboxAfterUpload.copy(
                            status = ReportOutboxStatus.Submitted.name,
                            serverReportId = outcome.response!!.reportId,
                            lastFailureReason = null,
                            updatedAt = now,
                        ),
                    )
                    ReportSubmitResult.Success(
                        outboxId = outcome.outboxAfterUpload.outboxId,
                        serverReportId = outcome.response.reportId,
                    )
                }
            },
            onFailure = { throwable ->
                failOutbox(outboxId, throwable.toSubmitFailureReason())
            },
        )
    }

    /**
     * 한 번의 submit 시도(이미지 업로드 + 제보 생성)를 토큰을 받아 수행한다.
     *
     * 부분 업로드 실패는 throw하지 않고 `SubmitOutcome.partialUploadFailure = true`로 표현해
     * runner의 재시도 대상에서 제외한다(인증 문제가 아니라 재시도해도 무의미하므로).
     * 인증 실패(401)는 throw 그대로 두어 runner가 reissue + 재시도하도록 한다.
     */
    private suspend fun performSubmit(
        datasource: HazardReportsRemoteDataSource,
        outboxEntity: ReportOutboxEntity,
        token: String,
    ): SubmitOutcome {
        val outboxData = outboxEntity.toData()
        val uploadResult =
            imageUploader.uploadAll(
                accessToken = token,
                photos = outboxData.photos,
                alreadyUploadedCount = outboxData.imageObjectKeys.size,
            )
        val mergedObjectKeys = outboxData.imageObjectKeys + uploadResult.newlyUploadedObjectKeys
        val mergedThumbnailObjectKeys = outboxData.thumbnailObjectKeys + uploadResult.newlyUploadedThumbnailObjectKeys
        val outboxAfterUpload =
            persistObjectKeys(
                outboxEntity = outboxEntity,
                objectKeys = mergedObjectKeys,
                thumbnailObjectKeys = mergedThumbnailObjectKeys,
            )

        if (!uploadResult.allSucceeded) {
            return SubmitOutcome(
                outboxAfterUpload = outboxAfterUpload,
                response = null,
                partialUploadFailure = true,
            )
        }

        val response =
            datasource.createHazardReport(
                accessToken = token,
                request =
                    CreateHazardReportRequestDto(
                        reportType = outboxAfterUpload.reportCategory,
                        description = outboxAfterUpload.description.takeIf(String::isNotBlank),
                        reportPoint =
                            HazardReportPointDto(
                                lat = outboxAfterUpload.latitude,
                                lng = outboxAfterUpload.longitude,
                            ),
                        // Task 5.6 — Task 5.5에서 outbox에 보존된 presigned 업로드 objectKey를 BE에 전달.
                        // 업로드가 모두 성공한 시점에만 이 코드 경로에 도달하므로 mergedObjectKeys는
                        // 사용자가 첨부한 사진 전체에 대응한다.
                        imageObjectKeys = mergedObjectKeys,
                        thumbnailObjectKeys = mergedThumbnailObjectKeys,
                    ),
                // Task 5.8 — outboxId(UUID, 36자)를 Idempotency-Key로 재사용.
                // 같은 outbox가 네트워크 끊김 등으로 재시도되어도 BE는 신규 row를 만들지 않고 기존 reportId를 반환한다.
                idempotencyKey = outboxAfterUpload.outboxId,
            )

        return SubmitOutcome(
            outboxAfterUpload = outboxAfterUpload,
            response = response,
            partialUploadFailure = false,
        )
    }

    /**
     * `runAuthenticated`를 통해 token이 필요한 작업을 실행한다.
     *
     * Runner가 주입되어 있으면 401 자동 재발급/재시도를 처리하고, 그렇지 않으면 legacy `accessTokenProvider`로 fallback.
     * 인증 세션이 아예 없거나 reissue마저 실패하면 `null`을 반환해 호출자가 Unauthorized로 분류한다.
     */
    private suspend fun <T : Any> runAuthenticated(execute: suspend (token: String) -> T): T? {
        val runner = authenticatedRequestRunner
        if (runner != null) {
            return when (
                val result =
                    runner.run(
                        execute = { session -> execute(session.accessToken) },
                        isAuthenticationFailure = ::isHazardReportsAuthenticationFailure,
                    )
            ) {
                AuthenticatedRequestResult.MissingSession,
                AuthenticatedRequestResult.AuthenticationFailed,
                -> null
                is AuthenticatedRequestResult.Success -> result.value
            }
        }
        val token = accessTokenProvider() ?: return null
        return execute(token)
    }

    private fun isHazardReportsAuthenticationFailure(throwable: Throwable): Boolean =
        throwable is HazardReportsApiException && throwable.httpStatusCode == HTTP_UNAUTHORIZED

    private data class SubmitOutcome(
        val outboxAfterUpload: ReportOutboxEntity,
        val response: CreateHazardReportResponseDto?,
        val partialUploadFailure: Boolean,
    )

    /**
     * 업로드 단계의 결과(목록의 일부 또는 전부 성공한 objectKey)를 outbox에 미리 보존한다.
     * 이후 단계가 실패하더라도 다음 재시도에서 이미 업로드된 사진은 다시 올리지 않는다.
     */
    private suspend fun persistObjectKeys(
        outboxEntity: ReportOutboxEntity,
        objectKeys: List<String>,
        thumbnailObjectKeys: List<String>,
    ): ReportOutboxEntity {
        if (
            objectKeys == deserializeStringList(outboxEntity.imageObjectKeysJson) &&
            thumbnailObjectKeys == deserializeStringList(outboxEntity.thumbnailObjectKeysJson)
        ) {
            return outboxEntity
        }
        val updated =
            outboxEntity.copy(
                imageObjectKeysJson = serializeStringList(objectKeys),
                thumbnailObjectKeysJson = serializeStringList(thumbnailObjectKeys),
                updatedAt = clock(),
            )
        reportOutboxDao.upsertReportOutbox(updated)
        return updated
    }

    private suspend fun failOutbox(
        outboxId: String,
        reason: ReportSubmitFailureReason,
    ): ReportSubmitResult.Failure {
        val outboxEntity = reportOutboxDao.getReportOutbox(outboxId)
        if (outboxEntity != null) {
            markOutboxStatus(outboxEntity, ReportOutboxStatus.Failed, lastFailureReason = reason.name)
        }
        return ReportSubmitResult.Failure(outboxId = outboxId, reason = reason)
    }

    private suspend fun markOutboxStatus(
        outboxEntity: ReportOutboxEntity,
        status: ReportOutboxStatus,
        lastFailureReason: String?,
    ) {
        val now = clock()
        reportOutboxDao.upsertReportOutbox(
            outboxEntity.copy(
                status = status.name,
                lastFailureReason = lastFailureReason,
                updatedAt = now,
            ),
        )
    }

    private suspend fun refreshReportHistoryFromServerIfPossible() {
        runCatching {
            val datasource = hazardReportsRemoteDataSource ?: return@runCatching

            // Task 5.9 — 목록 조회도 401 자동 재발급 흐름으로 감싼다. 세션이 없으면 그냥 skip.
            val serverItems =
                runAuthenticated { token ->
                    fetchAllReportHistoryFromServer(datasource = datasource, token = token)
                } ?: return@runCatching
            serverReportHistory.value = serverItems.map { item -> item.toHistoryData() }
        }
    }

    private suspend fun fetchAllReportHistoryFromServer(
        datasource: HazardReportsRemoteDataSource,
        token: String,
    ): List<HazardReportListItemDto> {
        val reports = mutableListOf<HazardReportListItemDto>()
        var cursor: Long? = null

        do {
            val page =
                datasource.getMyHazardReports(
                    accessToken = token,
                    cursor = cursor,
                    size = DEFAULT_PAGE_SIZE,
                )
            reports += page.content
            cursor = page.nextCursor
        } while (page.hasNext && cursor != null)

        return reports
    }

    private suspend fun fetchServerReportDetail(reportId: Long): ReportHistoryDetailData? =
        runCatching {
            val datasource = hazardReportsRemoteDataSource ?: return@runCatching null

            // Task 5.9 — 상세 조회도 동일하게 runner.run으로 감싼다.
            runAuthenticated { token ->
                datasource.getMyHazardReportDetail(accessToken = token, reportId = reportId).toDetailData()
            }
        }.getOrNull()

    private fun HazardReportListItemDto.toHistoryData(): ReportHistoryData {
        val createdAtMillis = createdAt.toServerEpochMillisOrNull() ?: clock()
        return ReportHistoryData(
            historyId = "$SERVER_HISTORY_PREFIX$reportId",
            reportCategory = reportType,
            processingStatus = status.toReportProcessingStatusOrNull(),
            // Task 5.7 — BE list 응답의 description/address 그대로 노출 (mypage 카드용).
            description = description,
            address = address,
            latitude = reportPoint.lat,
            longitude = reportPoint.lng,
            photoUri = null,
            imageUrl = representativeImageUrl,
            source = ReportHistorySource.Server,
            serverReportId = reportId,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = createdAtMillis,
        )
    }

    private fun HazardReportDetailDto.toDetailData(): ReportHistoryDetailData {
        val createdAtMillis = createdAt.toServerEpochMillisOrNull() ?: clock()
        return ReportHistoryDetailData(
            historyId = "$SERVER_HISTORY_PREFIX$reportId",
            reportCategory = reportType,
            processingStatus = status.toReportProcessingStatusOrNull(),
            description = description,
            address = null,
            latitude = reportPoint.lat,
            longitude = reportPoint.lng,
            imageRefs = imageUrls,
            source = ReportHistorySource.Server,
            serverReportId = reportId,
            createdAtMillis = createdAtMillis,
        )
    }

    /**
     * 서버 응답의 createdAt(ISO 8601 형식)을 epoch millis로 변환한다.
     *
     * BE는 timezone offset이 없는 LocalDateTime 형식(예: `"2026-05-15T15:49:30.628581"`)으로 내려보내며,
     * 실제 값은 **KST 시간**이다 (logcat 진단으로 확인). 따라서 offset 없는 케이스는 KST로 해석해야 한다.
     *
     * Fallback chain:
     * 1. ISO Instant("...Z") — 추후 BE가 UTC offset을 명시할 때 자동 호환
     * 2. ISO with offset("...+09:00" 등) — BE가 offset을 명시할 때
     * 3. offset 없는 LocalDateTime — 현재 BE 응답 케이스. KST로 해석한다.
     */
    private fun String.toServerEpochMillisOrNull(): Long? {
        runCatching { Instant.parse(this).toEpochMilli() }
            .getOrNull()
            ?.let { return it }
        runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
            .getOrNull()
            ?.let { return it }
        return runCatching {
            LocalDateTime.parse(this)
                .atZone(KST_ZONE_ID)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val HTTP_UNAUTHORIZED = 401
        // BE가 createdAt을 offset 없는 LocalDateTime(KST 시간)으로 내려보내므로 명시적으로 KST로 해석한다.
        private val KST_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

private fun Throwable.toSubmitFailureReason(): ReportSubmitFailureReason =
    when (this) {
        is HazardReportsApiException ->
            when {
                httpStatusCode == 401 -> ReportSubmitFailureReason.Unauthorized
                httpStatusCode in 400..499 -> ReportSubmitFailureReason.InvalidInput
                else -> ReportSubmitFailureReason.Unknown
            }
        is java.io.IOException -> ReportSubmitFailureReason.Network
        else -> ReportSubmitFailureReason.Unknown
    }

private fun ReportDraftEntity.toData(): ReportDraftData =
    ReportDraftData(
        draftId = draftId,
        reportCategory = reportCategory,
        description = description,
        address = address,
        addressDetail = addressDetail,
        latitude = latitude,
        longitude = longitude,
        locationSource = locationSource,
        photos = resolveDraftPhotosFromEntity(),
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
    )

private fun ReportDraftData.toEntity(): ReportDraftEntity {
    val firstPhoto = photos.firstOrNull()
    return ReportDraftEntity(
        draftId = draftId,
        reportCategory = reportCategory,
        description = description,
        address = address,
        addressDetail = addressDetail,
        latitude = latitude,
        longitude = longitude,
        locationSource = locationSource,
        // legacy 단일 사진 컬럼도 first photo로 채워둔다. 미래 cleanup 시 제거 예정이지만
        // 그 사이에 old reader가 이 row를 읽어도 1장은 복원 가능하도록 유지.
        photoUri = firstPhoto?.localUri,
        photoMimeType = firstPhoto?.mimeType,
        photoSizeBytes = firstPhoto?.sizeBytes,
        photosJson = serializeDraftPhotos(photos),
        createdAt = createdAtMillis,
        updatedAt = updatedAtMillis,
    )
}

/**
 * v7 photosJson이 있으면 그걸 source of truth로 사용한다.
 * 없으면 legacy single-photo 컬럼으로 fallback (v6 이전 row 또는 마이그레이션 직후 row).
 */
private fun ReportDraftEntity.resolveDraftPhotosFromEntity(): List<ReportDraftPhotoData> {
    val deserialized = photosJson?.let(::deserializeDraftPhotos)
    if (!deserialized.isNullOrEmpty()) return deserialized

    val legacyUri = photoUri?.takeIf(String::isNotBlank) ?: return emptyList()
    return listOf(
        ReportDraftPhotoData(
            localUri = legacyUri,
            mimeType = photoMimeType,
            sizeBytes = photoSizeBytes,
        ),
    )
}

/**
 * `ReportDraftPhotoData` 리스트를 JSON 배열 문자열로 직렬화.
 * 빈 리스트는 null로 저장하여 SQL 컬럼 의미를 "사진 없음"으로 명확하게 둔다.
 */
private fun serializeDraftPhotos(photos: List<ReportDraftPhotoData>): String? {
    if (photos.isEmpty()) return null
    val array = org.json.JSONArray()
    photos.forEach { photo ->
        val obj = org.json.JSONObject()
        obj.put("uri", photo.localUri)
        photo.mimeType?.let { obj.put("mime", it) }
        photo.sizeBytes?.let { obj.put("size", it) }
        array.put(obj)
    }
    return array.toString()
}

private fun deserializeDraftPhotos(json: String): List<ReportDraftPhotoData> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(json)
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val uri = obj.optString("uri").takeIf { it.isNotBlank() } ?: continue
                add(
                    ReportDraftPhotoData(
                        localUri = uri,
                        mimeType = obj.optString("mime").takeIf { it.isNotBlank() },
                        sizeBytes = if (obj.has("size")) obj.optLong("size") else null,
                    ),
                )
            }
        }
    }.getOrElse { emptyList() }
}

private fun ReportOutboxEntity.toData(): ReportOutboxData =
    ReportOutboxData(
        outboxId = outboxId,
        reportCategory = reportCategory,
        description = description,
        address = address,
        addressDetail = addressDetail,
        latitude = latitude,
        longitude = longitude,
        photoUri = photoUri,
        photoMimeType = photoMimeType,
        photoSizeBytes = photoSizeBytes,
        // v10 — Task 5.5: 새 photos 컬럼이 있으면 그걸 source of truth로, 없으면 legacy 단일 photo로 fallback.
        photos = resolveOutboxPhotosFromEntity(),
        imageObjectKeys = deserializeStringList(imageObjectKeysJson),
        thumbnailObjectKeys = deserializeStringList(thumbnailObjectKeysJson),
        status = runCatching { ReportOutboxStatus.valueOf(status) }.getOrDefault(ReportOutboxStatus.Pending),
        serverReportId = serverReportId,
        lastFailureReason = lastFailureReason,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
    )

private fun ReportOutboxData.toEntity(): ReportOutboxEntity {
    val firstPhoto = photos.firstOrNull()
    return ReportOutboxEntity(
        outboxId = outboxId,
        reportCategory = reportCategory,
        description = description,
        address = address,
        addressDetail = addressDetail,
        latitude = latitude,
        longitude = longitude,
        // legacy 단일 사진 컬럼은 첫 사진으로 채워둔다. 마이그레이션 직후 old reader가 이 row를
        // 읽어도 1장은 복원 가능하도록 유지.
        photoUri = firstPhoto?.localUri ?: photoUri,
        photoMimeType = firstPhoto?.mimeType ?: photoMimeType,
        photoSizeBytes = firstPhoto?.sizeBytes ?: photoSizeBytes,
        photosJson = serializeOutboxPhotos(photos),
        imageObjectKeysJson = serializeStringList(imageObjectKeys),
        thumbnailObjectKeysJson = serializeStringList(thumbnailObjectKeys),
        status = status.name,
        serverReportId = serverReportId,
        lastFailureReason = lastFailureReason,
        createdAt = createdAtMillis,
        updatedAt = updatedAtMillis,
    )
}

/**
 * v10 photosJson이 있으면 그걸 source of truth로 사용한다.
 * 없으면 legacy single-photo 컬럼으로 fallback (v9 이전 row).
 */
private fun ReportOutboxEntity.resolveOutboxPhotosFromEntity(): List<ReportOutboxPhotoData> {
    val deserialized = photosJson?.let(::deserializeOutboxPhotos)
    if (!deserialized.isNullOrEmpty()) return deserialized

    val legacyUri = photoUri?.takeIf(String::isNotBlank) ?: return emptyList()
    return listOf(
        ReportOutboxPhotoData(
            localUri = legacyUri,
            mimeType = photoMimeType,
            sizeBytes = photoSizeBytes,
        ),
    )
}

private fun serializeOutboxPhotos(photos: List<ReportOutboxPhotoData>): String? {
    if (photos.isEmpty()) return null
    val array = org.json.JSONArray()
    photos.forEach { photo ->
        val obj = org.json.JSONObject()
        obj.put("uri", photo.localUri)
        photo.mimeType?.let { obj.put("mime", it) }
        photo.sizeBytes?.let { obj.put("size", it) }
        array.put(obj)
    }
    return array.toString()
}

private fun deserializeOutboxPhotos(json: String): List<ReportOutboxPhotoData> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(json)
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val uri = obj.optString("uri").takeIf { it.isNotBlank() } ?: continue
                add(
                    ReportOutboxPhotoData(
                        localUri = uri,
                        mimeType = obj.optString("mime").takeIf { it.isNotBlank() },
                        sizeBytes = if (obj.has("size")) obj.optLong("size") else null,
                    ),
                )
            }
        }
    }.getOrElse { emptyList() }
}

private fun serializeStringList(values: List<String>): String? {
    if (values.isEmpty()) return null
    val array = org.json.JSONArray()
    values.forEach { array.put(it) }
    return array.toString()
}

private fun deserializeStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = org.json.JSONArray(json)
        List(array.length()) { array.optString(it) }
            .filter { it.isNotBlank() }
    }.getOrElse { emptyList() }
}

private fun mergeServerAndLocalReportHistory(
    serverItems: List<ReportHistoryData>,
    localOutboxItems: List<ReportOutboxData>,
): List<ReportHistoryData> {
    val serverReportIds = serverItems.mapNotNull(ReportHistoryData::serverReportId).toSet()
    val localItems =
        localOutboxItems
            .map(ReportOutboxData::toLocalHistoryData)
            .filterNot { localItem ->
                localItem.serverReportId != null && localItem.serverReportId in serverReportIds
            }

    return (serverItems + localItems).sortedByDescending(ReportHistoryData::updatedAtMillis)
}

private fun ReportOutboxData.toLocalHistoryData(): ReportHistoryData =
    ReportHistoryData(
        historyId = "$LOCAL_HISTORY_PREFIX$outboxId",
        reportCategory = reportCategory,
        processingStatus = null,
        description = description.takeIf(String::isNotBlank),
        address = address,
        latitude = latitude,
        longitude = longitude,
        photoUri = photoUri,
        imageUrl = null,
        source = ReportHistorySource.LocalOutbox,
        serverReportId = serverReportId,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

private fun ReportOutboxData.toDetailData(): ReportHistoryDetailData =
    ReportHistoryDetailData(
        historyId = "$LOCAL_HISTORY_PREFIX$outboxId",
        reportCategory = reportCategory,
        processingStatus = null,
        description = description.takeIf(String::isNotBlank),
        address = address,
        latitude = latitude,
        longitude = longitude,
        imageRefs = listOfNotNull(photoUri?.takeIf(String::isNotBlank)),
        source = ReportHistorySource.LocalOutbox,
        serverReportId = serverReportId,
        createdAtMillis = createdAtMillis,
    )

private fun String.removePrefixOrNull(prefix: String): String? =
    takeIf { it.startsWith(prefix) }?.removePrefix(prefix)

private fun String.toReportProcessingStatusOrNull(): ReportProcessingStatus? =
    runCatching { ReportProcessingStatus.valueOf(this) }.getOrNull()

private fun countReportProcessingStatus(reports: List<ReportHistoryData>): ReportProcessingCounts =
    ReportProcessingCounts(
        pending = reports.count { it.processingStatus == ReportProcessingStatus.PENDING },
        approved = reports.count { it.processingStatus == ReportProcessingStatus.APPROVED },
    )

private const val SERVER_HISTORY_PREFIX = "server:"
private const val LOCAL_HISTORY_PREFIX = "outbox:"
