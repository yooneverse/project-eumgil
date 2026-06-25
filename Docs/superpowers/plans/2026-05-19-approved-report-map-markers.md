# Approved Report Map Markers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드가 승인 제보 지도 API를 붙일 수 있도록 FE 지도 마커/바텀시트/상태/계약 골격을 먼저 구현하고, 백엔드 전달용 API 문서를 함께 만든다.

**Architecture:** FE에는 승인 제보 지도 전용 repository interface와 빈 구현을 두어 API가 붙기 전에는 런타임에서 제보 마커가 표시되지 않게 한다. 지도 렌더링은 `MapViewportOverlayState`에 승인 제보 point를 합성하는 구조로 열어두고, click target은 `approved-report:<reportId>` prefix로 분리해 기존 시설 상세 조회 경로와 충돌하지 않게 한다. 백엔드는 handoff 문서를 기준으로 endpoint와 DTO를 구현하고, 이후 FE/BE 연결 단계에서 빈 repository만 remote 구현으로 교체한다.

**Tech Stack:** Android Kotlin, Jetpack Compose, Kotlin Flow/StateFlow, Kakao Vector Map label overlay, Coil `SubcomposeAsyncImage`, JUnit/source-policy tests, Markdown API handoff.

---

## Scope

- FE 코드만 수정한다. BE 코드 수정은 하지 않는다.
- 런타임 가짜 제보 데이터는 넣지 않는다. API가 붙기 전까지 지도에는 승인 제보 마커가 0개로 표시된다.
- 테스트는 가짜 repository 데이터를 검증하지 않는다. 대신 click target parsing, zoom visibility 정책, overlay binding, sheet coordination, BackHandler, API handoff 문서 존재를 검증한다.
- "내가 한 제보"가 아니라 "모든 사용자의 승인된 제보"를 전제로 한다. 기존 `/hazard-reports/me` 기반 내 제보 이력 흐름과 섞지 않는다.
- 백엔드가 API를 붙이면 `ApprovedReportMapRepository` 구현만 remote로 바꾸면 되도록 FE 내부 상태와 UI는 먼저 준비한다.
- 메인 지도 데이터 대상에는 항상 포함될 수 있는 구조로 두되, 실제 마커 렌더링은 현재 zoom level이 `15` 이하일 때만 한다.
- 현재 코드에서 `ZoomInClicked`가 zoom level을 증가시키므로 `zoomLevel <= 15`를 "일정 이상 축소" 기준으로 둔다. 수동 QA에서 실제 Kakao zoom 체감이 반대이면 threshold 비교식만 조정한다.
- 마커는 노란색 삼각형 + 중앙 느낌표다. Kakao native marker 크기는 기존 빠른필터/시설 마커와 같은 28dp, fallback preview marker footprint는 기존 시설 marker와 같은 44dp로 맞춘다.
- 기존 facility marker layer의 z-order가 1000이고 generic overlay marker layer가 950이므로, 승인 제보 marker는 별도 clickable label layer를 추가해 z-order 1005로 둔다.
- 승인 제보 marker 클릭 시 시설 상세 sheet가 아니라 승인 제보 전용 sheet를 연다. sheet에는 제보 종류, 주소 또는 좌표, 설명, 사진이 있으면 사진을 표시한다.
- 경로점 지도 선택 모드나 음성 검색 sheet가 열려 있으면 기존 정책과 맞춰 승인 제보 sheet를 열지 않는다.
- 백엔드 전달 문서는 `Docs/API/2026-05-19-approved-report-map-api-handoff.md`에 만든다.
- 이 계획에는 커밋 단계가 없다. 커밋은 사용자가 별도로 요청할 때만 한다.

## File Structure

- Create: `FE/app/src/main/java/com/ssafy/e102/eumgil/data/repository/ApprovedReportMapRepository.kt`
  - viewport query를 받는 승인 제보 지도 repository interface, API 응답을 담을 FE-side entry model, API 전 빈 구현을 둔다.
- Create: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/model/ApprovedReportMarkerModels.kt`
  - 지도 UI용 승인 제보 marker model, zoom visibility 정책, sheet state, click target prefix/parse helper를 둔다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/di/RepositoryModule.kt`
  - `provideApprovedReportMapRepository()`를 추가하고 API 전에는 빈 구현을 반환한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/app/AppContainer.kt`
  - `approvedReportMapRepository` lazy property를 추가한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapRoute.kt`
  - MapViewModel factory에 repository를 전달하고 승인 제보 sheet BackHandler를 추가한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapContract.kt`
  - `MapUiState`와 `MapUiAction`에 승인 제보 marker/sheet 상태와 action을 추가한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapViewModel.kt`
  - 승인 제보 repository load hook, viewport query, zoom threshold visibility, marker click, sheet dismiss를 처리한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt`
  - marker click target prefix를 파싱해 승인 제보 action으로 라우팅하고, overlay state에 승인 제보 marker를 합성하며, 승인 제보 sheet와 최근목적지 sheet 우선순위를 조정한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlay.kt`
  - `MapViewportPointKind.APPROVED_REPORT`와 승인 제보 point 합성을 추가한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewportBindings.kt`
  - `KakaoOverlayMarkerKind.APPROVED_REPORT` 변환과 size/clickTargetId binding을 추가한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt`
  - 승인 제보 전용 Kakao label layer와 노란색 삼각형 bitmap style cache를 추가한다.
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt`
  - fallback 지도에서도 같은 승인 제보 marker spec을 그린다.
- Create: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedReportBottomSheetShell.kt`
  - 승인 제보 종류, 위치, 설명, 사진 thumbnail/fallback을 표시한다.
- Modify: `FE/app/src/main/res/values/strings.xml`
  - 승인 제보 marker 접근성 label과 sheet 문구를 추가한다.
