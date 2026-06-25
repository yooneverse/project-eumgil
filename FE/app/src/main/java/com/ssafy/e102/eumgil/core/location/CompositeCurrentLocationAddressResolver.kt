package com.ssafy.e102.eumgil.core.location

import com.ssafy.e102.eumgil.core.model.GeoCoordinate

/**
 * Primary resolver가 null을 돌려주면 fallback resolver로 재시도하는 합성 reverse geocoder.
 *
 * 사용 예: 카카오 로컬 REST API를 primary로 두고 (정밀도 ↑), 권한 거부·네트워크 실패 등으로
 * null이 오면 Android `Geocoder` 기반 resolver를 fallback으로 두어 적어도 도로명/행정구역
 * 단위 주소는 보장한다.
 *
 * Primary가 빈 문자열을 정상 응답으로 돌려주면 그대로 빈 문자열을 반환한다 — null과 빈 값을
 * 구분하기 위함. 즉 fallback은 "primary가 명시적으로 실패(null)했을 때만" 호출된다.
 */
class CompositeCurrentLocationAddressResolver(
    private val primary: CurrentLocationAddressResolver,
    private val fallback: CurrentLocationAddressResolver,
) : CurrentLocationAddressResolver {
    override suspend fun resolveAddress(coordinate: GeoCoordinate): String? =
        primary.resolveAddress(coordinate)
            ?: fallback.resolveAddress(coordinate)
}
