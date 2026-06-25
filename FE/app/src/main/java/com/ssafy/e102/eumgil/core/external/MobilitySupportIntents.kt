package com.ssafy.e102.eumgil.core.external

import android.content.Intent
import android.net.Uri
import com.ssafy.e102.eumgil.core.model.LowFloorBusReservation
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val DURIBAL_PHONE_NUMBER = "1555-1114"
private const val LOW_FLOOR_BUS_REQUEST_BASE_URL =
    "https://bus.busan.go.kr/busanBIMS/mobile/webApp/page/customer/low_request.asp"
private const val BUSAN_BIMS_QUERY_CHARSET = "EUC-KR"
private val busanZoneId: ZoneId = ZoneId.of("Asia/Seoul")
private val lowFloorBusTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

fun createDialIntent(phoneNumber: String): Intent =
    Intent(Intent.ACTION_DIAL, Uri.parse(dialUriString(phoneNumber)))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun dialUriString(phoneNumber: String): String = "tel:${phoneNumber.filter(Char::isDigit)}"

fun createDuribalDialIntent(): Intent =
    createDialIntent(DURIBAL_PHONE_NUMBER)

fun duribalDialUriString(): String = dialUriString(DURIBAL_PHONE_NUMBER)

suspend fun requestLowFloorBusReservation(reservation: LowFloorBusReservation): Boolean =
    withContext(Dispatchers.IO) {
        val connection =
            (URL(createLowFloorBusReservationUrl(reservation)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                useCaches = false
            }
        try {
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

fun createLowFloorBusReservationUrl(
    reservation: LowFloorBusReservation,
    now: LocalDateTime = LocalDateTime.now(busanZoneId),
): String =
    buildString {
        append(LOW_FLOOR_BUS_REQUEST_BASE_URL)
        append("?svc_yy=").append(now.year)
        append("&svc_mm=").append(now.monthValue)
        append("&svc_dd=").append(now.dayOfMonth)
        append("&svc_tm=").append(now.format(lowFloorBusTimeFormatter))
        append("&svc_wd=").append(now.dayOfWeek.value % 7)
        append("&bstop_nm=").append(reservation.stopName.queryEncode())
        append("&ars_no=").append(reservation.arsNo.queryEncode())
        append("&line_no=").append(reservation.routeNo.queryEncode())
        append("&car_no=").append(reservation.vehicleNo.queryEncode())
        append("&wait_tm=").append(reservation.remainingMinute)
        append("&wait_dist=").append(reservation.remainingStopCount ?: 1)
    }

private fun String.queryEncode(): String = URLEncoder.encode(this, BUSAN_BIMS_QUERY_CHARSET)
