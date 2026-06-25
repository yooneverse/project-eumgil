package com.ssafy.e102.eumgil.feature.report

import androidx.annotation.DrawableRes
import com.ssafy.e102.eumgil.R

fun String?.toReportTypeOrNull(): ReportType? =
    ReportType.entries.firstOrNull { type -> type.apiValue == this }

@DrawableRes
fun reportTypeMarkerIconRes(apiValue: String?): Int =
    apiValue.toReportTypeOrNull()?.markerIconRes ?: R.drawable.ic_report_other

val ReportType.displayLabel: String
    get() =
        when (this) {
            ReportType.STAIRS_STEP -> "계단·단차 있음"
            ReportType.BRAILLE_BLOCK -> "점자블록 문제"
            ReportType.SIDEWALK_MISSING -> "인도 없음"
            ReportType.RAMP -> "경사로 문제"
            ReportType.SIDEWALK_WIDTH -> "인도폭 문제"
            ReportType.OTHER_OBSTACLE -> "기타 장애물"
        }

@get:DrawableRes
val ReportType.markerIconRes: Int
    get() =
        when (this) {
            ReportType.STAIRS_STEP -> R.drawable.ic_report_stairs
            ReportType.BRAILLE_BLOCK -> R.drawable.ic_report_tactile_damage
            ReportType.SIDEWALK_MISSING -> R.drawable.ic_report_sidewalk
            ReportType.RAMP -> R.drawable.ic_report_ramp
            ReportType.SIDEWALK_WIDTH -> R.drawable.ic_report_roadway
            ReportType.OTHER_OBSTACLE -> R.drawable.ic_report_other
        }