- Create: `Docs/API/2026-05-19-approved-report-map-api-handoff.md`
  - 백엔드가 붙일 API endpoint, query, response DTO, 정책, FE 연결 지점을 정리한다.
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/ApprovedReportMarkerModelsTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/MapApprovedReportScaffoldViewModelTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/MapScreenApprovedReportPolicyTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/MapRouteApprovedReportBackHandlerPolicyTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportApprovedReportOverlayTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/component/KakaoApprovedReportMarkerBindingsTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedReportBottomSheetShellPolicyTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/docs/ApprovedReportMapApiHandoffDocumentTest.kt`

---

### Task 1: 승인 제보 지도 계약과 UI 모델 골격 추가

**Files:**
- Create: `FE/app/src/main/java/com/ssafy/e102/eumgil/data/repository/ApprovedReportMapRepository.kt`
- Create: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/model/ApprovedReportMarkerModels.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/ApprovedReportMarkerModelsTest.kt`

- [ ] **Step 1: marker model 테스트 작성**

`ApprovedReportMarkerModelsTest`에 zoom 정책, click target prefix, approved status mapping을 고정한다. 테스트 fixture는 런타임 데이터가 아니며 앱에 표시되지 않는다.

```kotlin
package com.ssafy.e102.eumgil.feature.map

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapEntry
import com.ssafy.e102.eumgil.feature.map.model.APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL
import com.ssafy.e102.eumgil.feature.map.model.approvedReportClickTargetId
import com.ssafy.e102.eumgil.feature.map.model.parseApprovedReportClickTargetId
import com.ssafy.e102.eumgil.feature.map.model.shouldShowApprovedReportMarkers
import com.ssafy.e102.eumgil.feature.map.model.toApprovedReportMarkerDataOrNull
import com.ssafy.e102.eumgil.feature.map.model.toApprovedReportSheetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedReportMarkerModelsTest {
    @Test
    fun `approved report markers are visible only when zoomed out enough`() {
        assertTrue(shouldShowApprovedReportMarkers(APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL))
        assertTrue(shouldShowApprovedReportMarkers(APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL - 1))
        assertFalse(shouldShowApprovedReportMarkers(APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL + 1))
    }

    @Test
    fun `approved report click target uses a distinct prefix`() {
        val clickTargetId = approvedReportClickTargetId(42L)

        assertEquals("approved-report:42", clickTargetId)
        assertEquals(42L, parseApprovedReportClickTargetId(clickTargetId))
        assertNull(parseApprovedReportClickTargetId("facility-42"))
        assertNull(parseApprovedReportClickTargetId("approved-report:not-a-number"))
    }

    @Test
    fun `only approved entries map to marker data and sheet state`() {
        val approved =
            ApprovedReportMapEntry(
                reportId = 42L,
                reportTypeApiValue = "BROKEN_BLOCK",
                statusApiValue = "APPROVED",
                coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
                address = "부산광역시 부산진구 중앙대로",
                description = "점자블록 파손",
                imageUrls = listOf("https://example.com/report.jpg"),
                approvedAt = "2026-05-19T09:00:00Z",
            )
        val pending =
            approved.copy(reportId = 43L, statusApiValue = "PENDING")

        val marker = approved.toApprovedReportMarkerDataOrNull()
        val hiddenMarker = pending.toApprovedReportMarkerDataOrNull()
        val sheetState = toApprovedReportSheetState(listOfNotNull(marker), selectedReportId = 42L)

        assertEquals(42L, marker?.reportId)
        assertEquals("점자블록 파손", marker?.description)
        assertNull(hiddenMarker)
        assertTrue(sheetState.isVisible)
        assertEquals("점자블록 파손", sheetState.report?.description)
    }
}
```

- [ ] **Step 2: repository 계약 파일 작성**

`ApprovedReportMapRepository.kt`를 만든다. API 전에는 빈 구현만 제공한다.

```kotlin
package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.GeoCoordinate

data class ApprovedReportMapQuery(
    val center: GeoCoordinate,
    val radiusMeters: Int,
)

data class ApprovedReportMapEntry(
    val reportId: Long,
    val reportTypeApiValue: String,
    val statusApiValue: String,
    val coordinate: GeoCoordinate,
    val address: String? = null,
    val description: String? = null,
    val imageUrls: List<String> = emptyList(),
    val approvedAt: String? = null,
)

interface ApprovedReportMapRepository {
    suspend fun getApprovedReports(query: ApprovedReportMapQuery): List<ApprovedReportMapEntry>
}

object EmptyApprovedReportMapRepository : ApprovedReportMapRepository {
    override suspend fun getApprovedReports(query: ApprovedReportMapQuery): List<ApprovedReportMapEntry> =
        emptyList()
}
```

- [ ] **Step 3: marker model 파일 작성**

`ApprovedReportMarkerModels.kt`를 만든다.

