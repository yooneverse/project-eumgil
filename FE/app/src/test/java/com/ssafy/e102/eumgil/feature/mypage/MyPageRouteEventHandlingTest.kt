package com.ssafy.e102.eumgil.feature.mypage

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyPageRouteEventHandlingTest {
    @Test
    fun `my page route launches snackbar work without blocking later events`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/mypage/MyPageRoute.kt")
                .readText()

        assertTrue(
            "MyPageRoute should keep a coroutine scope for non-blocking snackbar work.",
            source.contains("val coroutineScope = rememberCoroutineScope()"),
        )
        assertTrue(
            "MyPageRoute should show snackbar messages from a launched child coroutine so the event collector can keep processing taps.",
            source.contains("coroutineScope.launch {"),
        )
        assertTrue(
            "MyPageRoute should replace any current snackbar before showing the next temporary notice.",
            source.contains("snackbarHostState.currentSnackbarData?.dismiss()"),
        )
        assertFalse(
            "MyPageRoute should not suspend the event collector directly on the preparing message snackbar call.",
            source.contains("MyPageUiEvent.ShowPreparingMessage -> snackbarHostState.showSnackbar(preparingMessage)"),
        )
    }
}
