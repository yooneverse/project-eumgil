package com.ssafy.e102.eumgil.core.external

import com.ssafy.e102.eumgil.core.model.LowFloorBusReservation
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MobilitySupportIntentsTest {

    @Test
    fun `createLowFloorBusReservationUrl matches Busan BIMS low request contract`() {
        val url =
            createLowFloorBusReservationUrl(
                reservation =
                    LowFloorBusReservation(
                        stopName = "부산역",
                        arsNo = "70001",
                        routeNo = "7000",
                        vehicleNo = "1618",
                        remainingMinute = 14,
                        remainingStopCount = 2,
                    ),
                now = LocalDateTime.of(2026, 5, 13, 14, 13, 22),
            )

        assertEquals(
            "https://bus.busan.go.kr/busanBIMS/mobile/webApp/page/customer/low_request.asp" +
                "?svc_yy=2026" +
                "&svc_mm=5" +
                "&svc_dd=13" +
                "&svc_tm=141322" +
                "&svc_wd=3" +
                "&bstop_nm=%BA%CE%BB%EA%BF%AA" +
                "&ars_no=70001" +
                "&line_no=7000" +
                "&car_no=1618" +
                "&wait_tm=14" +
                "&wait_dist=2",
            url,
        )
    }
}