```kotlin
package com.ssafy.e102.eumgil.feature.map.model

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapEntry

const val APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL = 15
private const val APPROVED_REPORT_CLICK_TARGET_PREFIX = "approved-report:"
private const val APPROVED_REPORT_STATUS = "APPROVED"

data class ApprovedReportMarkerData(
    val reportId: Long,
    val reportTypeApiValue: String,
    val reportTypeLabel: String,
    val coordinate: GeoCoordinate,
    val address: String? = null,
    val description: String? = null,
    val imageUrls: List<String> = emptyList(),
    val approvedAt: String? = null,
) {
    val title: String
        get() = description?.takeIf(String::isNotBlank) ?: reportTypeLabel
}

data class ApprovedReportMarkerUiState(
    val reports: List<ApprovedReportMarkerData> = emptyList(),
    val visibleReports: List<ApprovedReportMarkerData> = emptyList(),
    val selectedReportId: Long? = null,
)

data class ApprovedReportSheetState(
    val report: ApprovedReportMarkerData? = null,
) {
    val isVisible: Boolean
        get() = report != null
}

fun shouldShowApprovedReportMarkers(zoomLevel: Int): Boolean =
    zoomLevel <= APPROVED_REPORT_VISIBLE_MAX_ZOOM_LEVEL

fun approvedReportClickTargetId(reportId: Long): String =
    "$APPROVED_REPORT_CLICK_TARGET_PREFIX$reportId"

fun parseApprovedReportClickTargetId(clickTargetId: String): Long? =
    clickTargetId
        .takeIf { it.startsWith(APPROVED_REPORT_CLICK_TARGET_PREFIX) }
        ?.removePrefix(APPROVED_REPORT_CLICK_TARGET_PREFIX)
        ?.toLongOrNull()

fun ApprovedReportMapEntry.toApprovedReportMarkerDataOrNull(): ApprovedReportMarkerData? {
    if (statusApiValue != APPROVED_REPORT_STATUS) return null
    return ApprovedReportMarkerData(
        reportId = reportId,
        reportTypeApiValue = reportTypeApiValue,
        reportTypeLabel = reportTypeApiValue.toApprovedReportTypeLabel(),
        coordinate = coordinate,
        address = address,
        description = description,
        imageUrls = imageUrls,
        approvedAt = approvedAt,
    )
}

fun toApprovedReportSheetState(
    reports: List<ApprovedReportMarkerData>,
    selectedReportId: Long?,
): ApprovedReportSheetState =
    ApprovedReportSheetState(report = reports.firstOrNull { it.reportId == selectedReportId })

private fun String.toApprovedReportTypeLabel(): String =
    when (this) {
        "BROKEN_BLOCK" -> "점자블록 파손"
        "OBSTACLE" -> "보행 장애물"
        "DAMAGED_ROAD" -> "보도 파손"
        "SIGNAL_ISSUE" -> "신호 시설 문제"
        else -> "주의 제보"
    }
```

- [ ] **Step 4: Task 1 테스트 실행**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*ApprovedReportMarkerModelsTest"
```

Expected: model helper 테스트가 통과한다.

---

### Task 2: MapViewModel 상태와 빈 repository 연결

**Files:**
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapContract.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapViewModel.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/di/RepositoryModule.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/app/AppContainer.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapRoute.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/MapApprovedReportScaffoldViewModelTest.kt`

- [ ] **Step 1: ViewModel 골격 테스트 작성**

API 전 기본 상태가 빈 마커이고, 빈 상태에서 sheet action이 잘 닫히는지 검증한다.

```kotlin
package com.ssafy.e102.eumgil.feature.map

import com.ssafy.e102.eumgil.data.repository.EmptyApprovedReportMapRepository
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapApprovedReportScaffoldViewModelTest {
    @Test
    fun `approved report scaffold starts empty before backend api is wired`() = runTest {
        val viewModel =
            createMapViewModel(
                approvedReportMapRepository = EmptyApprovedReportMapRepository,
            )

        viewModel.onRouteStarted()
        viewModel.onAction(
            MapUiAction.ViewportCameraChanged(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                zoomLevel = 15,
            ),
        )

        assertEquals(0, viewModel.uiState.value.approvedReportMarkerState.reports.size)
        assertEquals(0, viewModel.uiState.value.approvedReportMarkerState.visibleReports.size)
        assertFalse(viewModel.uiState.value.approvedReportSheetState.isVisible)
    }

    @Test
    fun `approved report sheet dismiss action is safe with no selected report`() = runTest {
        val viewModel =
            createMapViewModel(
                approvedReportMapRepository = EmptyApprovedReportMapRepository,
            )

        viewModel.onAction(MapUiAction.ApprovedReportSheetDismissed)

        assertFalse(viewModel.uiState.value.approvedReportSheetState.isVisible)
    }
}
```

`createMapViewModel(...)` helper는 기존 `MapViewModelTest` helper에 `approvedReportMapRepository` 인자를 추가해 재사용한다.

- [ ] **Step 2: MapContract 확장**

`MapUiState`에 승인 제보 상태를 추가한다.

```kotlin
val approvedReportMarkerState: ApprovedReportMarkerUiState = ApprovedReportMarkerUiState(),
val approvedReportSheetState: ApprovedReportSheetState = ApprovedReportSheetState(),
```

`MapUiAction`에 전용 action을 추가한다.

```kotlin
data class ApprovedReportMarkerTapped(
    val reportId: Long,
) : MapUiAction

data object ApprovedReportSheetDismissed : MapUiAction
```

- [ ] **Step 3: MapViewModel constructor/factory 확장**

`MapViewModel` 생성자와 `provideFactory(...)`에 repository 인자를 추가한다.

```kotlin
private val approvedReportMapRepository: ApprovedReportMapRepository = EmptyApprovedReportMapRepository,
```

factory에도 같은 인자를 추가하고 `MapRoute`에서 `appContainer.approvedReportMapRepository`를 넘긴다.

- [ ] **Step 4: ViewModel load hook 구현**

`onRouteStarted()` 또는 기존 route start load 흐름에서 승인 제보를 로드한다. API 전 빈 구현은 항상 빈 리스트를 반환하므로 지도에 아무 마커도 뜨지 않는다.

