package com.ssafy.e102.eumgil.feature.report

import androidx.activity.ComponentActivity
import com.ssafy.e102.eumgil.core.location.CurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationGrantAccuracy
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.location.NoOpCurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.data.repository.ReportDraftData
import com.ssafy.e102.eumgil.data.repository.ReportHistoryData
import com.ssafy.e102.eumgil.data.repository.ReportHistorySource
import com.ssafy.e102.eumgil.data.repository.ReportOutboxData
import com.ssafy.e102.eumgil.data.repository.ReportProcessingCounts
import com.ssafy.e102.eumgil.data.repository.ReportProcessingStatus
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.ReportSubmitFailureReason
import com.ssafy.e102.eumgil.data.repository.ReportSubmitResult
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `processing counts are exposed in report ui state`() =
        runTest {
            val viewModel =
                createReportViewModel(
                    repository =
                        FakeReportRepository(
                            processingCounts = ReportProcessingCounts(pending = 3, approved = 2),
                        ),
                )
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.processingCounts.pending)
            assertEquals(2, viewModel.uiState.value.processingCounts.approved)
        }

    @Test
    fun `invalid submit marks errors and does not save outbox`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)
            val event = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }
            advanceUntilIdle()

            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertNull(repository.savedOutbox)
            assertEquals(ReportTypeError.Required, uiState.reportType.error)
            assertEquals(ReportLocationError.Required, uiState.location.error)
            assertFalse(uiState.isSubmitEnabled)
            assertEquals(ReportUiEvent.ScrollToFirstError, event.await())
        }

    @Test
    fun `valid submit saves outbox and emits complete event`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)
            advanceUntilIdle()

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.BRAILLE_BLOCK))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.DescriptionChanged("  점자블록 파손  "))
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val savedOutbox = requireNotNull(repository.savedOutbox)
            val uiState = viewModel.uiState.value

            assertEquals(ReportType.BRAILLE_BLOCK.apiValue, savedOutbox.reportCategory)
            assertEquals("점자블록 파손", savedOutbox.description)
            assertEquals(35.1796, savedOutbox.latitude, 0.0)
            assertEquals(129.0756, savedOutbox.longitude, 0.0)
            assertTrue(uiState.screenState is ReportScreenState.Completed)
            assertTrue(uiState.submitState is ReportSubmitState.Success)
            // Task 4.4 — NavigateToReportComplete event 제거 후 outboxId는 state로만 검증.
            val outboxState = uiState.outboxState
            assertTrue(outboxState is ReportOutboxState.Saved)
            assertEquals("outbox-1", (outboxState as ReportOutboxState.Saved).outboxId)
        }

    @Test
    fun `outbox failure keeps input and exposes retryable failure state`() =
        runTest {
            val repository = FakeReportRepository(failOutbox = true)
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.DescriptionChanged("장애물"))
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertEquals(ReportType.OTHER_OBSTACLE, uiState.reportType.value)
            assertEquals("장애물", uiState.description.value)
            assertTrue(uiState.screenState is ReportScreenState.Failure)
            assertTrue(uiState.submitState is ReportSubmitState.Failed)
            assertTrue(uiState.outboxState is ReportOutboxState.Failed)
        }

    @Test
    fun `submit success with server reportId completes flow with returned reportId`() =
        runTest {
            val repository =
                FakeReportRepository(
                    submitResultFactory = { outboxId ->
                        ReportSubmitResult.Success(outboxId = outboxId, serverReportId = 42L)
                    },
                )
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.RAMP))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertTrue(uiState.screenState is ReportScreenState.Completed)
            val submit = uiState.submitState
            assertTrue(submit is ReportSubmitState.Success)
            assertEquals(42L, (submit as ReportSubmitState.Success).reportId)
            assertTrue(uiState.outboxState is ReportOutboxState.Saved)
            assertEquals(listOf("outbox-1"), repository.submittedOutboxIds)
        }

    @Test
    fun `navigation guidance submit success emits return event with submitted report id`() =
        runTest {
            val repository =
                FakeReportRepository(
                    submitResultFactory = { outboxId ->
                        ReportSubmitResult.Success(outboxId = outboxId, serverReportId = 42L)
                    },
                )
            val viewModel = createReportViewModel(repository)
            val event =
                backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.first { emitted -> emitted is ReportUiEvent.ReturnToNavigationWithSubmittedReport }
                }
            advanceUntilIdle()

            viewModel.onAction(ReportUiAction.RouteEntered(ReportEntryPoint.NavigationGuidance))
            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.RAMP))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "Busan",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val emitted = event.await() as ReportUiEvent.ReturnToNavigationWithSubmittedReport
            assertEquals(42L, emitted.reportId)
        }

    @Test
    fun `server submit failure keeps outbox saved and surfaces retryable failure`() =
        runTest {
            val repository =
                FakeReportRepository(
                    submitResultFactory = { outboxId ->
                        ReportSubmitResult.Failure(
                            outboxId = outboxId,
                            reason = ReportSubmitFailureReason.Network,
                        )
                    },
                )
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.SIDEWALK_MISSING))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val uiState = viewModel.uiState.value

            assertTrue(uiState.screenState is ReportScreenState.Failure)
            assertEquals(
                ReportFailureReason.NetworkUnavailable,
                (uiState.screenState as ReportScreenState.Failure).reason,
            )
            val submit = uiState.submitState
            assertTrue(submit is ReportSubmitState.Failed)
            assertEquals(
                ReportFailureReason.NetworkUnavailable,
                (submit as ReportSubmitState.Failed).reason,
            )
            assertTrue(uiState.outboxState is ReportOutboxState.Saved)
        }

    @Test
    fun `retry after server failure reuses same outboxId without saving outbox again`() =
        runTest {
            var attempt = 0
            val repository =
                FakeReportRepository(
                    submitResultFactory = { outboxId ->
                        attempt += 1
                        if (attempt == 1) {
                            ReportSubmitResult.Failure(
                                outboxId = outboxId,
                                reason = ReportSubmitFailureReason.Network,
                            )
                        } else {
                            ReportSubmitResult.Success(outboxId = outboxId, serverReportId = 7L)
                        }
                    },
                )
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.RAMP))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val firstOutboxId = requireNotNull(repository.savedOutbox).outboxId

            viewModel.onAction(ReportUiAction.RetrySubmitClicked)
            advanceUntilIdle()

            assertEquals(2, repository.submittedOutboxIds.size)
            assertEquals(firstOutboxId, repository.submittedOutboxIds[0])
            assertEquals(firstOutboxId, repository.submittedOutboxIds[1])
            val uiState = viewModel.uiState.value
            assertTrue(uiState.screenState is ReportScreenState.Completed)
            val submit = uiState.submitState
            assertTrue(submit is ReportSubmitState.Success)
            assertEquals(7L, (submit as ReportSubmitState.Success).reportId)
        }

    @Test
    fun `selecting report type advances step to LocationConfirm`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            assertEquals(ReportStep.Home, viewModel.uiState.value.currentStep)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.STAIRS_STEP))
            advanceUntilIdle()

            assertEquals(ReportStep.LocationConfirm, viewModel.uiState.value.currentStep)
            assertEquals(ReportType.STAIRS_STEP, viewModel.uiState.value.reportType.value)
        }

    @Test
    fun `next step click on location confirm with valid location advances to detail input`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.RAMP))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.NextStepClicked)
            advanceUntilIdle()

            assertEquals(ReportStep.DetailInput, viewModel.uiState.value.currentStep)
        }

    @Test
    fun `next step click on location confirm without location stays on same step`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(ReportUiAction.NextStepClicked)
            advanceUntilIdle()

            assertEquals(ReportStep.LocationConfirm, viewModel.uiState.value.currentStep)
        }

    @Test
    fun `back click on intermediate step moves to previous step without navigating`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.BRAILLE_BLOCK))
            assertEquals(ReportStep.LocationConfirm, viewModel.uiState.value.currentStep)

            viewModel.onAction(ReportUiAction.BackClicked)
            advanceUntilIdle()

            assertEquals(ReportStep.TypeSelection, viewModel.uiState.value.currentStep)
        }

    @Test
    fun `back click on home emits NavigateBack`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)
            val event = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }
            advanceUntilIdle()

            viewModel.onAction(ReportUiAction.BackClicked)
            advanceUntilIdle()

            assertEquals(ReportUiEvent.NavigateBack, event.await())
            assertEquals(ReportStep.Home, viewModel.uiState.value.currentStep)
        }

    @Test
    fun `back click on guidance report type step returns to navigation`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)
            val event = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }

            viewModel.onAction(
                ReportUiAction.RouteEntered(
                    entryPoint = ReportEntryPoint.NavigationGuidance,
                    startNew = true,
                ),
            )
            advanceUntilIdle()

            assertEquals(ReportEntryPoint.NavigationGuidance, viewModel.uiState.value.entryPoint)
            assertEquals(ReportStep.TypeSelection, viewModel.uiState.value.currentStep)

            viewModel.onAction(ReportUiAction.BackClicked)
            advanceUntilIdle()

            assertEquals(ReportUiEvent.NavigateBack, event.await())
            assertEquals(ReportStep.TypeSelection, viewModel.uiState.value.currentStep)
        }

    @Test
    fun `guidance report entry clears stale failed submit state for a fresh report`() =
        runTest {
            val repository =
                FakeReportRepository(
                    submitResultFactory = { outboxId ->
                        ReportSubmitResult.Failure(
                            outboxId = outboxId,
                            reason = ReportSubmitFailureReason.Network,
                        )
                    },
                )
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.screenState is ReportScreenState.Failure)

            viewModel.onAction(
                ReportUiAction.RouteEntered(
                    entryPoint = ReportEntryPoint.NavigationGuidance,
                    startNew = true,
                ),
            )
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ReportEntryPoint.NavigationGuidance, state.entryPoint)
            assertEquals(ReportStep.TypeSelection, state.currentStep)
            assertTrue(state.screenState is ReportScreenState.Editing)
            assertTrue(state.submitState is ReportSubmitState.Idle)
            assertTrue(state.outboxState is ReportOutboxState.NotSaved)
            assertNull(state.reportType.value)
            assertNull(state.location.value)
        }

    @Test
    fun `successful submit sets current step to Complete`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            assertEquals(ReportStep.Complete, viewModel.uiState.value.currentStep)
        }

    @Test
    fun `report history click after complete resets form for next report`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)
            val event =
                async {
                    viewModel.uiEvent.first { emittedEvent ->
                        emittedEvent is ReportUiEvent.NavigateToReportHistory
                    }
                }

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "éºÂ€?ê³—ë–†ï§£??ë©¸ë ",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.screenState is ReportScreenState.Completed)

            viewModel.onAction(ReportUiAction.ReportHistoryClicked)
            advanceUntilIdle()

            assertEquals(ReportUiEvent.NavigateToReportHistory(), event.await())
            assertEquals(ReportUiState(), viewModel.uiState.value)
        }

    @Test
    fun `recent report click navigates to report history detail`() =
        runTest {
            val viewModel = createReportViewModel(FakeReportRepository())
            val event =
                backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.uiEvent.first { emittedEvent ->
                        emittedEvent is ReportUiEvent.NavigateToReportHistory
                    } as ReportUiEvent.NavigateToReportHistory
                }

            viewModel.onAction(ReportUiAction.RecentReportClicked("history-1"))
            advanceUntilIdle()

            assertEquals("history-1", event.await().historyId)
        }

    @Test
    fun `adding photos appends to list and updates count`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            repeat(3) { index ->
                viewModel.onAction(
                    ReportUiAction.PhotoSelected(
                        ReportPhoto(
                            localUri = "content://picker/photo-$index.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 1_024_000L,
                        ),
                    ),
                )
            }
            advanceUntilIdle()

            val photoInput = viewModel.uiState.value.photo
            assertEquals(3, photoInput.count)
            assertNull(photoInput.error)
            assertTrue(photoInput.canAddMore)
        }

    @Test
    fun `adding more than max photos is capped without TooMany error via UI path`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            repeat(ReportFormLimits.PHOTO_MAX_COUNT + 2) { index ->
                viewModel.onAction(
                    ReportUiAction.PhotoSelected(
                        ReportPhoto(
                            localUri = "content://picker/photo-$index.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 1_024_000L,
                        ),
                    ),
                )
            }
            advanceUntilIdle()

            val photoInput = viewModel.uiState.value.photo
            assertEquals(ReportFormLimits.PHOTO_MAX_COUNT, photoInput.count)
            assertFalse(photoInput.canAddMore)
            assertNull(photoInput.error)
        }

    @Test
    fun `selecting photo with same localUri is ignored to dedupe attachments`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            val photo =
                ReportPhoto(
                    localUri = "content://picker/123",
                    mimeType = "image/jpeg",
                    sizeBytes = 1024L,
                )
            viewModel.onAction(ReportUiAction.PhotoSelected(photo))
            viewModel.onAction(ReportUiAction.PhotoSelected(photo))
            advanceUntilIdle()

            val photoInput = viewModel.uiState.value.photo
            assertEquals(1, photoInput.count)
            assertEquals(photo.localUri, photoInput.values.single().localUri)
        }

    @Test
    fun `removing photo at index drops only that entry`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            repeat(3) { index ->
                viewModel.onAction(
                    ReportUiAction.PhotoSelected(
                        ReportPhoto(
                            localUri = "content://picker/photo-$index.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 1_024_000L,
                        ),
                    ),
                )
            }
            advanceUntilIdle()
            val before = viewModel.uiState.value.photo.values
            assertEquals(3, before.size)
            val targetUri = before[1].localUri

            viewModel.onAction(ReportUiAction.PhotoRemovedAt(1))
            advanceUntilIdle()

            val after = viewModel.uiState.value.photo.values
            assertEquals(2, after.size)
            assertFalse(after.any { it.localUri == targetUri })
        }

    @Test
    fun `outbox saves only first photo when multiple are attached`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.STAIRS_STEP))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            repeat(3) { index ->
                viewModel.onAction(
                    ReportUiAction.PhotoSelected(
                        ReportPhoto(
                            localUri = "content://picker/photo-$index.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = 1_024_000L,
                        ),
                    ),
                )
            }
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val savedOutbox = requireNotNull(repository.savedOutbox)
            val firstPhoto = viewModel.uiState.value.photo.values.first()
            assertEquals(firstPhoto.localUri, savedOutbox.photoUri)
        }

    @Test
    fun `description max length 300 hard limits extra input`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            val longText = "가".repeat(ReportFormLimits.DESCRIPTION_MAX_LENGTH + 1)
            viewModel.onAction(ReportUiAction.DescriptionChanged(longText))
            advanceUntilIdle()

            val description = viewModel.uiState.value.description
            assertEquals(ReportFormLimits.DESCRIPTION_MAX_LENGTH, description.value.length)
            assertNull(description.error)
        }

    @Test
    fun `editing after outbox failure clears retry failure state`() =
        runTest {
            val repository = FakeReportRepository(failOutbox = true)
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.DescriptionChanged("장애물"))
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            viewModel.onAction(ReportUiAction.DescriptionChanged("장애물 위치 변경"))
            val uiState = viewModel.uiState.value

            assertTrue(uiState.screenState is ReportScreenState.Editing)
            assertEquals(ReportSubmitState.Idle, uiState.submitState)
            assertEquals(ReportOutboxState.NotSaved, uiState.outboxState)
            assertTrue(uiState.isSubmitEnabled)
        }

    @Test
    fun `single photo attachment is preserved in outbox payload`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(
                ReportUiAction.PhotoSelected(
                    ReportPhoto(
                        localUri = "content://picker/photo-0.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 1_024_000L,
                    ),
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val savedOutbox = requireNotNull(repository.savedOutbox)
            val attachedPhoto = viewModel.uiState.value.photo.values.first()

            assertEquals(attachedPhoto.localUri, savedOutbox.photoUri)
            assertEquals(attachedPhoto.mimeType, savedOutbox.photoMimeType)
            assertEquals(attachedPhoto.sizeBytes, savedOutbox.photoSizeBytes)
        }

    @Test
    fun `location with out of range coordinate marks InvalidCoordinate error and blocks submit`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.STAIRS_STEP))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 200.0,
                            longitude = 129.0756,
                            address = "범위 밖 좌표",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(ReportLocationError.InvalidCoordinate, uiState.location.error)
            assertFalse(uiState.isSubmitEnabled)

            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            assertNull(repository.savedOutbox)
        }

    @Test
    fun `unauthorized server response maps to Unauthorized failure and keeps outbox saved`() =
        runTest {
            val repository =
                FakeReportRepository(
                    submitResultFactory = { outboxId ->
                        ReportSubmitResult.Failure(
                            outboxId = outboxId,
                            reason = ReportSubmitFailureReason.Unauthorized,
                        )
                    },
                )
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertTrue(uiState.screenState is ReportScreenState.Failure)
            assertEquals(
                ReportFailureReason.Unauthorized,
                (uiState.screenState as ReportScreenState.Failure).reason,
            )
            val submit = uiState.submitState
            assertTrue(submit is ReportSubmitState.Failed)
            assertEquals(
                ReportFailureReason.Unauthorized,
                (submit as ReportSubmitState.Failed).reason,
            )
            assertTrue(uiState.outboxState is ReportOutboxState.Saved)
        }

    @Test
    fun `start new report after complete resets form to type selection`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.STAIRS_STEP))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.DescriptionChanged("기존 입력"))
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.screenState is ReportScreenState.Completed)

            viewModel.onAction(ReportUiAction.StartNewReportClicked)
            advanceUntilIdle()

            val resetState = viewModel.uiState.value
            assertEquals(ReportStep.TypeSelection, resetState.currentStep)
            assertEquals(null, resetState.reportType.value)
            assertEquals("", resetState.description.value)
            assertTrue(resetState.screenState is ReportScreenState.Editing)
        }

    @Test
    fun `tab reentered after complete resets form to report home`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.STAIRS_STEP))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.screenState is ReportScreenState.Completed)

            viewModel.onAction(ReportUiAction.TabReentered)
            advanceUntilIdle()

            val resetState = viewModel.uiState.value
            assertEquals(ReportStep.Home, resetState.currentStep)
            assertEquals(null, resetState.reportType.value)
            assertTrue(resetState.screenState is ReportScreenState.Editing)
        }

    @Test
    fun `tab reentered while editing resets in progress form input`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.RAMP))
            viewModel.onAction(ReportUiAction.DescriptionChanged("작성 중인 설명"))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.screenState is ReportScreenState.Editing)

            viewModel.onAction(ReportUiAction.TabReentered)
            advanceUntilIdle()

            val resetState = viewModel.uiState.value
            assertEquals(ReportStep.Home, resetState.currentStep)
            assertEquals(null, resetState.reportType.value)
            assertEquals("", resetState.description.value)
            assertTrue(resetState.screenState is ReportScreenState.Editing)
        }

    @Test
    fun `tab reentered with persisted draft starts a fresh report state`() =
        runTest {
            val repository =
                FakeReportRepository(
                    latestDraft =
                        ReportDraftData(
                            draftId = "draft-1",
                            reportCategory = ReportType.RAMP.apiValue,
                            description = "임시저장된 설명",
                            address = null,
                            latitude = null,
                            longitude = null,
                            locationSource = null,
                            photos = emptyList(),
                            createdAtMillis = 10L,
                            updatedAtMillis = 20L,
                        ),
                )
            val viewModel = createReportViewModel(repository)
            advanceUntilIdle()

            assertEquals(ReportStep.Home, viewModel.uiState.value.currentStep)
            assertNull(viewModel.uiState.value.reportType.value)

            viewModel.onAction(ReportUiAction.TabReentered)
            advanceUntilIdle()

            val resetState = viewModel.uiState.value
            assertEquals(ReportStep.Home, resetState.currentStep)
            assertTrue(resetState.screenState is ReportScreenState.Editing)
            assertNull(resetState.reportType.value)
            assertNull(resetState.location.value)
        }

    @Test
    fun `tab reentered after submit failure resets stale failure state`() =
        runTest {
            val repository =
                FakeReportRepository(
                    submitResultFactory = { outboxId ->
                        ReportSubmitResult.Failure(
                            outboxId = outboxId,
                            reason = ReportSubmitFailureReason.Network,
                        )
                    },
                )
            val viewModel = createReportViewModel(repository)

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.screenState is ReportScreenState.Failure)

            viewModel.onAction(ReportUiAction.TabReentered)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.screenState is ReportScreenState.Editing)
            assertEquals(ReportStep.Home, state.currentStep)
            assertEquals(null, state.reportType.value)
            assertTrue(state.outboxState is ReportOutboxState.NotSaved)
            assertTrue(state.submitState is ReportSubmitState.Idle)
        }

    @Test
    fun `back to map after complete resets form and emits navigate to map event`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)
            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.OTHER_OBSTACLE))
            viewModel.onAction(
                ReportUiAction.LocationSelected(
                    location =
                        ReportLocation(
                            latitude = 35.1796,
                            longitude = 129.0756,
                            address = "부산시청 인근",
                        ),
                    source = ReportLocationSource.MapPin,
                ),
            )
            viewModel.onAction(ReportUiAction.SubmitClicked)
            advanceUntilIdle()

            val backToMapEvent = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }
            advanceUntilIdle()

            viewModel.onAction(ReportUiAction.BackToMapClicked)
            advanceUntilIdle()

            assertEquals(ReportUiEvent.NavigateToMap, backToMapEvent.await())
            val resetState = viewModel.uiState.value
            assertEquals(ReportStep.Home, resetState.currentStep)
            assertEquals(null, resetState.reportType.value)
        }

    // ─── 임시저장 제거 후 유형 선택 흐름 ────────────────────────────────────

    @Test
    fun `selecting report type with persisted draft applies type immediately`() =
        runTest {
            val repository =
                FakeReportRepository(
                    latestDraft =
                        ReportDraftData(
                            draftId = "draft-1",
                            reportCategory = ReportType.STAIRS_STEP.apiValue,
                            description = "기존 임시저장 설명",
                            address = null,
                            latitude = null,
                            longitude = null,
                            locationSource = null,
                            photos = emptyList(),
                            createdAtMillis = 10L,
                            updatedAtMillis = 20L,
                        ),
                )
            val viewModel = createReportViewModel(repository)
            advanceUntilIdle()

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.BRAILLE_BLOCK))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ReportType.BRAILLE_BLOCK, state.reportType.value)
            assertEquals(ReportStep.LocationConfirm, state.currentStep)
            assertNull(repository.deletedDraftId)
        }

    @Test
    fun `selecting report type without existing draft applies type immediately without dialog`() =
        runTest {
            val repository = FakeReportRepository()
            val viewModel = createReportViewModel(repository)
            advanceUntilIdle()

            viewModel.onAction(ReportUiAction.ReportTypeSelected(ReportType.RAMP))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ReportType.RAMP, state.reportType.value)
            assertEquals(ReportStep.LocationConfirm, state.currentStep)
        }

    // ─── Task 2.1 — 현재 위치 GPS 연동 ─────────────────────────────────────

    @Test
    fun `current location with granted permission and fresh last known immediately applies location`() =
        runTest {
            val freshSnapshot =
                LocationSnapshot(
                    latitude = 37.5665,
                    longitude = 126.9780,
                    accuracyMeters = 10f,
                    recordedAtEpochMillis = System.currentTimeMillis(),
                )
            val locationManager = FakeCurrentLocationManager(initialSnapshot = freshSnapshot)
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState = LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
                )
            val viewModel =
                createReportViewModel(
                    repository = FakeReportRepository(),
                    currentLocationManager = locationManager,
                    locationPermissionManager = permissionManager,
                )

            viewModel.onAction(ReportUiAction.CurrentLocationResetClicked)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(37.5665, state.location.value?.latitude ?: 0.0, 0.0001)
            assertEquals(126.9780, state.location.value?.longitude ?: 0.0, 0.0001)
            assertEquals(ReportLocationSource.CurrentLocation, state.location.source)
            assertFalse(state.location.isResolvingCurrentLocation)
            assertNull(state.location.error)
        }

    @Test
    fun `current location with denied permission emits RequestLocationPermission and stays resolving`() =
        runTest {
            val permissionManager =
                FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val viewModel =
                createReportViewModel(
                    repository = FakeReportRepository(),
                    locationPermissionManager = permissionManager,
            )
            val event = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.first() }
            runCurrent()

            viewModel.onAction(ReportUiAction.CurrentLocationResetClicked)
            runCurrent()

            assertEquals(ReportUiEvent.RequestLocationPermission, event.await())
            val state = viewModel.uiState.value
            assertTrue(state.location.isResolvingCurrentLocation)
            assertNull(state.location.value)
            viewModel.onAction(ReportUiAction.RefreshLocationPermission)
            advanceUntilIdle()
        }

    @Test
    fun `refresh location permission after still denied finishes resolving with permission error`() =
        runTest {
            val permissionManager =
                FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val viewModel =
                createReportViewModel(
                    repository = FakeReportRepository(),
                    locationPermissionManager = permissionManager,
                )

            viewModel.onAction(ReportUiAction.CurrentLocationResetClicked)
            runCurrent()

            // 사용자가 권한 다이얼로그에서 거부 후 Activity가 ON_RESUME으로 돌아옴.
            // permissionState는 여전히 Denied. RefreshLocationPermission 한 번 들어옴.
            viewModel.onAction(ReportUiAction.RefreshLocationPermission)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.location.isResolvingCurrentLocation)
            assertEquals(ReportLocationError.PermissionDenied, state.location.error)
        }

    @Test
    fun `refresh location permission after granted starts fetch and applies location`() =
        runTest {
            val freshSnapshot =
                LocationSnapshot(
                    latitude = 35.1796,
                    longitude = 129.0756,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = System.currentTimeMillis(),
                )
            val locationManager = FakeCurrentLocationManager(initialSnapshot = freshSnapshot)
            val permissionManager =
                FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val viewModel =
                createReportViewModel(
                    repository = FakeReportRepository(),
                    currentLocationManager = locationManager,
                    locationPermissionManager = permissionManager,
                )

            // 1) 사용자가 버튼 탭 → 권한 요청 emit, resolving=true
            viewModel.onAction(ReportUiAction.CurrentLocationResetClicked)
            runCurrent()
            assertTrue(viewModel.uiState.value.location.isResolvingCurrentLocation)

            // 2) 사용자가 허용 후 Activity ON_RESUME → permissionState=Granted, RefreshLocationPermission dispatch
            permissionManager.setPermissionState(LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE))
            viewModel.onAction(ReportUiAction.RefreshLocationPermission)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(35.1796, state.location.value?.latitude ?: 0.0, 0.0001)
            assertEquals(ReportLocationSource.CurrentLocation, state.location.source)
            assertFalse(state.location.isResolvingCurrentLocation)
        }

    @Test
    fun `current location with GPS disabled emits unavailable error`() =
        runTest {
            val permissionManager =
                FakeLocationPermissionManager(
                    initialState =
                        LocationPermissionState.Unavailable(
                            reason = com.ssafy.e102.eumgil.core.location
                                .LocationPermissionUnavailableReason.LOCATION_SERVICES_DISABLED,
                        ),
                )
            val viewModel =
                createReportViewModel(
                    repository = FakeReportRepository(),
                    locationPermissionManager = permissionManager,
                )

            viewModel.onAction(ReportUiAction.CurrentLocationResetClicked)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.location.isResolvingCurrentLocation)
            assertEquals(ReportLocationError.CurrentLocationUnavailable, state.location.error)
        }

    @Test
    fun `current location resolving state ignores rapid repeat clicks`() =
        runTest {
            val permissionManager =
                FakeLocationPermissionManager(initialState = LocationPermissionState.Denied)
            val viewModel =
                createReportViewModel(
                    repository = FakeReportRepository(),
                    locationPermissionManager = permissionManager,
                )

            viewModel.onAction(ReportUiAction.CurrentLocationResetClicked)
            viewModel.onAction(ReportUiAction.CurrentLocationResetClicked)
            viewModel.onAction(ReportUiAction.CurrentLocationResetClicked)
            runCurrent()

            // 첫 클릭만 처리되어야 함. refresh가 3회가 아닌 1회 호출.
            assertEquals(1, permissionManager.refreshCallCount)
            assertTrue(viewModel.uiState.value.location.isResolvingCurrentLocation)
            viewModel.onAction(ReportUiAction.RefreshLocationPermission)
            advanceUntilIdle()
        }
}

