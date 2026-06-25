package com.ssafy.e102.eumgil.feature.navigation

internal enum class NavigationLiveGuidanceSpeechStage {
    INITIAL,
    APPROACH_30M,
    NEAR_10M,
}

internal fun toLiveGuidanceDisplayDistanceMeters(rawDistance: Int): Int =
    when {
        rawDistance <= LIVE_GUIDANCE_DISTANCE_HIDE_THRESHOLD_METERS -> 0
        rawDistance <= LIVE_GUIDANCE_DISTANCE_NEAR_BUCKET_LIMIT_METERS ->
            rawDistance.floorToLiveGuidanceBucket(LIVE_GUIDANCE_DISTANCE_NEAR_BUCKET_METERS)
        else ->
            rawDistance.floorToLiveGuidanceBucket(LIVE_GUIDANCE_DISTANCE_FAR_BUCKET_METERS)
    }

internal fun formatNavigationLiveGuidanceCardTextFromRawDistance(
    action: NavigationGuidanceAction,
    rawDistanceMeters: Int,
    fallbackTitle: String,
): String =
    formatNavigationLiveGuidanceCardText(
        action = action,
        displayDistanceMeters = toLiveGuidanceDisplayDistanceMeters(rawDistanceMeters),
        fallbackTitle = fallbackTitle,
    )

internal fun formatNavigationLiveGuidanceCardText(
    action: NavigationGuidanceAction,
    displayDistanceMeters: Int,
    fallbackTitle: String,
): String {
    if (action == NavigationGuidanceAction.START) return "출발"

    if (displayDistanceMeters <= 0) {
        return when (action) {
            NavigationGuidanceAction.CROSSWALK -> "횡단보도"
            NavigationGuidanceAction.TURN_LEFT -> "좌회전"
            NavigationGuidanceAction.TURN_RIGHT -> "우회전"
            NavigationGuidanceAction.STRAIGHT -> "직진 이동"
            NavigationGuidanceAction.CURB_GAP -> "단차 구간"
            NavigationGuidanceAction.STAIRS -> "계단 구간"
            NavigationGuidanceAction.CONSTRUCTION -> "공사 구간"
            NavigationGuidanceAction.ELEVATOR -> "엘리베이터"
            NavigationGuidanceAction.BUS -> "버스 탑승"
            NavigationGuidanceAction.SUBWAY -> "지하철 탑승"
            NavigationGuidanceAction.ALIGHT -> "하차"
            NavigationGuidanceAction.ARRIVAL -> "목적지"
            else -> fallbackTitle
        }
    }

    val distanceLabel = "${displayDistanceMeters}m"
    return when (action) {
        NavigationGuidanceAction.CROSSWALK -> "$distanceLabel 앞 횡단보도"
        NavigationGuidanceAction.TURN_LEFT -> "$distanceLabel 후 좌회전"
        NavigationGuidanceAction.TURN_RIGHT -> "$distanceLabel 후 우회전"
        NavigationGuidanceAction.STRAIGHT -> "$distanceLabel 직진 이동"
        NavigationGuidanceAction.CURB_GAP -> "$distanceLabel 앞 단차 구간"
        NavigationGuidanceAction.STAIRS -> "$distanceLabel 앞 계단 구간"
        NavigationGuidanceAction.CONSTRUCTION -> "$distanceLabel 앞 공사 구간"
        NavigationGuidanceAction.ELEVATOR -> "$distanceLabel 앞 엘리베이터"
        NavigationGuidanceAction.BUS -> "$distanceLabel 앞 버스 탑승"
        NavigationGuidanceAction.SUBWAY -> "$distanceLabel 앞 지하철 탑승"
        NavigationGuidanceAction.ALIGHT -> "$distanceLabel 후 하차"
        NavigationGuidanceAction.ARRIVAL -> "목적지까지 $distanceLabel"
        else -> "$distanceLabel 후 $fallbackTitle"
    }
}

