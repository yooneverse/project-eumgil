package com.ssafy.e102.eumgil.feature.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTopBarPolicyTest {
    @Test
    fun `report type selection step keeps back button to return to report home`() {
        assertTrue(reportTopBarShowsBackButton(ReportStep.TypeSelection))
    }

    @Test
    fun `report intermediate steps keep back button`() {
        assertTrue(reportTopBarShowsBackButton(ReportStep.LocationConfirm))
        assertTrue(reportTopBarShowsBackButton(ReportStep.DetailInput))
    }

    @Test
    fun `report completion hides back button`() {
        assertFalse(reportTopBarShowsBackButton(ReportStep.Complete))
    }

    // ─── Task 1.3 — 단계별 TopBar에 선택 type 라벨 표시 ─────────────────────
    //
    // 설계 근거: LocationConfirm / DetailInput 단계는 화면 콘텐츠(지도 / 입력 필드)로
    // 어떤 단계인지 충분히 인지 가능하므로, 단계명 대신 사용자가 선택한 type 라벨만
    // 노출하여 "지금 어떤 유형의 제보를 작성 중인지"를 시각 위계의 최상위로 둔다.

    @Test
    fun `type selection step always shows base label regardless of selected type`() {
        assertEquals("제보하기", reportStepTitle(ReportStep.TypeSelection))
        assertEquals("제보하기", reportStepTitle(ReportStep.TypeSelection, ReportType.STAIRS_STEP))
    }

    @Test
    fun `location confirm step keeps report form title when type is selected`() {
        assertEquals(
            "제보하기",
            reportStepTitle(ReportStep.LocationConfirm, ReportType.STAIRS_STEP),
        )
        assertEquals(
            "제보하기",
            reportStepTitle(ReportStep.LocationConfirm, ReportType.BRAILLE_BLOCK),
        )
        assertEquals(
            "제보하기",
            reportStepTitle(ReportStep.LocationConfirm, ReportType.RAMP),
        )
    }

    @Test
    fun `detail input step keeps report form title when type is selected`() {
        assertEquals(
            "제보하기",
            reportStepTitle(ReportStep.DetailInput, ReportType.STAIRS_STEP),
        )
        assertEquals(
            "제보하기",
            reportStepTitle(ReportStep.DetailInput, ReportType.SIDEWALK_MISSING),
        )
        assertEquals(
            "제보하기",
            reportStepTitle(ReportStep.DetailInput, ReportType.OTHER_OBSTACLE),
        )
    }

    @Test
    fun `intermediate steps fall back to step name when type is null`() {
        // type이 null인 경우는 비정상 흐름(필수값 위반)이지만 graceful fallback으로 단계명 노출.
        assertEquals("제보하기", reportStepTitle(ReportStep.LocationConfirm, null))
        assertEquals("제보하기", reportStepTitle(ReportStep.DetailInput, null))
    }

    @Test
    fun `complete step keeps base label even with type selected because summary card already shows it`() {
        assertEquals("제보 완료", reportStepTitle(ReportStep.Complete))
        assertEquals(
            "제보 완료",
            reportStepTitle(ReportStep.Complete, ReportType.SIDEWALK_WIDTH),
        )
    }
}
