package com.ssafy.e102.eumgil.feature.report

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import kotlinx.coroutines.flow.collect

@Composable
fun ReportHistoryRoute(
    initialHistoryId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToReport: () -> Unit,
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
            ReportHistoryViewModel.provideFactory(reportRepository = appContainer.reportRepository)
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "ReportHistoryRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[ReportHistoryViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialHistoryId, viewModel) {
        initialHistoryId
            ?.takeIf(String::isNotBlank)
            ?.let { historyId -> viewModel.onAction(ReportHistoryUiAction.ReportClicked(historyId)) }
    }

    LaunchedEffect(viewModel, onNavigateBack, onNavigateToReport, snackbarHostState) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                ReportHistoryUiEvent.NavigateBack -> onNavigateBack()
                ReportHistoryUiEvent.NavigateToReport -> onNavigateToReport()
                is ReportHistoryUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    ReportHistoryScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
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