internal fun formatNavigationLiveGuidanceSpeechText(
    action: NavigationGuidanceAction,
    rawDistanceMeters: Int,
    stage: NavigationLiveGuidanceSpeechStage,
    fallbackTitle: String,
): String {
    if (action == NavigationGuidanceAction.START) {
        return "현재 위치에서 선택한 경로 안내를 시작합니다."
    }
    if (stage == NavigationLiveGuidanceSpeechStage.NEAR_10M) {
        return formatNearNavigationLiveGuidanceSpeechText(
            action = action,
            fallbackTitle = fallbackTitle,
        )
    }

    val distanceLabel = rawDistanceMeters.takeIf { distance -> distance > 0 }?.let { distance -> "${distance}m" }
    return when (action) {
        NavigationGuidanceAction.CROSSWALK ->
            distanceLabel?.let { "$it 앞 횡단보도가 있습니다." } ?: "횡단보도가 있습니다."
        NavigationGuidanceAction.TURN_LEFT ->
            distanceLabel?.let { "$it 후 좌회전입니다." } ?: "좌회전입니다."
        NavigationGuidanceAction.TURN_RIGHT ->
            distanceLabel?.let { "$it 후 우회전입니다." } ?: "우회전입니다."
        NavigationGuidanceAction.STRAIGHT ->
            distanceLabel?.let { "$it 직진 이동입니다." } ?: "직진 이동입니다."
        NavigationGuidanceAction.CURB_GAP ->
            distanceLabel?.let { "$it 앞 단차 구간입니다." } ?: "단차 구간입니다."
        NavigationGuidanceAction.STAIRS ->
            distanceLabel?.let { "$it 앞 계단 구간입니다." } ?: "계단 구간입니다."
        NavigationGuidanceAction.CONSTRUCTION ->
            distanceLabel?.let { "$it 앞 공사 구간입니다." } ?: "공사 구간입니다."
        NavigationGuidanceAction.ELEVATOR ->
            distanceLabel?.let { "$it 앞 엘리베이터가 있습니다." } ?: "엘리베이터가 있습니다."
        NavigationGuidanceAction.BUS ->
            distanceLabel?.let { "$it 앞 버스 탑승 지점입니다." } ?: "버스 탑승 지점입니다."
        NavigationGuidanceAction.SUBWAY ->
            distanceLabel?.let { "$it 앞 지하철 탑승 지점입니다." } ?: "지하철 탑승 지점입니다."
        NavigationGuidanceAction.ALIGHT ->
            distanceLabel?.let { "$it 후 하차입니다." } ?: "하차입니다."
        NavigationGuidanceAction.ARRIVAL ->
            distanceLabel?.let { "목적지까지 $it 남았습니다." } ?: "목적지입니다."
        else ->
            distanceLabel?.let { "$it 후 $fallbackTitle 입니다." } ?: fallbackTitle
    }
}

private fun formatNearNavigationLiveGuidanceSpeechText(
    action: NavigationGuidanceAction,
    fallbackTitle: String,
): String =
    when (action) {
        NavigationGuidanceAction.CROSSWALK -> "곧 횡단보도가 있습니다."
        NavigationGuidanceAction.TURN_LEFT -> "곧 좌회전입니다."
        NavigationGuidanceAction.TURN_RIGHT -> "곧 우회전입니다."
        NavigationGuidanceAction.STRAIGHT -> "곧 직진 이동입니다."
        NavigationGuidanceAction.CURB_GAP -> "곧 단차 구간입니다."
        NavigationGuidanceAction.STAIRS -> "곧 계단 구간입니다."
        NavigationGuidanceAction.CONSTRUCTION -> "곧 공사 구간입니다."
        NavigationGuidanceAction.ELEVATOR -> "곧 엘리베이터가 있습니다."
        NavigationGuidanceAction.BUS -> "곧 버스 탑승 지점입니다."
        NavigationGuidanceAction.SUBWAY -> "곧 지하철 탑승 지점입니다."
        NavigationGuidanceAction.ALIGHT -> "곧 하차입니다."
        NavigationGuidanceAction.ARRIVAL -> "곧 목적지입니다."
        else -> {
            val title = fallbackTitle.trim()
            if (title.isEmpty()) {
                "곧 다음 안내입니다."
            } else {
                "곧 $title 입니다."
            }
        }
    }

private fun Int.floorToLiveGuidanceBucket(bucketMeters: Int): Int =
    (this / bucketMeters)
        .times(bucketMeters)
        .coerceAtLeast(bucketMeters)

private const val LIVE_GUIDANCE_DISTANCE_HIDE_THRESHOLD_METERS = 10
private const val LIVE_GUIDANCE_DISTANCE_NEAR_BUCKET_LIMIT_METERS = 100
private const val LIVE_GUIDANCE_DISTANCE_NEAR_BUCKET_METERS = 10
private const val LIVE_GUIDANCE_DISTANCE_FAR_BUCKET_METERS = 20
