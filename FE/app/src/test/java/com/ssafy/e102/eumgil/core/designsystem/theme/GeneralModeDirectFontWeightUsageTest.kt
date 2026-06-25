package com.ssafy.e102.eumgil.core.designsystem.theme

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class GeneralModeDirectFontWeightUsageTest {
    @Test
    fun `priority general mode screens do not directly use black or extra bold`() {
        listOf(
            "src/main/java/com/ssafy/e102/eumgil/feature/auth/AuthScreen.kt",
            "src/main/java/com/ssafy/e102/eumgil/feature/onboarding/OnboardingScreen.kt",
            "src/main/java/com/ssafy/e102/eumgil/feature/tutorial/TutorialScreen.kt",
            "src/main/java/com/ssafy/e102/eumgil/feature/route/RouteSettingScreen.kt",
            "src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationScreen.kt",
            "src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt",
            "src/main/java/com/ssafy/e102/eumgil/feature/report/ReportHistoryScreen.kt",
            "src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewport.kt",
            "src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt",
            "src/main/java/com/ssafy/e102/eumgil/core/designsystem/component/place/PlaceListCard.kt",
        ).forEach { relativePath ->
            val source = File(relativePath).readText()

            assertFalse("$relativePath still uses FontWeight.Black directly.", source.contains("FontWeight.Black"))
            assertFalse("$relativePath still uses FontWeight.ExtraBold directly.", source.contains("FontWeight.ExtraBold"))
        }
    }
}