```kotlin
private var lastApprovedReportQuery: ApprovedReportMapQuery? = null

private fun loadApprovedReportsForViewport(force: Boolean = false) {
    val cameraTarget = mutableUiState.value.cameraTarget
    val query =
        ApprovedReportMapQuery(
            center = cameraTarget.center.toGeoCoordinate(),
            radiusMeters = approvedReportQueryRadiusMeters(cameraTarget.resolvedZoomLevel()),
        )
    if (!force && query == lastApprovedReportQuery) return
    lastApprovedReportQuery = query

    viewModelScope.launch {
        val reports =
            runCatching { approvedReportMapRepository.getApprovedReports(query) }
                .getOrDefault(emptyList())
                .mapNotNull { entry -> entry.toApprovedReportMarkerDataOrNull() }
        mutableUiState.update { state ->
            val visibleReports =
                if (shouldShowApprovedReportMarkers(state.cameraTarget.resolvedZoomLevel())) {
                    reports
                } else {
                    emptyList()
                }
            state.copy(
                approvedReportMarkerState =
                    state.approvedReportMarkerState.copy(
                        reports = reports,
                        visibleReports = visibleReports,
                    ),
            )
        }
    }
}

private fun MapCoordinate.toGeoCoordinate(): GeoCoordinate =
    GeoCoordinate(latitude = latitude, longitude = longitude)

private fun approvedReportQueryRadiusMeters(zoomLevel: Int): Int =
    when {
        zoomLevel <= 12 -> 12_000
        zoomLevel <= 15 -> 5_000
        else -> 2_000
    }
```

`handleViewportCameraChanged(...)`가 camera target을 갱신한 뒤 `updateApprovedReportVisibilityForZoom(zoomLevel)`를 호출한다. user gesture로 중심 좌표가 바뀌면 `loadApprovedReportsForViewport()`도 호출해서 remote 교체 후 viewport별 데이터를 다시 받게 한다.

- [ ] **Step 5: 승인 제보 marker action 구현**

기존 `handleMarkerTapped(markerId)`에는 섞지 않는다. 전용 action에서 sheet를 토글하고 기존 시설/map tap selection을 닫는다.

```kotlin
private fun handleApprovedReportMarkerTapped(reportId: Long) {
    val state = mutableUiState.value
    if (state.routeEndpointMapPickerState != null || state.isVoiceSearchVisible) return

    val selectedReportId = state.approvedReportMarkerState.selectedReportId
    val nextSelectedReportId = reportId.takeIf { selectedReportId != reportId }
    val nextSheetState =
        toApprovedReportSheetState(
            reports = state.approvedReportMarkerState.visibleReports,
            selectedReportId = nextSelectedReportId,
        )

    mutableUiState.update { current ->
        current.copy(
            selectedMarkerId = null,
            selectedMapPinCoordinate = null,
            facilityDetailSheetState = MapFacilityDetailSheetState(),
            approvedReportMarkerState =
                current.approvedReportMarkerState.copy(selectedReportId = nextSelectedReportId),
            approvedReportSheetState = nextSheetState,
        )
    }
}

private fun dismissApprovedReportSheet() {
    mutableUiState.update { state ->
        state.copy(
            approvedReportMarkerState =
                state.approvedReportMarkerState.copy(selectedReportId = null),
            approvedReportSheetState = ApprovedReportSheetState(),
        )
    }
}
```

`onAction`에는 다음 분기를 추가한다.

```kotlin
is MapUiAction.ApprovedReportMarkerTapped ->
    handleApprovedReportMarkerTapped(action.reportId)

MapUiAction.ApprovedReportSheetDismissed ->
    dismissApprovedReportSheet()
```

- [ ] **Step 6: DI 연결**

`RepositoryModule`에 provider를 추가한다.

```kotlin
fun provideApprovedReportMapRepository(): ApprovedReportMapRepository =
    EmptyApprovedReportMapRepository
```

`AppContainer`에 lazy property를 추가한다.

```kotlin
val approvedReportMapRepository: ApprovedReportMapRepository by lazy(LazyThreadSafetyMode.NONE) {
    RepositoryModule.provideApprovedReportMapRepository()
}
```

`MapRoute`의 factory 호출에 추가한다.

```kotlin
approvedReportMapRepository = appContainer.approvedReportMapRepository,
```

- [ ] **Step 7: Task 2 테스트 실행**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*MapApprovedReportScaffoldViewModelTest"
```

Expected: API 전 빈 상태 scaffold 테스트가 통과한다.

---

### Task 3: 지도 overlay, click routing, Kakao marker layer 확장

**Files:**
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlay.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewportBindings.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportApprovedReportOverlayTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/component/KakaoApprovedReportMarkerBindingsTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/MapScreenApprovedReportPolicyTest.kt`

- [ ] **Step 1: overlay binding 테스트 작성**

테스트 fixture로 직접 만든 `ApprovedReportMarkerData`를 overlay factory에 넣어 point 합성만 검증한다. 런타임 데이터는 만들지 않는다.

```kotlin
@Test
fun `approved report markers are added as non projection overlay points`() {
    val state =
        createMapMarkerViewportOverlayState(
            cameraTarget =
                MapCameraTarget(
                    center = MapCoordinate(35.1796, 129.0756),
                    source = MapCameraSource.DEFAULT_BUSAN,
                    zoomLevel = 15,
                ),
            markerOverlayState = MapMarkerOverlayState(),
            selectedMarkerId = null,
            currentLocation = null,
            currentLocationLabel = null,
            approvedReportMarkers =
                listOf(
                    ApprovedReportMarkerData(
                        reportId = 42L,
                        reportTypeApiValue = "OBSTACLE",
                        reportTypeLabel = "보행 장애물",
                        coordinate = GeoCoordinate(35.1796, 129.0756),
                    ),
                ),
        )

    val approvedPoint = state.points.single { it.kind == MapViewportPointKind.APPROVED_REPORT }
    assertEquals("approved-report:42", approvedPoint.overlayId)
    assertEquals("approved-report:42", approvedPoint.clickTargetId)
    assertFalse(approvedPoint.includeInProjection)
}
```

- [ ] **Step 2: Kakao binding 테스트 작성**

`KakaoApprovedReportMarkerBindingsTest`에서 native marker kind, size, clickTargetId, dedicated layer policy를 검증한다.

