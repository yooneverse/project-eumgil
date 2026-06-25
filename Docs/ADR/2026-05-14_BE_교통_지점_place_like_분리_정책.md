# 📋 BE 교통 지점 place-like 분리 정책 ADR

> **작성일:** 2026-05-14  
> **작성자:** 김응서  
> **상태:** Accepted

---

## 1. 배경

지도에서 사용자가 선택하는 대상은 일반 장소뿐 아니라 버스정류장, 지하철역도 포함된다.

UX 관점에서는 세 대상 모두 아래 동작을 제공해야 한다.

- 지도 POI 클릭 시 바텀시트 표시
- 검색 결과에서 선택 가능
- 출발지 또는 도착지로 설정 가능
- 북마크 후보로 사용 가능
- 관리자 지도에서 확인 가능

하지만 DB 관점에서는 일반 장소, 버스정류장, 지하철역의 원천과 식별자가 다르다.

| 대상 | 주요 원천/식별자 | 데이터 성격 |
| --- | --- | --- |
| 일반 장소 | Kakao place id, 내부 `places.place_id` | 음식점, 관광지, 공공기관 등 서비스 장소 |
| 버스정류장 | BIMS `bstopid`, `arsno` | 정류장 마스터, 도착정보, 저상버스 정보 |
| 지하철역 | ODsay station id, 내부 `subway_stations.subway_station_id` | 역 마스터, 시간표, 엘리베이터, 접근성 정보 |

따라서 모든 선택 가능 대상을 `places`에 넣는 방식은 단기적으로 단순하지만, 장기적으로 정류장/역 고유 도메인 정보를 보존하기 어렵고 중복 데이터가 생긴다.

특히 지하철역은 `places`에 일부 데이터가 존재할 수 있으나, 현재 프로젝트의 ERD와 코드 기준으로 도시철도 접근성 핵심 데이터는 `subway_stations`, `subway_station_elevators`, `subway_timetables` 쪽에서 관리하는 방향이다.

---

## 2. 결정 사항

일반 장소, 버스정류장, 지하철역은 DB 테이블을 분리한다.

```text
places
  일반 장소

place_accessibility_features
  일반 장소 접근성 정보

bus_stops
  BIMS 버스정류장 마스터

subway_stations
  지하철역 마스터

subway_station_accessibility_features
  지하철역 접근성 요약 정보

subway_station_elevators
  지하철역 엘리베이터 개별 좌표

subway_timetables
  지하철 시간표
```

다만 FE/API 관점에서는 버스정류장과 지하철역도 일반 장소처럼 다룰 수 있도록 place-like 응답을 제공한다.

즉, **DB는 분리하고, API 응답과 UX는 통일한다.**

---

## 3. 상세 정책

### 3.1 `places`에서 지하철역 제거

지하철역은 최종적으로 `places`에서 제거한다.

단, 제거는 바로 수행하지 않는다. 먼저 아래 대체 경로가 완성되어야 한다.

1. 지하철역 검색 결과가 `subway_stations` 기반으로 매칭된다.
2. 지하철역 POI 클릭이 `subway_stations` 기반 상세 응답으로 변환된다.
3. 지하철역 접근성 정보가 `subway_station_accessibility_features` 또는 `subway_station_elevators` 기반으로 응답된다.
4. 지하철역 북마크가 `place_id` 없이도 동작한다.
5. 관리자 지도에서 지하철역이 별도 레이어 또는 통합 지점 레이어로 표시된다.

### 3.2 `place_accessibility_features`는 재사용하지 않음

기존 `place_accessibility_features`는 이름은 범용처럼 보이지만 실제 스키마와 엔티티가 `Place`에 강하게 묶여 있다.

```text
place_accessibility_features.place_id NOT NULL
```

따라서 `subway_stations`와 직접 관계를 맺지 않는다.

지하철역 접근성 정보는 별도 테이블로 둔다.

```text
subway_station_accessibility_features
- id
- subway_station_id
- feature_type
- is_available
- description nullable
```

`feature_type`은 기존 `AccessibilityFeatureType` enum을 재사용한다.

예시:

- `elevator`
- `accessibleToilet`
- `accessibleParking`
- `guidanceFacility`

