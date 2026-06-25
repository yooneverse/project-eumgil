package com.ssafy.e102.eumgil.feature.lowvision

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.config.AppEnvironment
import com.ssafy.e102.eumgil.data.repository.provideAccountWithdrawalRepository
import kotlinx.coroutines.launch

@Composable
fun LowVisionMyPageRoute(
    onModeChangeClick: () -> Unit,
    onAppInfoClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onTabSelected: (LowVisionBottomTab) -> Unit,
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
            LowVisionMyPageViewModel.provideFactory(
                authLogoutRepository = appContainer.authLogoutRepository,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "LowVisionMyPageRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[LowVisionMyPageViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                LowVisionMyPageUiEvent.NavigateToLogin -> onLogoutClick()
                is LowVisionMyPageUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LowVisionFontTheme {
        LowVisionMyPageScreen(
            isLogoutLoading = uiState.isLogoutLoading,
            snackbarHostState = snackbarHostState,
            onModeChangeClick = onModeChangeClick,
            onAppInfoClick = onAppInfoClick,
            onLogoutClick = viewModel::onLogoutClick,
            onTabSelected = onTabSelected,
            modifier = modifier,
        )
    }
}

@Composable
fun LowVisionAppInfoRoute(
    onTabSelected: (LowVisionBottomTab) -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToTextSizeSetting: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContainer =
        remember(context.applicationContext) {
            (context.applicationContext as BusanEumgilApp).appContainer
        }
    val activity = remember(context) { context.findComponentActivity() }
    val accountWithdrawalRepository =
        remember(appContainer) {
            provideAccountWithdrawalRepository(
                baseUrl = AppEnvironment.baseUrl,
                authSessionRepository = appContainer.authSessionRepository,
                initSettingsRepository = appContainer.settingsRepository,
                bookmarkDao = appContainer.localDatabase.bookmarkDao(),
                favoriteRouteDao = appContainer.localDatabase.favoriteRouteDao(),
                reportOutboxDao = appContainer.localDatabase.reportOutboxDao(),
                isMockMode = AppEnvironment.isMockMode,
            )
        }
    val viewModelFactory =
        remember(accountWithdrawalRepository) {
            LowVisionAppInfoViewModel.provideFactory(
                accountWithdrawalRepository = accountWithdrawalRepository,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "LowVisionAppInfoRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[LowVisionAppInfoViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val preparingMessage = stringResource(id = R.string.my_page_preparing_message)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel, snackbarHostState, onNavigateToLogin) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                LowVisionAppInfoUiEvent.NavigateToLogin -> onNavigateToLogin()
                is LowVisionAppInfoUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LowVisionFontTheme {
        LowVisionAppInfoScreen(
            isWithdrawLoading = uiState.isWithdrawLoading,
            snackbarHostState = snackbarHostState,
            onTextSizeClick = {
                onNavigateToTextSizeSetting?.invoke()
                    ?: coroutineScope.launch { snackbarHostState.showSnackbar(preparingMessage) }
            },
            onWithdrawClick = viewModel::onWithdrawClick,
            onTabSelected = onTabSelected,
            modifier = modifier,
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