```kotlin
@Test
fun `approved report point converts to kakao approved report marker`() {
    val point =
        MapViewportPointOverlay(
            overlayId = "approved-report:42",
            coordinate = MapCoordinate(35.1796, 129.0756),
            kind = MapViewportPointKind.APPROVED_REPORT,
            label = "보행 장애물",
            includeInProjection = false,
            clickTargetId = "approved-report:42",
        )

    val marker = point.toKakaoProjectedPointMarkerState()

    assertEquals(KakaoOverlayMarkerKind.APPROVED_REPORT, marker?.kind)
    assertEquals(28, marker?.sizeDp)
    assertEquals("approved-report:42", marker?.clickTargetId)
}
```

```kotlin
@Test
fun `approved report kakao layer stays above facility marker layer`() {
    val source =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
            .readText()

    assertTrue(source.contains("KAKAO_APPROVED_REPORT_MARKER_LAYER_ID"))
    assertTrue(source.contains("KAKAO_APPROVED_REPORT_MARKER_LAYER_Z_ORDER = 1005"))
    assertTrue(source.contains("KAKAO_MARKER_LAYER_Z_ORDER = 1000"))
}
```

- [ ] **Step 3: MapScreen click routing 정책 테스트 작성**

`approved-report:<id>`가 `MarkerTapped`로 들어가지 않는 정책을 고정한다.

```kotlin
@Test
fun `approved report click target routes to approved report action before facility action`() {
    val source =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt")
            .readText()

    assertTrue(source.contains("parseApprovedReportClickTargetId"))
    assertTrue(source.contains("MapUiAction.ApprovedReportMarkerTapped"))
    assertTrue(source.contains("MapUiAction.MarkerTapped"))
}
```

- [ ] **Step 4: MapViewportOverlay point kind와 합성 추가**

`MapViewportPointKind`에 추가한다.

```kotlin
APPROVED_REPORT,
```

`createMapMarkerViewportOverlayState(...)` signature에 `approvedReportMarkers`를 추가한다.

```kotlin
approvedReportMarkers: List<ApprovedReportMarkerData> = emptyList(),
```

points 합성 시 시설 마커 뒤에 승인 제보를 append해서 fallback에서도 더 위에 그려지게 한다.

```kotlin
val approvedReportPoints =
    approvedReportMarkers.map { report ->
        val clickTargetId = approvedReportClickTargetId(report.reportId)
        MapViewportPointOverlay(
            overlayId = clickTargetId,
            coordinate = report.coordinate.toMapCoordinate(),
            kind = MapViewportPointKind.APPROVED_REPORT,
            label = report.reportTypeLabel,
            contentDescription = "주의 제보: ${report.reportTypeLabel}",
            includeInProjection = false,
            clickTargetId = clickTargetId,
        )
    }
```

- [ ] **Step 5: MapScreen overlay 합성과 click routing 추가**

`mapViewportState(uiState)`에서 `uiState.approvedReportMarkerState.visibleReports`를 overlay factory에 넘긴다.

```kotlin
approvedReportMarkers = uiState.approvedReportMarkerState.visibleReports,
```

`MapViewport`의 marker click lambda는 helper를 통해 dispatch한다.

```kotlin
onMarkerClick = { clickTargetId ->
    dispatchMapMarkerClick(clickTargetId, onAction)
}
```

helper는 `MapScreen.kt` 하단 private function으로 둔다.

```kotlin
private fun dispatchMapMarkerClick(
    clickTargetId: String,
    onAction: (MapUiAction) -> Unit,
) {
    val approvedReportId = parseApprovedReportClickTargetId(clickTargetId)
    if (approvedReportId != null) {
        onAction(MapUiAction.ApprovedReportMarkerTapped(approvedReportId))
    } else {
        onAction(MapUiAction.MarkerTapped(clickTargetId))
    }
}
```

- [ ] **Step 6: Kakao native marker kind와 전용 layer 추가**

`KakaoOverlayMarkerKind`에 추가한다.

```kotlin
APPROVED_REPORT,
```

`toKakaoProjectedPointMarkerState()`에서 `APPROVED_REPORT`를 null로 버리지 말고 28dp marker로 변환한다.

```kotlin
MapViewportPointKind.APPROVED_REPORT ->
    KakaoOverlayMarkerRenderState(
        overlayId = overlayId,
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        kind = KakaoOverlayMarkerKind.APPROVED_REPORT,
        sizeDp = 28,
        clickTargetId = clickTargetId,
    )
```

`KakaoMapViewport.kt`에 전용 layer 상수를 추가한다.

```kotlin
private const val KAKAO_APPROVED_REPORT_MARKER_LAYER_ID = "eumgil-approved-report-markers"
private const val KAKAO_APPROVED_REPORT_MARKER_LAYER_Z_ORDER = 1005
```

generic overlay marker sync에서 approved report marker는 별도 layer로 보낸다. 다른 route/focus overlay marker는 기존 `KAKAO_OVERLAY_MARKER_LAYER_ID`와 z-order 950을 유지한다.

- [ ] **Step 7: Kakao marker bitmap 스타일 추가**

`KakaoOverlayMarkerStyleCache`에 approved report style 분기를 추가한다.

```kotlin
KakaoOverlayMarkerKind.APPROVED_REPORT ->
    createApprovedReportWarningMarkerBitmap(sizePx)
```

bitmap 생성은 기존 facility marker bitmap처럼 `Bitmap` + `Canvas`를 사용한다. 색상은 다음 값으로 고정한다.

```kotlin
private const val APPROVED_REPORT_MARKER_FILL = 0xFFFFD84D.toInt()
private const val APPROVED_REPORT_MARKER_STROKE = 0xFF7A4F00.toInt()
private const val APPROVED_REPORT_MARKER_TEXT = 0xFF3A2A00.toInt()
```

삼각형은 marker bounds 안쪽에 stroke가 잘리지 않도록 padding을 두고, 중앙 느낌표는 bold text paint로 그린다.