### 3.3 엘리베이터 feature와 엘리베이터 좌표는 분리

`subway_station_accessibility_features.feature_type = elevator`는 “이 역에 엘리베이터가 있음”이라는 역 단위 요약 정보다.

`subway_station_elevators`는 “엘리베이터 개별 위치 좌표”다.

둘은 중복이 아니라 다른 의미다.

- 역 상세/검색 badge: `elevator=true` feature 사용
- 경로 보정/도보 연결점 계산: `subway_station_elevators.point` 사용

`subway_station_elevators`가 하나 이상 있으면 `elevator=true` feature를 계산하거나 동기화할 수 있다. 단, 원천 데이터가 없다는 뜻과 실제로 시설이 없다는 뜻은 다르므로, 근거 없는 `false` row를 만들지 않는다.

### 3.4 버스정류장도 place-like 응답으로 제공

버스정류장은 `bus_stops`에 BIMS 마스터로 저장한다.

지도 POI 클릭 시 Kakao SDK가 `버스정류장` 같은 일반명만 내려주는 경우에는 좌표 기준으로 `bus_stops`에서 가까운 정류장을 매칭해 이름을 보정한다.

FE/API 관점에서는 일반 장소처럼 선택 가능해야 하지만, 내부 식별자는 `bstopid`, `arsno`를 유지한다.

### 3.5 관리자 지도는 `places`만 보지 않음

관리자 지도에서 표시해야 하는 지점은 일반 장소만이 아니다.

관리자 지도는 아래 데이터를 통합 지점 레이어로 표시한다.

- `places`
- `subway_stations`
- `bus_stops`

다만 수정 API는 한 API에 억지로 합치지 않는다.

| 대상 | 수정 API 방향 |
| --- | --- |
| 일반 장소 | 기존 places 관리자 API |
| 지하철역 | 지하철역 관리자 API 또는 transit 관리자 API |
| 버스정류장 | BIMS 동기화 기반, 필요 시 별도 관리자 API |

---

## 4. API 응답 방향

DB는 분리하지만 FE는 선택 가능한 지점을 같은 방식으로 렌더링할 수 있어야 한다.

예시 응답 방향:

```json
{
  "detailType": "SUBWAY_STATION",
  "targetId": "SUBWAY_STATION:130",
  "name": "서면역 부산1호선",
  "address": "부산 부산진구 ...",
  "point": {
    "lat": 35.1577,
    "lng": 129.0592
  },
  "accessibilityFeatures": [
    {
      "featureType": "elevator",
      "isAvailable": true
    },
    {
      "featureType": "accessibleToilet",
      "isAvailable": true
    }
  ]
}
```

`PlaceClickDetailResponse`를 확장할지, 교통 지점 전용 detail response를 만들지는 구현 시점에 결정한다.

단, FE가 처리하는 최종 형태는 아래 속성을 공통으로 가져야 한다.

- 표시명
- 주소 또는 설명
- 좌표
- 접근성 feature 목록
- 출발지/도착지 설정 가능 여부
- 북마크 대상 ID
- 대상 타입

---

## 5. 사이드이펙트

### 5.1 검색 로직

현재 검색이 Kakao 장소 검색 결과 중심이면, 지하철역과 버스정류장을 내부 마스터와 병합하는 로직이 필요하다.

특히 지하철역은 Kakao 이름과 내부 역명이 다를 수 있다.

예:

- `사상역`
- `사상역 부산2호선`
- `사상역 1번출구`

따라서 단순 이름 일치가 아니라 아래 기준을 조합해야 한다.

- 역명 정규화
- 좌표 근접도
- 노선명
- alias 규칙

### 5.2 POI 클릭 로직

지도 SDK의 POI id는 내부 `subway_stations.odsay_station_id` 또는 BIMS `bstopid`와 다르다.

따라서 POI 클릭 시에는 외부 provider id 직접 매칭보다 `nameHint + 좌표` 기반 내부 마스터 매칭이 필요하다.

### 5.3 북마크

지하철역을 `places`에서 제거하면 기존 `place_id` 기반 북마크만으로는 부족하다.

