package com.ssafy.e102.eumgil.feature.lowvision

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.data.repository.canSaveServerBookmark
import com.ssafy.e102.eumgil.data.repository.toBookmarkDataOrNull
import kotlinx.coroutines.launch

@Composable
fun LowVisionNavigationCompleteRoute(
    onNavigateToBookmark: () -> Unit,
    onCompleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appContainer =
        remember(appContext) {
            (appContext as BusanEumgilApp).appContainer
        }
    val selectedDestination by
        appContainer.destinationSelectionRepository.selectedDestination.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    LowVisionFontTheme {
        LowVisionNavigationCompleteScreen(
            isSaveEnabled = selectedDestination?.canSaveServerBookmark() == true,
            onSaveClick = {
                val destination = selectedDestination ?: return@LowVisionNavigationCompleteScreen
                coroutineScope.launch {
                    val bookmark = destination.toBookmarkDataOrNull()
                    if (bookmark == null) {
                        Toast
                            .makeText(
                                context,
                                LOW_VISION_BOOKMARK_UNAVAILABLE_MESSAGE,
                                Toast.LENGTH_SHORT,
                            ).show()
                        return@launch
                    }
                    runCatching {
                        appContainer.bookmarkRepository.saveBookmark(bookmark)
                    }.onSuccess {
                        println(
                            "BookmarkSaveTrace[LowVisionNavigationCompleteRoute] result=success placeId=${bookmark.placeId}",
                        )
                        onNavigateToBookmark()
                    }.onFailure { throwable ->
                        println(
                            "BookmarkSaveTrace[LowVisionNavigationCompleteRoute] result=failure placeId=${bookmark.placeId} message=${throwable.message.orEmpty()}",
                        )
                        Toast
                            .makeText(
                                context,
                                LOW_VISION_BOOKMARK_SAVE_FAILURE_MESSAGE,
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            },
            onCompleteClick = onCompleteClick,
            modifier = modifier,
        )
    }
}

private const val LOW_VISION_BOOKMARK_UNAVAILABLE_MESSAGE = "서버에 저장할 수 있는 목적지에서만 북마크를 저장할 수 있습니다."
private const val LOW_VISION_BOOKMARK_SAVE_FAILURE_MESSAGE = "북마크를 저장하지 못했습니다. 다시 시도해 주세요."