- [ ] **Step 8: fallback marker spec 추가**

`MapViewportOverlayBackdrop.kt`의 `toViewportPointMarkerSpec()`에 `APPROVED_REPORT` 분기를 추가한다.

```kotlin
MapViewportPointKind.APPROVED_REPORT ->
    ViewportPointMarkerSpec(
        fillColor = Color(0xFFFFD84D),
        strokeColor = Color(0xFF7A4F00),
        contentColor = Color(0xFF3A2A00),
        sizeDp = 44.dp,
        shape = ViewportPointMarkerShape.TRIANGLE_WARNING,
    )
```

현재 `ViewportPointMarkerSpec`이 circle/diamond 중심이면 `ViewportPointMarkerShape.TRIANGLE_WARNING` enum 또는 dedicated boolean을 추가하고 draw branch에서 삼각형 + `!`를 그린다.

- [ ] **Step 9: Task 3 테스트 실행**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*MapViewportApprovedReportOverlayTest" --tests "*KakaoApprovedReportMarkerBindingsTest" --tests "*MapScreenApprovedReportPolicyTest"
```

Expected: 승인 제보 overlay 합성, Kakao marker 변환, click routing 정책 테스트가 통과한다.

---

### Task 4: 승인 제보 바텀시트와 sheet 우선순위/BackHandler 연결

**Files:**
- Create: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedReportBottomSheetShell.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapScreen.kt`
- Modify: `FE/app/src/main/java/com/ssafy/e102/eumgil/feature/map/MapRoute.kt`
- Modify: `FE/app/src/main/res/values/strings.xml`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedReportBottomSheetShellPolicyTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/MapRouteApprovedReportBackHandlerPolicyTest.kt`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/MapScreenApprovedReportPolicyTest.kt`

- [ ] **Step 1: 바텀시트 policy 테스트 작성**

이 프로젝트의 기존 screen/sheet 테스트는 source-policy test가 많으므로 동일한 방식으로 최소 UI 계약을 고정한다.

```kotlin
package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedReportBottomSheetShellPolicyTest {
    private val source =
        File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/ApprovedReportBottomSheetShell.kt")
            .readText()

    @Test
    fun `approved report sheet shows type location description and optional image`() {
        assertTrue(source.contains("ApprovedReportBottomSheetShell("))
        assertTrue(source.contains("report.reportTypeLabel"))
        assertTrue(source.contains("report.address"))
        assertTrue(source.contains("report.description"))
        assertTrue(source.contains("SubcomposeAsyncImage("))
        assertTrue(source.contains("report.imageUrls.firstOrNull()"))
    }

    @Test
    fun `approved report sheet does not add navigation cta in first scope`() {
        assertFalse(source.contains("FacilitySetDestinationClicked"))
        assertFalse(source.contains("RouteEditingTarget"))
    }
}
```

- [ ] **Step 2: MapRoute BackHandler policy 테스트 작성**

BackHandler는 `MapScreen`이 아니라 `MapRoute`에 있으므로 테스트도 `MapRoute.kt`를 본다.

```kotlin
package com.ssafy.e102.eumgil.feature.map

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRouteApprovedReportBackHandlerPolicyTest {
    @Test
    fun `approved report sheet has a route level back handler`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/MapRoute.kt")
                .readText()

        assertTrue(source.contains("uiState.approvedReportSheetState.isVisible"))
        assertTrue(source.contains("MapUiAction.ApprovedReportSheetDismissed"))
    }
}
```

- [ ] **Step 3: strings 추가**

`strings.xml`에 다음 string을 추가한다.

```xml
<string name="map_approved_report_marker_content_description">주의 제보: %1$s</string>
<string name="map_approved_report_sheet_title">주의 제보</string>
<string name="map_approved_report_sheet_type_label">제보 종류</string>
<string name="map_approved_report_sheet_location_label">위치</string>
<string name="map_approved_report_sheet_description_label">상세 내용</string>
<string name="map_approved_report_sheet_no_description">등록된 상세 내용이 없습니다.</string>
<string name="map_approved_report_sheet_no_photo">첨부 사진 없음</string>
<string name="map_approved_report_sheet_close">제보 상세 닫기</string>
```

- [ ] **Step 4: ApprovedReportBottomSheetShell 작성**

기존 `FacilityDetailBottomSheetShell`/`RecentDestinationBottomSheetShell`의 bottom sheet surface 패턴을 따른다. CTA는 넣지 않는다.

구성:
- header: `주의 제보` title과 close icon button
- body: 제보 종류, 주소 또는 좌표, 설명
- image: `imageUrls.firstOrNull()`이 있으면 `SubcomposeAsyncImage`, 없으면 작은 fallback surface 또는 "첨부 사진 없음"

사진 실패 fallback은 깨진 이미지 아이콘이 아니라 텍스트/neutral surface로 표시한다.

- [ ] **Step 5: MapScreen sheet 연결과 최근목적지 우선순위 조정**

`RecentDestinationBottomSheetShell` visible 조건에 승인 제보 sheet visible 부정을 추가한다.

```kotlin
uiState.approvedReportSheetState.isVisible.not()
```

승인 제보 sheet는 facility detail sheet와 동시에 보이지 않게 아래 정책을 지킨다.

- 승인 제보 marker를 누르면 `MapViewModel`에서 `facilityDetailSheetState`를 비운다.
- 승인 제보 sheet가 닫히면 최근목적지 sheet의 base state가 아직 visible이면 기존 restore/expanded 규칙대로 다시 보일 수 있다.
- 승인 제보 marker 때문에 닫힌 facility detail sheet는 자동 복원하지 않는다. 사용자가 새 marker를 선택해서 context가 바뀐 것으로 본다.

`MapScreen`에는 다음 형태로 sheet를 추가한다.

