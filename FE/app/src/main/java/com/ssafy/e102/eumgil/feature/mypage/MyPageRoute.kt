package com.ssafy.e102.eumgil.feature.mypage

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.config.AppEnvironment
import com.ssafy.e102.eumgil.core.external.createDuribalDialIntent as createDuribalDialIntentCore
import com.ssafy.e102.eumgil.data.repository.provideAccountWithdrawalRepository
import kotlinx.coroutines.launch

@Composable
fun MyPageRoute(
    onNavigateToUserTypePrimary: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToGuide: () -> Unit,
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
        remember(appContainer, accountWithdrawalRepository) {
            MyPageViewModel.provideFactory(
                settingsRepository = appContainer.settingsRepository,
                authSessionRepository = appContainer.authSessionRepository,
                authLogoutRepository = appContainer.authLogoutRepository,
                userProfileRepository = appContainer.userProfileRepository,
                bookmarkRepository = appContainer.bookmarkRepository,
                routeBookmarkRepository = appContainer.routeBookmarkRepository,
                reportRepository = appContainer.reportRepository,
                searchRepository = appContainer.searchRepository,
                accountWithdrawalRepository = accountWithdrawalRepository,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "MyPageRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[MyPageViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val preparingMessage = stringResource(id = R.string.my_page_preparing_message)
    val coroutineScope = rememberCoroutineScope()
    var isDuribalConfirmDialogVisible by rememberSaveable { mutableStateOf(false) }
    var isWithdrawConfirmDialogVisible by rememberSaveable { mutableStateOf(false) }

    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(viewModel, snackbarHostState, preparingMessage) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                MyPageUiEvent.NavigateToUserTypePrimary -> onNavigateToUserTypePrimary()
                MyPageUiEvent.NavigateToLogin -> onNavigateToLogin()
                MyPageUiEvent.NavigateToGuide -> onNavigateToGuide()
                MyPageUiEvent.NavigateToTextSizeSetting ->
                    onNavigateToTextSizeSetting?.invoke() ?: showSnackbar(preparingMessage)
                MyPageUiEvent.OpenPrivacyPolicy -> context.startActivity(createPrivacyPolicyIntent())
                MyPageUiEvent.OpenServiceTerms -> context.startActivity(createServiceTermsIntent())
                MyPageUiEvent.ShowPreparingMessage -> showSnackbar(preparingMessage)
                MyPageUiEvent.ShowProfileSyncFailedMessage ->
                    showSnackbar(
                        context.getString(R.string.my_page_profile_sync_failed),
                    )
                is MyPageUiEvent.ShowSnackbar -> showSnackbar(event.message)
            }
        }
    }

    MyPageScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        isDuribalConfirmDialogVisible = isDuribalConfirmDialogVisible,
        isWithdrawConfirmDialogVisible = isWithdrawConfirmDialogVisible,
        onDuribalCallClick = { isDuribalConfirmDialogVisible = true },
        onDuribalConfirmDismiss = { isDuribalConfirmDialogVisible = false },
        onDuribalConfirm = {
            isDuribalConfirmDialogVisible = false
            context.startActivity(createDuribalDialIntentCore())
        },
        onWithdrawClick = { isWithdrawConfirmDialogVisible = true },
        onWithdrawConfirmDismiss = { isWithdrawConfirmDialogVisible = false },
        onWithdrawConfirm = {
            isWithdrawConfirmDialogVisible = false
            viewModel.onAction(MyPageUiAction.WithdrawClicked)
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

internal fun createDuribalDialIntent() = createDuribalDialIntentCore()

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
