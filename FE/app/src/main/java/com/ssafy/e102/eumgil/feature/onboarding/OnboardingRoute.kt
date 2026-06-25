package com.ssafy.e102.eumgil.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun PrimaryUserTypeRoute(
    onTypeSelected: (PrimaryUserType) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryUserTypeScreen(
        onTypeSelected = onTypeSelected,
        modifier = modifier,
    )
}

@Composable
fun LowVisionFollowUpRoute(
    onNavigateNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LowVisionFollowUpScreen(
        onNextClick = onNavigateNext,
        modifier = modifier,
    )
}

@Composable
fun MobilityTypeSecondaryRoute(
    onNavigateNext: (MobilitySubtype) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMobilitySubtypeRoute by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    MobilitySubtypeScreen(
        uiState =
            MobilitySubtypeUiState(
                selectedMobilitySubtype = MobilitySubtype.fromRouteValue(selectedMobilitySubtypeRoute),
            ),
        onSubtypeClick = { mobilitySubtype ->
            selectedMobilitySubtypeRoute = mobilitySubtype.routeValue
            onNavigateNext(mobilitySubtype)
        },
        modifier = modifier,
    )
}

@Composable
fun MobilityTypeSecondaryTermsRoute(
    onConsentCompleted: (MobilitySubtype, LocationTermsAgreement) -> Unit,
    onRequestDetails: (LocationTermsItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedMobilitySubtypeRoute by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var isTermsSheetVisible by rememberSaveable {
        mutableStateOf(false)
    }
    var isServiceTermsChecked by rememberSaveable { mutableStateOf(false) }
    var isSensitiveInfoTermsChecked by rememberSaveable { mutableStateOf(false) }
    var isPersonalLocationInfoTermsChecked by rememberSaveable { mutableStateOf(false) }
    var isOverFourteenChecked by rememberSaveable { mutableStateOf(false) }

    val selectedMobilitySubtype = MobilitySubtype.fromRouteValue(selectedMobilitySubtypeRoute)
    val termsUiState =
        LocationTermsUiState(
            isServiceTermsChecked = isServiceTermsChecked,
            isSensitiveInfoTermsChecked = isSensitiveInfoTermsChecked,
            isPersonalLocationInfoTermsChecked = isPersonalLocationInfoTermsChecked,
            isOverFourteenChecked = isOverFourteenChecked,
        )

    MobilitySubtypeScreen(
        uiState =
            MobilitySubtypeUiState(
                selectedMobilitySubtype = selectedMobilitySubtype,
            ),
        onSubtypeClick = { mobilitySubtype ->
            selectedMobilitySubtypeRoute = mobilitySubtype.routeValue
            isTermsSheetVisible = true
        },
        modifier = modifier,
    )

    if (isTermsSheetVisible && selectedMobilitySubtype != null) {
        LocationTermsBottomSheet(
            uiState = termsUiState,
            onDismissRequest = { isTermsSheetVisible = false },
            onAllTermsCheckedChange = { shouldCheckAll ->
                isServiceTermsChecked = shouldCheckAll
                isSensitiveInfoTermsChecked = shouldCheckAll
                isPersonalLocationInfoTermsChecked = shouldCheckAll
                isOverFourteenChecked = shouldCheckAll
            },
            onServiceTermsCheckedChange = { isServiceTermsChecked = it },
            onSensitiveInfoTermsCheckedChange = { isSensitiveInfoTermsChecked = it },
            onPersonalLocationInfoTermsCheckedChange = { isPersonalLocationInfoTermsChecked = it },
            onOverFourteenCheckedChange = { isOverFourteenChecked = it },
            onPrimaryActionClick = {
                if (termsUiState.canProceed) {
                    onConsentCompleted(selectedMobilitySubtype, termsUiState.toAgreement())
                }
            },
            onRequestDetails = onRequestDetails,
        )
    }
}

@Composable
fun LocationTermsRoute(
    initialLocationTermsChecked: Boolean = false,
    onConsentCompleted: (LocationTermsAgreement) -> Unit,
    onRequestDetails: (LocationTermsItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isServiceTermsChecked by rememberSaveable(initialLocationTermsChecked) {
        mutableStateOf(initialLocationTermsChecked)
    }
    var isSensitiveInfoTermsChecked by rememberSaveable(initialLocationTermsChecked) {
        mutableStateOf(initialLocationTermsChecked)
    }
    var isPersonalLocationInfoTermsChecked by rememberSaveable(initialLocationTermsChecked) {
        mutableStateOf(initialLocationTermsChecked)
    }
    var isOverFourteenChecked by rememberSaveable(initialLocationTermsChecked) {
        mutableStateOf(initialLocationTermsChecked)
    }
    var hasRestrictionNotice by rememberSaveable { mutableStateOf(false) }

    val uiState =
        LocationTermsUiState(
            isServiceTermsChecked = isServiceTermsChecked,
            isSensitiveInfoTermsChecked = isSensitiveInfoTermsChecked,
            isPersonalLocationInfoTermsChecked = isPersonalLocationInfoTermsChecked,
            isOverFourteenChecked = isOverFourteenChecked,
            hasRestrictionNotice = hasRestrictionNotice,
        )

    LocationTermsScreen(
        uiState = uiState,
        onAllTermsCheckedChange = { shouldCheckAll ->
            isServiceTermsChecked = shouldCheckAll
            isSensitiveInfoTermsChecked = shouldCheckAll
            isPersonalLocationInfoTermsChecked = shouldCheckAll
            isOverFourteenChecked = shouldCheckAll
            hasRestrictionNotice = false
        },
        onServiceTermsCheckedChange = { isChecked ->
            isServiceTermsChecked = isChecked
            hasRestrictionNotice = false
        },
        onSensitiveInfoTermsCheckedChange = { isChecked ->
            isSensitiveInfoTermsChecked = isChecked
            hasRestrictionNotice = false
        },
        onPersonalLocationInfoTermsCheckedChange = { isChecked ->
            isPersonalLocationInfoTermsChecked = isChecked
            hasRestrictionNotice = false
        },
        onOverFourteenCheckedChange = { isChecked ->
            isOverFourteenChecked = isChecked
            hasRestrictionNotice = false
        },
        onPrimaryActionClick = {
            if (uiState.canProceed) {
                onConsentCompleted(uiState.toAgreement())
            }
        },
        onRequestDetails = onRequestDetails,
        modifier = modifier,
    )
}