```kotlin
ApprovedReportBottomSheetShell(
    state = uiState.approvedReportSheetState,
    onDismissRequest = { onAction(MapUiAction.ApprovedReportSheetDismissed) },
)
```

- [ ] **Step 6: MapRoute BackHandler 추가**

현재 `MapRoute.kt`의 BackHandler 순서는 voice search, route endpoint picker, facility detail, app background다. 승인 제보 sheet handler는 voice search/route picker 뒤, facility detail 앞에 둔다.

```kotlin
BackHandler(
    enabled =
        uiState.approvedReportSheetState.isVisible &&
            uiState.routeEndpointMapPickerState == null &&
            uiState.isVoiceSearchVisible.not(),
) {
    viewModel.onAction(MapUiAction.ApprovedReportSheetDismissed)
}
```

- [ ] **Step 7: Task 4 테스트 실행**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*ApprovedReportBottomSheetShellPolicyTest" --tests "*MapRouteApprovedReportBackHandlerPolicyTest" --tests "*MapScreenApprovedReportPolicyTest"
```

Expected: sheet 구성, BackHandler 위치, 최근목적지/facility sheet coordination 정책 테스트가 통과한다.

---

### Task 5: 백엔드 API handoff 문서 작성

**Files:**
- Create: `Docs/API/2026-05-19-approved-report-map-api-handoff.md`
- Test: `FE/app/src/test/java/com/ssafy/e102/eumgil/docs/ApprovedReportMapApiHandoffDocumentTest.kt`

- [ ] **Step 1: handoff 문서 존재/핵심 필드 테스트 작성**

```kotlin
package com.ssafy.e102.eumgil.docs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedReportMapApiHandoffDocumentTest {
    @Test
    fun `approved report map api handoff documents endpoint and response fields`() {
        val source =
            File("../Docs/API/2026-05-19-approved-report-map-api-handoff.md")
                .readText()

        assertTrue(source.contains("GET /hazard-reports/approved"))
        assertTrue(source.contains("lat"))
        assertTrue(source.contains("lng"))
        assertTrue(source.contains("radius"))
        assertTrue(source.contains("reportId"))
        assertTrue(source.contains("reportType"))
        assertTrue(source.contains("status"))
        assertTrue(source.contains("reportPoint"))
        assertTrue(source.contains("imageUrls"))
        assertTrue(source.contains("approvedAt"))
    }
}
```

- [ ] **Step 2: handoff 문서 작성**

`Docs/API/2026-05-19-approved-report-map-api-handoff.md`를 다음 내용으로 만든다.

````markdown
# 승인 제보 지도 마커 API Handoff

## 목적

메인 지도에서 모든 사용자의 승인된 제보를 노란색 주의 마커로 표시하기 위한 백엔드 API 계약입니다. FE는 지도/마커/바텀시트 골격을 먼저 구현해두고, API 연결 전에는 빈 repository를 사용합니다.

## FE 연결 지점

- FE repository interface: `ApprovedReportMapRepository`
- FE query model: `ApprovedReportMapQuery(center: GeoCoordinate, radiusMeters: Int)`
- FE entry model: `ApprovedReportMapEntry`
- API 연결 시 교체할 파일:
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/data/repository/ApprovedReportMapRepository.kt`
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/data/remote/datasource/HazardReportsRemoteDataSource.kt`
  - `FE/app/src/main/java/com/ssafy/e102/eumgil/di/RepositoryModule.kt`

## Endpoint

```http
GET /hazard-reports/approved?lat={lat}&lng={lng}&radius={meters}
```

### Query Parameters

| name | type | required | description |
| --- | --- | --- | --- |
| `lat` | number | yes | 현재 지도 중심 위도 |
| `lng` | number | yes | 현재 지도 중심 경도 |
| `radius` | integer | yes | 조회 반경 meter |
| `cursor` | string | no | pagination이 필요할 경우 다음 페이지 cursor |
| `limit` | integer | no | 서버 기본값 사용 가능. 권장 최대 100 |

## Response

```json
{
  "reports": [
    {
      "reportId": 1001,
      "reportType": "BROKEN_BLOCK",
      "status": "APPROVED",
      "reportPoint": {
        "lat": 35.1797,
        "lng": 129.0752
      },
      "address": "부산광역시 부산진구 중앙대로 730",
      "description": "점자블록 일부가 파손되어 보행 주의가 필요합니다.",
      "imageUrls": ["https://cdn.example.com/reports/1001/1.jpg"],
      "representativeImageUrl": "https://cdn.example.com/reports/1001/1.jpg",
      "approvedAt": "2026-05-19T09:00:00Z"
    }
  ],
  "nextCursor": null
}
```

## Response Field Rules

- `reports`: 승인된 제보 목록입니다.
- `reportId`: FE marker click target에 사용하는 고유 id입니다.
- `reportType`: FE에서 사용자 표시 라벨로 매핑합니다.
- `status`: 반드시 `APPROVED`만 반환합니다.
- `reportPoint.lat`, `reportPoint.lng`: 지도 marker 좌표입니다.
- `address`: 없으면 FE는 좌표를 표시합니다.
- `description`: 없으면 FE는 "등록된 상세 내용이 없습니다."를 표시합니다.
- `imageUrls`: 0개 이상입니다. FE sheet에서는 첫 번째 이미지를 우선 표시합니다.
- `representativeImageUrl`: 있으면 `imageUrls[0]`과 같아도 됩니다.
- `approvedAt`: ISO-8601 문자열입니다.
- `nextCursor`: 다음 페이지가 없으면 `null`입니다.

## Backend Filtering Rules

- 모든 사용자 제보 중 `APPROVED` 상태만 반환합니다.
- `PENDING`, `REJECTED`, 삭제된 제보는 반환하지 않습니다.
- `/hazard-reports/me`와 분리된 API여야 합니다.
- 조회 범위는 `lat/lng/radius` 기준입니다.
- viewport가 넓을 수 있으므로 서버에서 최대 반환 개수 또는 cursor pagination을 적용합니다.

## Error Policy

- 인증이 필요한 API라면 401을 반환하고 FE는 빈 목록처럼 처리할 수 있어야 합니다.
- 서버 오류는 5xx로 반환합니다. FE는 marker 목록을 비우고 기존 지도 사용성을 유지합니다.
- 잘못된 query parameter는 400을 반환합니다.

## Image URL Policy

- `imageUrls`는 FE가 바로 표시 가능한 URL이어야 합니다.
- URL 만료가 있다면 최소 TTL을 백엔드 문서에 명시해야 합니다.
- 만료 URL이 내려오면 FE에서는 깨진 이미지를 fallback surface로 대체합니다.

## FE Remote Implementation Sketch

```kotlin
class RemoteApprovedReportMapRepository(
    private val remoteDataSource: HazardReportsRemoteDataSource,
) : ApprovedReportMapRepository {
    override suspend fun getApprovedReports(query: ApprovedReportMapQuery): List<ApprovedReportMapEntry> =
        remoteDataSource
            .getApprovedHazardReports(
                latitude = query.center.latitude,
                longitude = query.center.longitude,
                radiusMeters = query.radiusMeters,
            )
            .reports
            .map { dto -> dto.toApprovedReportMapEntry() }
}
```
````

- [ ] **Step 3: Task 5 테스트 실행**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*ApprovedReportMapApiHandoffDocumentTest"
```

