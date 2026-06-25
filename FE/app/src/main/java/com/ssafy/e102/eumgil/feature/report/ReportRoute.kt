package com.ssafy.e102.eumgil.feature.report

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.e102.eumgil.BuildConfig
import com.ssafy.e102.eumgil.app.BusanEumgilApp
import com.ssafy.e102.eumgil.core.location.AndroidCurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.CompositeCurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.KakaoLocalCurrentLocationAddressResolver
import kotlinx.coroutines.flow.collect

private const val REPORT_PHOTO_PICKER_LOG_TAG = "ReportPhotoPicker"

@Composable
fun ReportRoute(
    onNavigateBack: () -> Unit,
    onNavigateToReportHistory: (String?) -> Unit,
    onNavigateToMap: () -> Unit,
    onReturnToNavigationWithSubmittedReport: (Long) -> Unit = {},
    entryPoint: ReportEntryPoint = ReportEntryPoint.TopLevel,
    startNewRequest: Boolean = false,
    onStartNewRequestConsumed: () -> Unit = {},
    initialReportType: ReportType? = null,
    onInitialReportTypeConsumed: () -> Unit = {},
    initialDescription: String? = null,
    onInitialDescriptionConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val appContainer =
        remember(appContext) {
            (appContext as BusanEumgilApp).appContainer
        }
    val activity = remember(context) { context.findComponentActivity() }
    // Reverse geocoder — primary는 정밀도가 높은 카카오 로컬 REST API, fallback은 Android
    // Geocoder. NATIVE App Key가 REST API에 권한이 없거나 네트워크 실패 시에도 적어도
    // Geocoder 결과로 카드 주소가 채워지도록 안전망을 둔다.
    val addressResolver =
        remember(appContext) {
            CompositeCurrentLocationAddressResolver(
                primary = KakaoLocalCurrentLocationAddressResolver(BuildConfig.KAKAO_NATIVE_APP_KEY),
                fallback = AndroidCurrentLocationAddressResolver(appContext),
            )
        }
    val viewModelFactory =
        remember(appContainer, addressResolver) {
            ReportViewModel.provideFactory(
                reportRepository = appContainer.reportRepository,
                currentLocationManager = appContainer.currentLocationManager,
                locationPermissionManager = appContainer.locationPermissionManager,
                addressResolver = addressResolver,
                // Task 4.1 — 오프라인 시 제출 버튼을 자동 비활성화하기 위해 네트워크 모니터 주입.
                networkMonitor = appContainer.networkMonitor,
            )
        }
    val viewModel =
        remember(activity, viewModelFactory) {
            val owner = checkNotNull(activity) { "ReportRoute requires a ComponentActivity host." }
            ViewModelProvider(owner, viewModelFactory)[ReportViewModel::class.java]
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ─── Photo Picker (Task 3.2) ──────────────────────────────────────────
    // PickMultipleVisualMedia를 ReportFormLimits.PHOTO_MAX_COUNT 만큼 받도록 등록.
    // 사용자가 이미 첨부한 사진 수까지 합쳐 cap을 초과할 가능성이 있으므로 결과 콜백에서 한 번 더 잘라낸다.
    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.PickMultipleVisualMedia(
                    maxItems = ReportFormLimits.PHOTO_MAX_COUNT,
                ),
        ) { selectedUris: List<Uri> ->
            if (selectedUris.isEmpty()) return@rememberLauncherForActivityResult
            handlePhotoPickerResult(
                selectedUris = selectedUris,
                currentCount = uiState.photo.values.size,
                contentResolver = context.contentResolver,
                onPhotoSelected = { photo -> viewModel.onAction(ReportUiAction.PhotoSelected(photo)) },
            )
        }

    LaunchedEffect(entryPoint, viewModel) {
        if (!startNewRequest) {
            viewModel.onAction(
                ReportUiAction.RouteEntered(
                    entryPoint = entryPoint,
                    startNew = false,
                ),
            )
        }
    }

    LaunchedEffect(entryPoint, startNewRequest, viewModel) {
        if (startNewRequest) {
            viewModel.onAction(
                ReportUiAction.RouteEntered(
                    entryPoint = entryPoint,
                    startNew = true,
                ),
            )
            onStartNewRequestConsumed()
        }
    }

    // 음성 에이전트 진입 시 reportType 자동 선택 → TypeSelection 단계 스킵
    LaunchedEffect(initialReportType, viewModel) {
        if (initialReportType != null) {
            viewModel.onAction(ReportUiAction.ReportTypeSelected(initialReportType))
            onInitialReportTypeConsumed()
        }
    }

    // 음성 에이전트 진입 시 description 자동 채움 → DetailInput 도달 시 미리 노출 (스텝 무변)
    LaunchedEffect(initialDescription, viewModel) {
        if (initialDescription != null) {
            viewModel.onAction(ReportUiAction.DescriptionChanged(initialDescription))
            onInitialDescriptionConsumed()
        }
    }

    // 권한 다이얼로그가 dismiss되면 Activity가 ON_RESUME으로 돌아오는 경우가 많다.
    // ViewModel이 pending 중인 위치 요청을 가지고 있다면 새 권한 상태로 흐름을 종료시키기 위해
    // ON_RESUME마다 RefreshLocationPermission을 한 번씩 dispatch한다 (idempotent — pending 없으면 no-op).
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onAction(ReportUiAction.RefreshLocationPermission)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel, onNavigateBack, onNavigateToReportHistory, onNavigateToMap, onReturnToNavigationWithSubmittedReport) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                ReportUiEvent.NavigateBack -> onNavigateBack()
                is ReportUiEvent.ReturnToNavigationWithSubmittedReport ->
                    onReturnToNavigationWithSubmittedReport(event.reportId)
                is ReportUiEvent.NavigateToReportHistory -> onNavigateToReportHistory(event.historyId)
                ReportUiEvent.NavigateToMap -> onNavigateToMap()
                is ReportUiEvent.AnnounceForAccessibility -> {
                    // View.announceForAccessibility는 API 33+에서 deprecated이지만
                    // 모든 API 레벨에서 동작하며 Compose에는 1회성 announcement 공식 API가 없어 사용.
                    @Suppress("DEPRECATION")
                    view.announceForAccessibility(event.message)
                }
                ReportUiEvent.ScrollToFirstError -> scrollState.animateScrollTo(0)
                ReportUiEvent.RequestLocationPermission -> {
                    // Activity가 살아있어야 launcher 사용 가능. Manager가 이미 Granted/Unavailable
                    // 상태이면 자체적으로 no-op으로 처리하므로 안전.
                    activity?.let(appContainer.locationPermissionManager::requestLocationPermission)
                }
                ReportUiEvent.OpenPhotoPicker -> {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            }
        }
    }

    ReportScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        scrollState = scrollState,
        modifier = modifier,
    )
}

