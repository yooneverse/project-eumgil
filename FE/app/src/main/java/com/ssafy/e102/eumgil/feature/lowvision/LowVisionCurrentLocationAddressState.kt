package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ssafy.e102.eumgil.core.location.CurrentLocationAddressResolver
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.GeoCoordinate

@Composable
internal fun rememberLowVisionCurrentLocationAddress(
    coordinate: GeoCoordinate?,
    addressResolver: CurrentLocationAddressResolver,
): String? {
    var address by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(coordinate, addressResolver) {
        address = null
        if (coordinate != null) {
            address = addressResolver.resolveAddress(coordinate)
        }
    }

    return address
}

internal fun LocationSnapshot?.toLowVisionCurrentLocationCoordinate(): GeoCoordinate? =
    this?.let { snapshot ->
        GeoCoordinate(
            latitude = snapshot.latitude,
            longitude = snapshot.longitude,
        )
    }
