package com.ssafy.e102.eumgil.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.ssafy.e102.eumgil.core.location.locationPermissions

/**
 * 온보딩 완료 직전 위치 권한을 일괄 요청하는 Route.
 *
 * 별도 UI 없이 화면 진입 즉시 ACCESS_FINE_LOCATION · ACCESS_COARSE_LOCATION
 * 두 권한을 요청한다. 마이크 권한은 음성 검색을 실제로 사용할 때 요청한다.
 * 허용/거부 결과에 관계없이 [onPermissionHandled]를 호출해 다음 화면으로 진행한다.
 */
@Composable
fun PermissionRoute(onPermissionHandled: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onPermissionHandled()
    }

    LaunchedEffect(launcher) {
        launcher.launch(locationPermissions)
    }
}