private class FakeReportRepository(
    private var latestDraft: ReportDraftData? = null,
    private val failOutbox: Boolean = false,
    private val failDeleteDraft: Boolean = false,
    private val processingCounts: ReportProcessingCounts = ReportProcessingCounts(),
    private val submitResultFactory: (String) -> ReportSubmitResult = { _ ->
        ReportSubmitResult.Skipped
    },
) : ReportRepository {
    var savedDraft: ReportDraftData? = null
        private set
    var savedOutbox: ReportOutboxData? = null
        private set
    var deletedDraftId: String? = null
        private set
    var submittedOutboxIds: MutableList<String> = mutableListOf()
        private set

    override fun observeReportHistory(): Flow<List<ReportOutboxData>> = flowOf(emptyList())

    override fun observeReportHistoryEntries(): Flow<List<ReportHistoryData>> =
        flowOf(
            buildList {
                repeat(processingCounts.pending) { index ->
                    add(fakeHistoryData("pending-$index", ReportProcessingStatus.PENDING))
                }
                repeat(processingCounts.approved) { index ->
                    add(fakeHistoryData("approved-$index", ReportProcessingStatus.APPROVED))
                }
            },
        )

    override suspend fun getLatestDraft(): ReportDraftData? = latestDraft

    override suspend fun saveDraft(draft: ReportDraftData): ReportDraftData {
        savedDraft = draft.copy(draftId = draft.draftId.ifBlank { "draft-1" })
        latestDraft = savedDraft
        return requireNotNull(savedDraft)
    }

    override suspend fun deleteDraft(draftId: String) {
        deletedDraftId = draftId
        if (failDeleteDraft) {
            error("draft delete failed")
        }
        if (latestDraft?.draftId == draftId) {
            latestDraft = null
        }
    }

    override suspend fun saveOutbox(outbox: ReportOutboxData): ReportOutboxData =
        if (failOutbox) {
            error("outbox save failed")
        } else {
            outbox.copy(outboxId = outbox.outboxId.ifBlank { "outbox-1" }).also { saved ->
                savedOutbox = saved
            }
        }

    override suspend fun submitOutboxToServer(outboxId: String): ReportSubmitResult {
        submittedOutboxIds.add(outboxId)
        return submitResultFactory(outboxId)
    }
}

