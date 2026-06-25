package com.ssafy.e102.eumgil.feature.report

import com.ssafy.e102.eumgil.data.repository.ReportDraftData
import com.ssafy.e102.eumgil.data.repository.ReportHistoryData
import com.ssafy.e102.eumgil.data.repository.ReportHistoryDetailData
import com.ssafy.e102.eumgil.data.repository.ReportHistorySource
import com.ssafy.e102.eumgil.data.repository.ReportOutboxData
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.ReportSubmitResult
import com.ssafy.e102.eumgil.feature.report.ReportType
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportHistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `history items are mapped in latest updated order`() =
        runTest {
            val repository = FakeReportHistoryRepository()
            val viewModel = ReportHistoryViewModel(reportRepository = repository)

            repository.emit(
                listOf(
                    reportHistory(
                        historyId = "outbox:old",
                        reportCategory = ReportType.STAIRS_STEP.apiValue,
                        address = "부산진구 가야대로 772",
                        updatedAtMillis = 1_714_097_400_000L,
                        source = ReportHistorySource.LocalOutbox,
                    ),
                    reportHistory(
                        historyId = "server:7",
                        reportCategory = ReportType.OTHER_OBSTACLE.apiValue,
                        address = null,
                        updatedAtMillis = 1_714_104_000_000L,
                        imageUrl = "https://example.com/reports/7.jpg",
                        source = ReportHistorySource.Server,
                        serverReportId = 7L,
                    ),
                ),
            )
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(ReportHistoryScreenState.CONTENT, uiState.screenState)
            assertEquals(listOf("server:7", "outbox:old"), uiState.reports.map { it.outboxId })
            assertEquals("기타 장애물", uiState.reports.first().title)
            assertEquals("위치 35.179600, 129.075600", uiState.reports.first().address)
            assertEquals("https://example.com/reports/7.jpg", uiState.reports.first().photoUri)
            assertEquals("서버 이력", uiState.reports.first().sourceLabel)
            assertTrue(uiState.reports.first().submittedAtText.contains("2024.04"))
        }

    @Test
    fun `empty history exposes empty state`() =
        runTest {
            val repository = FakeReportHistoryRepository()
            val viewModel = ReportHistoryViewModel(reportRepository = repository)

            repository.emit(emptyList())
            advanceUntilIdle()

            assertEquals(ReportHistoryScreenState.EMPTY, viewModel.uiState.value.screenState)
            assertTrue(viewModel.uiState.value.reports.isEmpty())
        }

    @Test
    fun `repository failure exposes error state`() =
        runTest {
            val repository = FakeReportHistoryRepository()
            val viewModel = ReportHistoryViewModel(reportRepository = repository)

            repository.fail()
            advanceUntilIdle()

            assertEquals(ReportHistoryScreenState.ERROR, viewModel.uiState.value.screenState)
        }

    @Test
    fun `report click loads selected detail`() =
        runTest {
            val repository = FakeReportHistoryRepository()
            val viewModel = ReportHistoryViewModel(reportRepository = repository)
            repository.emit(
                listOf(
                    reportHistory(
                        historyId = "server:7",
                        reportCategory = ReportType.SIDEWALK_MISSING.apiValue,
                        address = null,
                        updatedAtMillis = 1_714_104_000_000L,
                        source = ReportHistorySource.Server,
                        serverReportId = 7L,
                    ),
                ),
            )
            repository.details["server:7"] =
                ReportHistoryDetailData(
                    historyId = "server:7",
                    reportCategory = ReportType.SIDEWALK_MISSING.apiValue,
                    processingStatus = null,
                    description = "보행 가능한 인도가 없습니다.",
                    address = null,
                    latitude = 35.1796,
                    longitude = 129.0756,
                    imageRefs = listOf("https://example.com/reports/7-1.jpg"),
                    source = ReportHistorySource.Server,
                    serverReportId = 7L,
                    createdAtMillis = 1_714_104_000_000L,
                )
            advanceUntilIdle()

            viewModel.onAction(ReportHistoryUiAction.ReportClicked("server:7"))
            advanceUntilIdle()

            val detail = viewModel.uiState.value.selectedDetail
            assertEquals("인도 없음", detail?.title)
            assertEquals("보행 가능한 인도가 없습니다.", detail?.description)
            assertEquals("첨부 사진 1장", detail?.imageCountText)
            assertEquals(null, viewModel.uiState.value.detailLoadingHistoryId)
        }

    @Test
    fun `report cta emits navigate to report event`() =
        runTest {
            val repository = FakeReportHistoryRepository()
            val viewModel = ReportHistoryViewModel(reportRepository = repository)
            val event = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }
            advanceUntilIdle()

            viewModel.onAction(ReportHistoryUiAction.ReportCtaClicked)
            advanceUntilIdle()

            assertEquals(ReportHistoryUiEvent.NavigateToReport, event.await())
        }

    @Test
    fun `back click emits navigate back event`() =
        runTest {
            val repository = FakeReportHistoryRepository()
            val viewModel = ReportHistoryViewModel(reportRepository = repository)
            val event = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }
            advanceUntilIdle()

            viewModel.onAction(ReportHistoryUiAction.BackClicked)
            advanceUntilIdle()

            assertEquals(ReportHistoryUiEvent.NavigateBack, event.await())
        }
}

private class FakeReportHistoryRepository : ReportRepository {
    private val reports = MutableSharedFlow<ReportHistoryEmission>()
    val details = mutableMapOf<String, ReportHistoryDetailData>()

    override suspend fun getLatestDraft(): ReportDraftData? = null

    override suspend fun saveDraft(draft: ReportDraftData): ReportDraftData = draft

    override suspend fun deleteDraft(draftId: String) = Unit

    override suspend fun saveOutbox(outbox: ReportOutboxData): ReportOutboxData = outbox

    override suspend fun submitOutboxToServer(outboxId: String): ReportSubmitResult =
        ReportSubmitResult.Skipped

    override fun observeReportHistory(): Flow<List<ReportOutboxData>> =
        MutableSharedFlow<List<ReportOutboxData>>()

    override fun observeReportHistoryEntries(): Flow<List<ReportHistoryData>> =
        reports.map { emission ->
            when (emission) {
                ReportHistoryEmission.Failure -> error("history load failed")
                is ReportHistoryEmission.Items -> emission.items
            }
        }

    override suspend fun getReportHistoryDetail(historyId: String): ReportHistoryDetailData? = details[historyId]

    suspend fun emit(items: List<ReportHistoryData>) {
        reports.emit(ReportHistoryEmission.Items(items))
    }

    suspend fun fail() {
        reports.emit(ReportHistoryEmission.Failure)
    }
}

private sealed interface ReportHistoryEmission {
    data class Items(
        val items: List<ReportHistoryData>,
    ) : ReportHistoryEmission

    data object Failure : ReportHistoryEmission
}

private fun reportHistory(
    historyId: String,
    reportCategory: String,
    address: String?,
    updatedAtMillis: Long,
    source: ReportHistorySource,
    serverReportId: Long? = null,
    imageUrl: String? = null,
): ReportHistoryData =
    ReportHistoryData(
        historyId = historyId,
        reportCategory = reportCategory,
        processingStatus = null,
        description = null,
        address = address,
        latitude = 35.1796,
        longitude = 129.0756,
        photoUri = null,
        imageUrl = imageUrl,
        source = source,
        serverReportId = serverReportId,
        createdAtMillis = updatedAtMillis - 1_000L,
        updatedAtMillis = updatedAtMillis,
    )