/**
 * Photo Picker 결과 처리.
 *
 * 1. cap 초과분은 잘라냄 (남은 슬롯만큼만 받음).
 * 2. 각 URI에 takePersistableUriPermission 시도 (SecurityException은 무시 — Photo Picker URI는
 *    영구 권한 미지원이지만 일부 케이스에서 동작 가능).
 * 3. ContentResolver로 MIME / size 조회 후 ReportPhoto 생성, PhotoSelected dispatch.
 */
private fun handlePhotoPickerResult(
    selectedUris: List<Uri>,
    currentCount: Int,
    contentResolver: ContentResolver,
    onPhotoSelected: (ReportPhoto) -> Unit,
) {
    val remainingSlots = (ReportFormLimits.PHOTO_MAX_COUNT - currentCount).coerceAtLeast(0)
    if (remainingSlots <= 0) return

    val acceptedUris = selectedUris.take(remainingSlots)

    acceptedUris.forEach { uri ->
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            // Photo Picker URI는 영구 권한 grant 시 SecurityException 발생할 수 있음.
            // process 살아있는 동안은 URI 권한 유효하므로 시연·동작상 문제 없음.
            Log.d(REPORT_PHOTO_PICKER_LOG_TAG, "Persistable URI permission skipped: $error")
        }

        val mimeType = contentResolver.getType(uri)
        val sizeBytes =
            runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull()

        onPhotoSelected(
            ReportPhoto(
                localUri = uri.toString(),
                mimeType = mimeType,
                sizeBytes = sizeBytes,
            ),
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