private fun fakeHistoryData(
    historyId: String,
    status: ReportProcessingStatus,
): ReportHistoryData =
    ReportHistoryData(
        historyId = historyId,
        reportCategory = ReportType.BRAILLE_BLOCK.apiValue,
        processingStatus = status,
        description = null,
        address = "부산광역시 강서구 명지국제8로 10",
        latitude = 35.1,
        longitude = 128.9,
        photoUri = null,
        imageUrl = null,
        source = ReportHistorySource.Server,
        serverReportId = null,
        createdAtMillis = 1_700_000_000_000L,
        updatedAtMillis = 1_700_000_000_000L,
    )

// ─── Test helpers ─────────────────────────────────────────────────────────
//
// 기존 테스트는 위치 기능을 사용하지 않으므로 기본 Granted 상태의 Fake managers를 주입한다.
// Task 2.1 신규 테스트(권한 거부 / 타임아웃 / GPS 꺼짐)는 setPermissionState / setLocation
// 등을 통해 시나리오를 구성한다.

private fun createReportViewModel(
    repository: ReportRepository,
    currentLocationManager: CurrentLocationManager = FakeCurrentLocationManager(),
    locationPermissionManager: LocationPermissionManager = FakeLocationPermissionManager(),
    addressResolver: CurrentLocationAddressResolver = NoOpCurrentLocationAddressResolver,
): ReportViewModel =
    ReportViewModel(
        reportRepository = repository,
        currentLocationManager = currentLocationManager,
        locationPermissionManager = locationPermissionManager,
        addressResolver = addressResolver,
    )

