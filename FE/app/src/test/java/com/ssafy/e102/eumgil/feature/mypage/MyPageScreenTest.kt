package com.ssafy.e102.eumgil.feature.mypage

import com.ssafy.e102.eumgil.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyPageScreenTest {
    @Test
    fun `profile card headline uses user mode label instead of default name`() {
        val headlineRes =
            resolveHeadlineTextRes(
                MyPageUiState(
                    userMode = MyPageUserMode.MOBILITY_IMPAIRED,
                    mobilitySubtype = MyPageMobilitySubtype.MANUAL_WHEELCHAIR,
                ),
            )

        assertEquals(R.string.my_page_mode_mobility, headlineRes)
    }

    @Test
    fun `profile card avatar uses mobility subtype specific galmaegi image`() {
        assertEquals(
            R.drawable.manual_galmaegi,
            resolveProfileAvatarRes(
                MyPageUiState(
                    userMode = MyPageUserMode.MOBILITY_IMPAIRED,
                    mobilitySubtype = MyPageMobilitySubtype.MANUAL_WHEELCHAIR,
                ),
            ),
        )
        assertEquals(
            R.drawable.auto_galmaegi,
            resolveProfileAvatarRes(
                MyPageUiState(
                    userMode = MyPageUserMode.MOBILITY_IMPAIRED,
                    mobilitySubtype = MyPageMobilitySubtype.ELECTRIC_WHEELCHAIR,
                ),
            ),
        )
        assertEquals(
            R.drawable.crutch_galmaegi,
            resolveProfileAvatarRes(
                MyPageUiState(
                    userMode = MyPageUserMode.MOBILITY_IMPAIRED,
                    mobilitySubtype = MyPageMobilitySubtype.OTHER,
                ),
            ),
        )
    }

    @Test
    fun `profile card avatar falls back to my page icon when mobility subtype is unavailable`() {
        assertEquals(
            R.drawable.ic_mypage_sf3_person_circle,
            resolveProfileAvatarRes(
                MyPageUiState(
                    userMode = MyPageUserMode.LOW_VISION,
                    mobilitySubtype = null,
                ),
            ),
        )
    }

    @Test
    fun `main menu rows suppress ripple only for entries that open another screen`() {
        assertTrue(shouldSuppressMyPageMenuRipple(MyPageMenuItem.TEXT_SIZE))
        assertTrue(shouldSuppressMyPageMenuRipple(MyPageMenuItem.APP_HELP))
        assertTrue(shouldSuppressMyPageMenuRipple(MyPageMenuItem.PRIVACY_POLICY))
        assertTrue(shouldSuppressMyPageMenuRipple(MyPageMenuItem.SERVICE_TERMS))
        assertFalse(shouldSuppressMyPageMenuRipple(MyPageMenuItem.NOTICE))
    }

    @Test
    fun `my page menu row keeps no ripple modifier available for navigation rows`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()

        assertTrue(
            "My page menu rows should branch ripple suppression by menu item so only navigation rows lose the transition flash.",
            source.contains("shouldSuppressMyPageMenuRipple(menuItem)"),
        )
        assertTrue(
            "My page navigation rows should be able to suppress ripple with an explicit null indication.",
            source.contains("indication = null"),
        )
    }

    @Test
    fun `my page report history menu is owned by report tab`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()

        assertFalse(
            "My page should not expose report history as a menu row because report tab owns that flow.",
            source.contains("MyPageMenuItem.REPORT_HISTORY"),
        )
    }

    @Test
    fun `my page body exposes target profile quick actions menu and footer`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()

        assertTrue(
            "My page should expose the target profile overview card.",
            source.contains("ProfileOverviewCard(") &&
                source.contains("ProfileStatsRow("),
        )
        assertTrue(
            "My page should expose the target quick action cards for Duribal and guide.",
            source.contains("QuickActionGrid(") &&
                source.contains("R.string.my_page_duribal_title") &&
                source.contains("R.string.my_page_guide_title"),
        )
        assertTrue(
            "The regular policy/report menu rows and footer actions should be visible above the bottom tab.",
            source.contains("MyPageMenuItem.TEXT_SIZE") &&
                source.contains("R.string.my_page_menu_text_size") &&
                source.contains("MyPageMenuItem.PRIVACY_POLICY") &&
                source.contains("MyPageMenuItem.SERVICE_TERMS") &&
                source.contains("MyPageFooter("),
        )
    }

    @Test
    fun `my page surfaces match report tab flat white border style`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()
        val profileSection =
            source
                .substringAfter("private fun ProfileOverviewCard(")
                .substringBefore("@Composable\nprivate fun ProfileAvatar")
        val quickActionSection =
            source
                .substringAfter("private fun QuickActionCard(")
                .substringBefore("@Composable\nprivate fun MainMenuCard")
        val menuCardSection =
            source
                .substringAfter("private fun MainMenuCard(")
                .substringBefore("@Composable\nprivate fun MyPageMenuRow")

        assertTrue(
            "My page should use the same white page background as the report tab.",
            source.contains("private val MyPageBackground = Color.White"),
        )
        assertTrue(
            "Profile, quick action, and menu surfaces should use report-style subtle borders.",
            profileSection.contains("border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f))") &&
                quickActionSection.contains("border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f))") &&
                menuCardSection.contains("border = BorderStroke(1.dp, EumBorderSubtle.copy(alpha = 0.65f))"),
        )
        assertTrue(
            "My page cards should remove shadow elevation to match report surfaces.",
            profileSection.contains("shadowElevation = 0.dp") &&
                quickActionSection.contains("shadowElevation = 0.dp") &&
                menuCardSection.contains("shadowElevation = 0.dp"),
        )
        assertFalse(
            "My page should not keep the old shadowed card elevations.",
            profileSection.contains("shadowElevation = 4.dp") ||
                quickActionSection.contains("shadowElevation = 3.dp") ||
                menuCardSection.contains("shadowElevation = 4.dp"),
        )
    }

    @Test
    fun `my page action and menu text uses profile name weight`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()
        val quickActionSection =
            source
                .substringAfter("private fun QuickActionCard(")
                .substringBefore("@Composable\nprivate fun MainMenuCard")
        val menuRowSection =
            source
                .substringAfter("private fun MyPageMenuRow(")
                .substringBefore("@Composable\nprivate fun MyPageMenuDivider")

        assertTrue(
            "Quick action titles and main menu rows should match the semibold profile name weight.",
            quickActionSection.contains("fontWeight = FontWeight.SemiBold") &&
                menuRowSection.contains("fontWeight = FontWeight.SemiBold"),
        )
        assertFalse(
            "Quick action titles and main menu rows should not keep the heavier bold treatment.",
            quickActionSection.contains("fontWeight = FontWeight.Bold") ||
                menuRowSection.contains("fontWeight = FontWeight.Bold"),
        )
    }

    @Test
    fun `my page content scrolls above footer for small screens and large text`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()

        assertTrue(
            "My page should keep the account footer outside a scrollable weighted content area so expanded menu text cannot overlap it.",
            source.contains(".weight(1f)") &&
                source.contains(".verticalScroll(rememberScrollState())") &&
                source.indexOf("MainMenuCard(") < source.indexOf("MyPageFooter("),
        )
    }

    @Test
    fun `my page uses target icon resources`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()

        assertTrue(
            "My reports stat should use an SF Symbols 3 exclamation bubble style icon.",
            source.contains("iconRes = R.drawable.ic_mypage_sf3_exclamation_bubble,"),
        )
        assertTrue(
            "Bookmark stat should use an SF Symbols 3 bookmark style icon.",
            source.contains("iconRes = R.drawable.ic_mypage_sf3_bookmark,"),
        )
        assertTrue(
            "Recent navigation stat should use an SF Symbols 3 location.north.line style icon.",
            source.contains("iconRes = R.drawable.ic_mypage_sf3_location_north_line,"),
        )
        assertTrue(
            "Guide quick action and service terms row should share an SF Symbols 3 doc.text style icon.",
            source.contains("iconRes = R.drawable.ic_mypage_sf3_doc_text,") &&
                source.indexOf("iconRes = R.drawable.ic_mypage_sf3_doc_text,") !=
                source.lastIndexOf("iconRes = R.drawable.ic_mypage_sf3_doc_text,"),
        )
        assertTrue(
            "Notice and privacy rows should use SF Symbols 3 bell and shield style icons.",
            source.contains("iconRes = R.drawable.ic_mypage_sf3_bell,") &&
                source.contains("iconRes = R.drawable.ic_mypage_sf3_shield,"),
        )
        assertTrue(
            "My page row affordances should use an SF Symbols 3 chevron.right style icon without rotating dropdown assets.",
            source.contains("R.drawable.ic_mypage_sf3_chevron_right") &&
                !source.contains(".rotate(-90f)"),
        )
        assertFalse(
            "My page should not keep replaced screen-local PNG icon resources after SF Symbols 3 unification.",
            listOf(
                "ic_mypage_notice",
                "ic_mypage_privacy_policy",
                "ic_mypage_terms_guide",
                "ic_mypage_recent_navigation",
            ).any(source::contains),
        )
    }

    @Test
    fun `my page duribal quick action uses supplied vehicle image asset`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()
        val duribalAsset =
            File("src/main/res/drawable-nodpi/ic_mypage_duribal_call_vehicle.png")

        assertTrue(
            "Duribal quick action should use the supplied vehicle PNG asset again.",
            source.contains("iconRes = R.drawable.ic_mypage_duribal_call_vehicle,"),
        )
        assertTrue(
            "Duribal quick action should preserve the supplied image colors instead of tinting it blue.",
            source.contains("iconTint = Color.Unspecified"),
        )
        assertTrue("Duribal vehicle PNG asset should exist.", duribalAsset.exists())
    }

    @Test
    fun `my page SF Symbols 3 icon strokes use light weights`() {
        val drawableDir = File("src/main/res/drawable")
        val sf3IconFiles =
            drawableDir
                .listFiles { _, name ->
                    name.startsWith("ic_mypage_sf3_") && name.endsWith(".xml")
                }
                .orEmpty()
                .associateBy { it.name }

        val chevronFile = sf3IconFiles.getValue("ic_mypage_sf3_chevron_right.xml")
        val standardIconFiles = sf3IconFiles.values - chevronFile
        val strokeWidthRegex = Regex("""android:strokeWidth="([^"]+)"""")

        assertTrue("My page should have SF Symbols 3 icon resources.", sf3IconFiles.isNotEmpty())
        standardIconFiles.forEach { file ->
            val strokeWidths =
                strokeWidthRegex
                    .findAll(file.readText())
                    .map { it.groupValues[1] }
                    .toList()

            assertTrue("${file.name} should declare vector stroke widths.", strokeWidths.isNotEmpty())
            assertTrue(
                "${file.name} should use 1.5dp stroke widths for the lighter SF Symbols 3 style.",
                strokeWidths.all { it == "1.5" },
            )
        }

        val chevronStrokeWidths =
            strokeWidthRegex
                .findAll(chevronFile.readText())
                .map { it.groupValues[1] }
                .toList()

        assertTrue("Chevron icon should declare vector stroke widths.", chevronStrokeWidths.isNotEmpty())
        assertTrue(
            "Chevron should use a slightly stronger 1.8dp stroke to keep the navigation affordance visible.",
            chevronStrokeWidths.all { it == "1.8" },
        )
    }

    @Test
    fun `my page stat icons use compact visual size`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()

        assertTrue(
            "My page stat icons should be slightly smaller than the previous 30dp treatment.",
            source.contains("modifier = Modifier.size(28.dp),"),
        )
        assertFalse(
            "My page stat icons should not keep the old 30dp size.",
            source.contains("modifier = Modifier.size(30.dp),"),
        )
    }

    @Test
    fun `my page quick action icons use compact visual size`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()

        assertTrue(
            "My page quick action icons should be slightly smaller than the previous 36dp treatment.",
            source.contains("iconSize: Dp = 34.dp,"),
        )
        assertFalse(
            "My page quick action icons should not keep the old 36dp size.",
            source.contains("modifier = Modifier.size(36.dp),"),
        )
    }

    @Test
    fun `my page guide quick action icon is smaller than duribal icon`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()

        assertTrue(
            "Duribal quick action should keep the 34dp base icon size.",
            source.contains("iconSize = 34.dp,"),
        )
        assertTrue(
            "Guide quick action should use a smaller 30dp icon size.",
            source.contains("iconSize = 30.dp,"),
        )
        assertTrue(
            "QuickActionCard should apply the per-action icon size.",
            source.contains("modifier = Modifier.size(iconSize),"),
        )
    }

    @Test
    fun `my page display name falls back to generic user label`() {
        assertEquals("사용자", resolveDisplayName(MyPageUiState(displayName = null)))
        assertEquals("사용자", resolveDisplayName(MyPageUiState(displayName = " ")))
        assertEquals("민들레", resolveDisplayName(MyPageUiState(displayName = " 민들레 ")))
    }

    @Test
    fun `my page route owns duribal call confirmation flow`() {
        val myPageSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()
        val routeSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageRoute.kt")
                .readText()

        assertTrue(
            "MyPageScreen should render the duribal confirmation dialog for the restored CTA.",
            myPageSource.contains("isDuribalConfirmDialogVisible") ||
                myPageSource.contains("EumDuribalCallConfirmDialog("),
        )
        assertTrue(
            "MyPageRoute should create the duribal dial intent after confirmation.",
            routeSource.contains("createDuribalDialIntent") ||
                routeSource.contains("onDuribalCallClick"),
        )
    }

    @Test
    fun `my page screen renders duribal call confirmation dialog with yes and no actions`() {
        val myPageSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageScreen.kt")
                .readText()
        val dialogSource =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/component/dialog/EumDuribalCallConfirmDialog.kt")
                .readText()

        assertTrue(
            "MyPageScreen should render the duribal confirmation dialog instead of dialing immediately from the button tap.",
            myPageSource.contains("isDuribalConfirmDialogVisible") &&
                myPageSource.contains("EumDuribalCallConfirmDialog("),
        )
        assertTrue(
            "Duribal confirmation dialog should expose explicit yes and no actions for the restored CTA flow.",
            dialogSource.contains("onConfirm") &&
                dialogSource.contains("onDismiss") &&
                dialogSource.contains("my_page_duribal_call_dialog_confirm") &&
                dialogSource.contains("my_page_duribal_call_dialog_dismiss"),
        )
    }

    @Test
    fun `duribal confirm dialog follows app custom dialog shell`() {
        val dialogSource =
            File("src/main/java/com/ssafy/e102/eumgil/core/designsystem/component/dialog/EumDuribalCallConfirmDialog.kt")
                .readText()

        assertFalse(
            "Duribal dialog should not use the platform AlertDialog shell because it looks inconsistent with app dialogs.",
            dialogSource.contains("AlertDialog("),
        )
        assertTrue(
            "Duribal dialog should use the same custom dialog pattern as the app confirmation dialogs.",
            dialogSource.contains("Dialog(") &&
                dialogSource.contains("Surface(") &&
                dialogSource.contains(".fillMaxWidth()") &&
                dialogSource.contains("ButtonDefaults.buttonElevation("),
        )
    }
}