Expected: API handoff 문서 존재와 핵심 endpoint/field 테스트가 통과한다.

---

### Task 6: 회귀 테스트와 API 전 수동 확인

**Files:**
- Existing tests under `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/`
- Existing tests under `FE/app/src/test/java/com/ssafy/e102/eumgil/feature/map/component/`
- `Docs/API/2026-05-19-approved-report-map-api-handoff.md`

- [ ] **Step 1: 지도 관련 단위 테스트 실행**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "*Map*"
```

Expected: 기존 지도 테스트와 신규 승인 제보 scaffold 테스트가 통과한다.

- [ ] **Step 2: Kotlin compile 확인**

Run:

```bash
./gradlew.bat :app:compileDebugUnitTestKotlin
```

Expected: Kotlin compile 성공.

- [ ] **Step 3: API 전 수동 확인**

API 연결 전에는 런타임 데이터가 없으므로 메인 지도에 승인 제보 marker가 뜨지 않는 것이 정상이다. 다음만 확인한다.

- 앱 실행 후 메인 지도 진입이 깨지지 않는다.
- zoom in/out, 빠른필터, 시설 marker click, 시설 상세 sheet가 기존처럼 동작한다.
- 최근목적지 sheet가 기존처럼 표시된다.
- 승인 제보 상태가 빈 목록일 때 sheet가 자동으로 뜨지 않는다.
- `Docs/API/2026-05-19-approved-report-map-api-handoff.md`를 백엔드 개발자에게 전달할 수 있다.

- [ ] **Step 4: API 연결 후 수동 QA 항목 기록**

백엔드가 API를 연결한 뒤 다음을 확인한다.

- 메인 지도에서 zoom level이 15 이하일 때 노란색 삼각형 승인 제보 marker가 보인다.
- zoom level이 16 이상이면 승인 제보 marker가 사라진다.
- 빠른필터로 뜨는 기존 시설 marker와 승인 제보 marker의 크기감이 맞다.
- 승인 제보 marker가 시설 marker와 겹치는 위치에서도 승인 제보 marker가 가려지지 않는다.
- 승인 제보 marker를 누르면 시설 상세가 아니라 승인 제보 sheet가 열린다.
- 사진 있는 제보는 사진이 보이고, 사진 없는 제보는 깨진 이미지가 보이지 않는다.
- 최근목적지 sheet가 보이는 상태에서 승인 제보 marker를 누르면 최근목적지는 내려가고 승인 제보 sheet가 보인다.
- 승인 제보 sheet를 닫으면 최근목적지 base state가 visible인 경우 기존 규칙대로 다시 보인다.
- 시설 상세 sheet가 열린 상태에서 승인 제보 marker를 누르면 시설 상세는 닫히고 승인 제보 sheet가 보이며, 닫아도 시설 상세가 자동 복원되지 않는다.
- route endpoint picker 또는 voice search가 열려 있을 때 승인 제보 marker click이 sheet를 열지 않는다.
- 기존 시설 marker 클릭, 시설 상세, 북마크, 경로 설정 흐름이 깨지지 않는다.

---

## Self-Review

- 요구사항 coverage: 메인 지도 승인 제보 marker 골격, 다른 사용자 승인 제보 전제, zoom threshold, 노란색 삼각형 느낌표 marker, 기존 빠른필터 시설 marker와 같은 크기감, marker click 시 제보 종류/사진 sheet 표시, 백엔드 전달 문서 생성을 모두 task에 반영했다.
- API 전 동작: 런타임 제보 데이터는 넣지 않는다. API 전에는 승인 제보 marker가 뜨지 않는 것이 정상이며, FE는 연결 가능한 구조만 준비한다.
- 기존 코드 정합성: `MapRoute`가 BackHandler 위치라는 점, `MapScreen`이 marker click lambda를 전달한다는 점, `MapViewModel.handleMarkerTapped()`가 시설 상세를 조회한다는 점, Kakao generic overlay layer z-order가 시설 marker보다 낮다는 점을 계획에 반영했다.
- API 전환성: `ApprovedReportMapQuery(center, radiusMeters)`를 받는 repository interface를 먼저 만들고, 이후 `EmptyApprovedReportMapRepository`만 remote 구현으로 교체하면 된다.
- FE/BE boundary: FE 코드와 `Docs/API` handoff 문서만 만든다. BE 코드는 수정하지 않는다.