북마크 대상은 아래처럼 type-aware id를 가져야 한다.

```text
PLACE:{placeId}
SUBWAY_STATION:{subwayStationId}
BUS_STOP:{bstopId}
EXTERNAL_POI:{provider}:{providerPlaceId}
EXTERNAL_ADDRESS:{hash}
```

기존 외부 북마크 snapshot 구조를 재사용할 수 있는지 확인해야 한다.

### 5.4 관리자 기능

관리자 지도 조회는 `places`만 반환하면 지하철역과 버스정류장이 사라진다.

따라서 관리자 지도는 통합 지점 조회를 제공해야 한다.

단, 수정 API까지 무리하게 통합하면 도메인 모델이 섞이므로 대상별 수정 API를 둔다.

### 5.5 데이터 마이그레이션

기존 `places`에 지하철역성 데이터가 있다면 삭제 전 이관이 필요하다.

이관 시 단순 이름 join은 위험하다.

필요한 검증:

- `places`에서 지하철역 후보 추출
- `subway_stations`와 이름/좌표 기반 매칭
- 기존 `place_accessibility_features`의 지하철성 feature가 있다면 `subway_station_accessibility_features`로 이관
- 매칭 실패 목록 수동 검수
- 북마크 영향 확인

### 5.6 빠른 필터

현재 지도 빠른 필터가 `places + place_accessibility_features`만 조회하면, 지하철역의 `elevator`, `accessibleToilet` 정보는 필터 결과에 포함되지 않는다.

지하철역을 필터 결과에 포함하려면 `places`와 `subway_stations` 결과를 union하는 조회 모델이 필요하다.

### 5.7 테스트 범위 증가

아래 테스트가 필요하다.

- 지하철역 POI 클릭 매칭 테스트
- 지하철역 검색 결과 병합 테스트
- 지하철 접근성 feature 응답 테스트
- 지하철역 북마크 저장/조회 테스트
- 관리자 지도 통합 지점 조회 테스트
- `places` 지하철 제거 후 회귀 테스트

---

## 6. 마이그레이션 순서

삭제보다 대체 경로 구현을 먼저 한다.

1. `subway_station_accessibility_features` 테이블과 엔티티 추가
2. 지하철 접근성 원천 데이터를 `subway_station_accessibility_features`로 적재
3. 지하철역 POI 클릭 시 `subway_stations` 매칭 로직 추가
4. 지하철역 상세 응답에 접근성 feature 포함
5. 검색 결과에서 지하철역을 place-like 결과로 병합
6. 북마크 대상 ID 정책 확인 및 보강
7. 관리자 지도에서 `places + subway_stations + bus_stops` 통합 조회 추가
8. 기존 `places` 지하철 후보와 feature 이관 검증
9. 매칭 실패/중복/북마크 영향 확인
10. `places`에서 지하철역 데이터 제거

---

## 7. 비결정 사항

이번 ADR에서 확정하지 않는 항목은 아래와 같다.

- `PlaceClickDetailResponse`를 확장할지, 별도 교통 지점 detail response를 만들지
- `SUBWAY_STATION`, `BUS_STOP`을 `PlaceDetailType`에 추가할지
- 지하철 접근성 상세 정보의 원천별 raw payload 보존 여부
- 지하철역 검색 결과를 Kakao 검색 결과와 어떤 우선순위로 병합할지
- 관리자 화면에서 지하철역/버스정류장 수정을 어느 수준까지 허용할지

---

## 8. 후속 작업

- 실제 DB에서 `places` 내 지하철역 후보 개수와 접근성 feature 보유 현황 확인
- `subway_stations`, `subway_station_elevators`, `subway_timetables` 실제 적재 건수 확인
- `subway_station_accessibility_features` ERD 및 migration 작성
- 기존 지하철 접근성 PoC 결과와 현재 ERD v4 문서 정합성 재검토
- 지하철역 POI 클릭 샘플로 `nameHint + 좌표` 매칭 기준 검증
- 관리자 통합 지점 조회 응답 스펙 설계
- 북마크 대상 ID 정책을 `PLACE`, `SUBWAY_STATION`, `BUS_STOP`까지 확장할지 검토
