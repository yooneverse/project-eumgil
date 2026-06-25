package com.ssafy.e102.eumgil.feature.textsize

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp

@Composable
fun TextSizeSettingRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val activity = remember(context) { context.findComponentActivity() }
    val viewModelFactory =
        remember(appContainer) {
            TextSizeSettingViewModel.provideFactory(
                textSizePreferenceRepository = appContainer.textSizePreferenceRepository,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "TextSizeSettingRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[TextSizeSettingViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                TextSizeSettingUiEvent.NavigateBack -> onNavigateBack()
            }
        }
    }

    TextSizeSettingScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
