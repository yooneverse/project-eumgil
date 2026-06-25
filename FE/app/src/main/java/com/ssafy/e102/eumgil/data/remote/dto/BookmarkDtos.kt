package com.ssafy.e102.eumgil.data.remote.dto

data class BookmarkPointDto(
    val lat: Double,
    val lng: Double,
)

data class BookmarkAccessibilityFeatureDto(
    val featureType: String,
    val isAvailable: Boolean,
)

data class BookmarkListItemDto(
    val bookmarkId: Long,
    val bookmarkTargetId: String,
    val targetType: String,
    val placeId: Long?,
    val provider: String?,
    val providerPlaceId: String?,
    val name: String,
    val category: String?,
    val providerCategory: String?,
    val address: String?,
    val point: BookmarkPointDto,
    val accessibilityFeatures: List<BookmarkAccessibilityFeatureDto> = emptyList(),
)

data class BookmarkPageDto(
    val content: List<BookmarkListItemDto>,
    val size: Int,
    val nextCursor: Long?,
    val hasNext: Boolean,
)

data class CreateBookmarkRequestDto(
    val placeId: Long? = null,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val name: String? = null,
    val providerCategory: String? = null,
    val address: String? = null,
    val point: BookmarkPointDto? = null,
)

data class CreateBookmarkResponseDto(
    val bookmarkId: Long,
    val bookmarkTargetId: String,
    val targetType: String,
    val placeId: Long?,
)