private class FakeCurrentLocationManager(
    initialSnapshot: LocationSnapshot? = null,
) : CurrentLocationManager {
    private val mutableLatestLocation = MutableStateFlow(initialSnapshot)
    override val latestLocation: StateFlow<LocationSnapshot?> = mutableLatestLocation.asStateFlow()

    var refreshCallCount: Int = 0
        private set
    var startCallCount: Int = 0
        private set
    var stopCallCount: Int = 0
        private set

    override fun refreshLatestLocation() {
        refreshCallCount += 1
    }

    override fun startLocationUpdates() {
        startCallCount += 1
    }

    override fun stopLocationUpdates() {
        stopCallCount += 1
    }

    fun emitSnapshot(snapshot: LocationSnapshot?) {
        mutableLatestLocation.value = snapshot
    }
}

private class FakeLocationPermissionManager(
    initialState: LocationPermissionState =
        LocationPermissionState.Granted(LocationGrantAccuracy.PRECISE),
) : LocationPermissionManager {
    private val mutablePermissionState = MutableStateFlow(initialState)
    override val permissionState: StateFlow<LocationPermissionState> =
        mutablePermissionState.asStateFlow()

    var refreshCallCount: Int = 0
        private set
    var requestCallCount: Int = 0
        private set

    override fun refreshPermissionState() {
        refreshCallCount += 1
    }

    override fun requestLocationPermission(activity: ComponentActivity) {
        requestCallCount += 1
    }

    fun setPermissionState(state: LocationPermissionState) {
        mutablePermissionState.value = state
    }
}
