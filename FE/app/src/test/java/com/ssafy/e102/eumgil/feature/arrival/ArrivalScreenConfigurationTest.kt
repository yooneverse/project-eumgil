package com.ssafy.e102.eumgil.feature.arrival

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalScreenConfigurationTest {
    @Test
    fun `arrival completion content uses dedicated background band asset`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival completion content should use a dedicated arrival illustration asset instead of reusing the splash background.",
            source.contains("R.drawable.arrival_completion_background"),
        )
        assertFalse(
            "Arrival completion content should no longer reuse the splash illustration drawable because it is also used by the app launch surfaces.",
            source.contains("R.drawable.splash_illustration"),
        )
    }

    @Test
    fun `arrival completion headline keeps the approved centered line break`() {
        val stringsSource =
            File("src/main/res/values/strings.xml").readText()

        assertTrue(
            "Arrival headline should keep the centered two-line copy requested for the completion screen.",
            stringsSource.contains("<string name=\"arrival_screen_headline\">오늘도 안전한 이동\\n수고하셨습니다!</string>"),
        )
    }

    @Test
    fun `arrival completion content promotes the illustration to a top background band`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival completion content should define a dedicated hero-band height so the illustration reads like a background section instead of a card.",
            source.contains("private val ArrivalHeroBandHeight = 332.dp"),
        )
        assertTrue(
            "Arrival completion content should scale the illustration by width so the full skyline remains visible inside the hero band.",
            source.contains("contentScale = ContentScale.FillWidth"),
        )
        assertTrue(
            "Arrival completion content should keep the artwork box attached to the bottom edge of the hero band so the lower skyline still reads as a background section.",
            source.contains(".align(Alignment.BottomCenter)"),
        )
        assertTrue(
            "Arrival completion content should preserve the original artwork ratio instead of forcing a cropped fixed-height box.",
            source.contains("private const val ArrivalHeroArtworkAspectRatio = 1440f / 900f"),
        )
        assertTrue(
            "Arrival completion content should size the artwork from its aspect ratio so the top and bottom of the illustration are both kept intact.",
            source.contains(".aspectRatio(ArrivalHeroArtworkAspectRatio)"),
        )
        assertTrue(
            "Arrival completion content should keep the taller hero band offset from the top edge to leave room for the brand lockup.",
            source.contains("private val ArrivalHeroBandTopSpacing = 36.dp"),
        )
        assertTrue(
            "Arrival completion content should lower the artwork slightly inside the hero band so the skyline reads further down the page.",
            source.contains("private val ArrivalHeroArtworkBottomSpacing = 28.dp"),
        )
        assertTrue(
            "Arrival completion content should push the hero band slightly lower from the top edge to match the updated composition.",
            source.contains("Spacer(modifier = Modifier.height(ArrivalHeroBandTopSpacing))"),
        )
        assertFalse(
            "Arrival completion content should no longer force the artwork into a fixed height that clips the skyline.",
            source.contains("private val ArrivalHeroArtworkHeight = 156.dp"),
        )
        assertFalse(
            "Arrival completion content should not keep the old root padding that inset the hero band on both sides.",
            source.contains(".padding(horizontal = EumSpacing.large, vertical = EumSpacing.medium)"),
        )
    }

    @Test
    fun `arrival evaluation sheet uses edge to edge bottom sheet placement`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertFalse(
            "Arrival evaluation sheet should not cap the sheet width when it needs to span the full bottom edge.",
            source.contains("Modifier.widthIn(max = 520.dp)"),
        )
        assertFalse(
            "Arrival evaluation sheet should not keep the old bottom-sheet wrapper that added navigation-bar and bottom spacing outside the sheet container.",
            source.contains(
                ".navigationBarsPadding()\n                    .padding(horizontal = EumSpacing.medium)\n                    .padding(bottom = EumSpacing.medium)",
            ),
        )
    }

    @Test
    fun `arrival evaluation sheet supports drag dismiss and yellow selected stars`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival evaluation sheet should expose a draggable handle so users can pull the sheet down to dismiss it.",
            source.contains("handleModifier ="),
        )
        assertTrue(
            "Arrival evaluation sheet should wire vertical dragging into the handle dismiss interaction.",
            source.contains(".draggable("),
        )
        assertTrue(
            "Selected arrival rating stars should use the yellow rating tint requested for the evaluation flow.",
            source.contains("private val ArrivalRatingSelectedColor = Color(0xFFFACC15)"),
        )
        assertTrue(
            "Arrival evaluation stars should grow to a larger touch target and icon size so the rating action reads more prominently in the sheet.",
            source.contains("private val ArrivalRatingButtonSize = 64.dp") &&
                source.contains("private val ArrivalRatingIconSize = 56.dp") &&
                source.contains("modifier = Modifier.size(ArrivalRatingButtonSize)") &&
                source.contains("modifier = Modifier.size(ArrivalRatingIconSize)"),
        )
    }

    @Test
    fun `arrival completion keeps home actions inside completion content instead of lifting them to the root layer`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival completion content should keep the home actions wired inside the completion content so ARR-01 remains the owner of the post-arrival CTA area.",
            source.contains("ArrivalCompletionContent(\n            onHomeClicked = { onAction(ArrivalUiAction.HomeClicked) },"),
        )
        assertFalse(
            "Arrival screen should not promote the home actions to a root-level overlay as a workaround for the bottom-sheet interaction bug.",
            source.contains("if (!uiState.isEvaluationSheetVisible) {\n            ArrivalCompletionActions("),
        )
    }

    @Test
    fun `arrival completion actions keep ctas above the system navigation bar`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival completion actions should apply navigation-bar padding so the home and new-route buttons stay above three-button system navigation.",
            source.contains(
                "modifier\n                .fillMaxWidth()\n                .navigationBarsPadding()\n                .padding(horizontal = EumSpacing.large)\n                .padding(bottom = EumSpacing.medium)",
            ),
        )
    }

    @Test
    fun `arrival evaluation sheet dismisses by animating its real offset instead of relying on parent slide out placement`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival evaluation sheet should animate the actual sheet offset so the uncovered completion CTA becomes tappable as soon as the sheet moves away.",
            source.contains("animateFloatAsState("),
        )
        assertTrue(
            "Arrival evaluation sheet should finish the dismiss flow only after the offset animation reaches the bottom edge.",
            source.contains("finishedListener = { offsetPx ->"),
        )
        assertTrue(
            "Arrival evaluation sheet should tell the ViewModel that the sheet is dismissed as soon as the dismiss gesture is accepted, so the uncovered completion CTA becomes immediately actionable.",
            source.contains("onAction(ArrivalUiAction.EvaluationSheetDismissed)"),
        )
        assertFalse(
            "Arrival evaluation sheet should not depend on AnimatedVisibility slide-out placement for dismissal because that leaves the original hit area blocking the completion CTA.",
            source.contains("exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }) + fadeOut()"),
        )
    }

    @Test
    fun `arrival evaluation sheet removes rating copy and enlarges stars to keep sheet height`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival evaluation sheet should enlarge the star touch targets after removing the extra copy so the sheet height stays visually stable.",
            source.contains("private val ArrivalRatingButtonSize = 64.dp"),
        )
        assertTrue(
            "Arrival evaluation sheet should almost double the visible star size after removing the question and rating label copy.",
            source.contains("private val ArrivalRatingIconSize = 56.dp"),
        )
        assertTrue(
            "Arrival evaluation sheet should give the larger star row vertical breathing room so the overall sheet height remains close to the previous layout.",
            source.contains(".padding(vertical = ArrivalRatingRowVerticalPadding)"),
        )
        assertFalse(
            "Arrival evaluation sheet should remove the question copy from the route-save sheet content.",
            source.contains("text = stringResource(id = R.string.arrival_evaluation_question)"),
        )
        assertFalse(
            "Arrival evaluation sheet should remove the satisfaction label text that used to mirror the selected star rating.",
            source.contains("uiState.selectedRatingLabel.labelResId?.let"),
        )
        assertFalse(
            "Arrival screen should no longer render the route save dialog flow after the save action becomes immediate.",
            source.contains("if (uiState.isRouteSaveDialogVisible)"),
        )
        assertFalse(
            "Arrival screen should no longer keep the route save dialog composable in this evaluation flow.",
            source.contains("private fun ArrivalRouteSaveDialog("),
        )
    }

    @Test
    fun `arrival evaluation sheet removes the extra prompt and rating feedback labels`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertFalse(
            "Arrival evaluation sheet should remove the question prompt once the star row itself becomes the main feedback affordance.",
            source.contains("text = stringResource(id = R.string.arrival_evaluation_question)"),
        )
        assertFalse(
            "Arrival evaluation sheet should remove the per-rating text feedback below the stars.",
            source.contains("uiState.selectedRatingLabel.labelResId?.let"),
        )
        assertFalse(
            "Arrival evaluation sheet should no longer reserve placeholder spacing for rating feedback text that is no longer shown.",
            source.contains("ArrivalEvaluationRatingFeedbackPlaceholderHeight"),
        )
    }

    @Test
    fun `arrival evaluation route save action becomes a text only toggle button`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertFalse(
            "Arrival route save CTA should remove the bookmark icon so the control reads as a simpler text button.",
            source.contains("ArrivalRouteSaveIconSize"),
        )
        assertFalse(
            "Arrival route save CTA should no longer render the outlined bookmark icon in the unsaved state.",
            source.contains("R.drawable.ic_nav_bookmark_outline"),
        )
        assertFalse(
            "Arrival route save CTA should no longer render the filled bookmark icon in the saved state.",
            source.contains("R.drawable.ic_nav_bookmark_selected"),
        )
        assertFalse(
            "Arrival route save CTA should not show the old save-cancel copy once the button itself indicates the saved state.",
            source.contains("R.string.arrival_evaluation_route_save_cancel"),
        )
        assertTrue(
            "Arrival route save CTA should switch between an outlined white surface and a filled blue saved state.",
            source.contains("val routeSaveContainerColor =") &&
                source.contains("val routeSaveContentColor =") &&
                source.contains("R.string.arrival_evaluation_route_saved") &&
                source.contains("R.string.arrival_evaluation_save_route"),
        )
    }

    @Test
    fun `arrival evaluation action buttons keep the same label typography`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival evaluation submit CTA should use the same labelLarge typography as the route save CTA so the two actions read as a matched button group.",
            source.contains(
                "text = stringResource(id = R.string.arrival_evaluation_submit),\n                                style = MaterialTheme.typography.labelLarge,",
            ),
        )
    }

    @Test
    fun `arrival evaluation close button uses a softer gray tint`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertTrue(
            "Arrival evaluation close button should use a dedicated toned-down gray tint instead of the stronger surface variant color.",
            source.contains("private val ArrivalCloseButtonTint =") &&
                source.contains("tint = ArrivalCloseButtonTint"),
        )
    }

    @Test
    fun `arrival completion actions center icon and text as a single group`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()

        assertFalse(
            "Arrival completion CTA labels should not use weight-based centering because the icon and text must stay visually centered as one group.",
            source.contains("modifier = Modifier.weight(1f, fill = false)"),
        )
        assertFalse(
            "Arrival completion CTA buttons should not add a fake trailing spacer because the icon and label group now centers together.",
            source.contains("Spacer(modifier = Modifier.width(24.dp))"),
        )
    }

    @Test
    fun `arrival route save action uses text only button states`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()
        val stringsSource =
            File("src/main/res/values/strings.xml").readText()

        assertFalse(
            "Arrival route save CTA should no longer show a bookmark icon once the button becomes a pure text state control.",
            source.contains("R.drawable.ic_nav_bookmark_selected"),
        )
        assertFalse(
            "Arrival route save CTA should not keep the outline bookmark icon either once the unsaved state becomes text-only.",
            source.contains("R.drawable.ic_nav_bookmark_outline"),
        )
        assertFalse(
            "Arrival route save CTA should drop the now-unused route-save icon size constant.",
            source.contains("private val ArrivalRouteSaveIconSize ="),
        )
        assertTrue(
            "Arrival route save CTA should switch to the saved-state label instead of exposing a cancel label.",
            source.contains("R.string.arrival_evaluation_route_saved"),
        )
        assertTrue(
            "Arrival route save CTA should keep the unsaved-state label on the outlined white button.",
            stringsSource.contains("<string name=\"arrival_evaluation_save_route\">경로 저장하기</string>"),
        )
        assertTrue(
            "Arrival route save CTA should show an action label that makes the selected toggle state clear.",
            stringsSource.contains("<string name=\"arrival_evaluation_route_saved\">저장 해제하기</string>"),
        )
    }

    @Test
    fun `arrival completion flow removes snackbar feedback entirely`() {
        val screenSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalScreen.kt").readText()
        val routeSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/arrival/ArrivalRoute.kt").readText()

        assertFalse(
            "ArrivalScreen should not render a SnackbarHost once completion feedback is removed from this flow.",
            screenSource.contains("SnackbarHost("),
        )
        assertFalse(
            "ArrivalScreen should not require a SnackbarHostState parameter once snackbar feedback is removed.",
            screenSource.contains("snackbarHostState: SnackbarHostState"),
        )
        assertFalse(
            "ArrivalRoute should not keep a SnackbarHostState after removing snackbar feedback from arrival completion.",
            routeSource.contains("SnackbarHostState"),
        )
        assertFalse(
            "ArrivalRoute should not call showSnackbar after removing snackbar feedback from arrival completion.",
            routeSource.contains("showSnackbar("),
        )
    }

    @Test
    fun `arrival explore new route navigation removes completion screen from back stack`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/app/navigation/MainNavGraph.kt").readText()

        assertTrue(
            "Exploring a new route from the arrival screen should navigate to search.",
            source.contains("navController.navigate(SearchRoute.Entry.createRoute())"),
        )
        assertTrue(
            "Exploring a new route from the arrival screen should remove the completion screen from the back stack so back does not reopen the evaluation flow.",
            source.contains("popUpTo(ArrivalRoute.Entry.route) {\n                        inclusive = true"),
        )
    }

    @Test
    fun `arrival evaluation route save strings match the simplified toggle labels`() {
        val stringsSource =
            File("src/main/res/values/strings.xml").readText()

        assertTrue(
            "Arrival evaluation unsaved route save CTA should use the short route-save label requested for the bottom sheet.",
            stringsSource.contains("<string name=\"arrival_evaluation_save_route\">경로 저장하기</string>"),
        )
        assertTrue(
            "Arrival evaluation saved route save CTA should use a toggle action label instead of a completion message.",
            stringsSource.contains("<string name=\"arrival_evaluation_route_saved\">저장 해제하기</string>"),
        )
    }
}
