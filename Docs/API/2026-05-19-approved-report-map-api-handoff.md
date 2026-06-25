# 승인 제보 지도 마커 API Handoff

> 작성일: 2026-05-19  
> 대상: FE 지도 화면 승인 제보 마커 연동을 위한 BE API 계약

## Purpose

FE 지도 화면은 현재 지도 중심 좌표와 반경을 기준으로 승인된 위험 제보만 받아 마커와 상세 시트에 표시한다.

This endpoint must be separate from `/hazard-reports/me`. `/hazard-reports/me` is the current user's own report history API, while this map API returns all user reports that are visible to the public map after approval. The response must include APPROVED only reports.

## Endpoint

```http
GET /hazard-reports/approved?lat={lat}&lng={lng}&radius={meters}
```

## Query Parameters

| name | type | required | description |
| --- | --- | --- | --- |
| `lat` | number | yes | 지도 viewport 중심 latitude |
| `lng` | number | yes | 지도 viewport 중심 longitude |
| `radius` | integer | yes | 검색 반경. 단위는 meter이며 endpoint placeholder의 `{meters}` 값 |

## Response Fields

```json
{
  "reports": [
    {
      "reportId": 1001,
      "reportType": "BROKEN_BLOCK",
      "status": "APPROVED",
      "reportPoint": {
        "lat": 35.1797,
        "lng": 129.0752
      },
      "address": "부산광역시 부산진구 예시로 100",
      "description": "보도블록 파손으로 휠체어 통행이 어렵습니다.",
      "imageUrls": [
        "https://cdn.example.com/hazard-reports/1001/1.jpg"
      ],
      "approvedAt": "2026-05-19T09:00:00Z"
    }
  ]
}
```

| field | type | required | description |
| --- | --- | --- | --- |
| `reports` | array | yes | 승인 제보 목록. 데이터가 없으면 빈 배열 |
| `reportId` | number | yes | 제보 고유 ID. FE marker click target과 상세 시트 식별자로 사용 |
| `reportType` | string | yes | 제보 유형 API 값. 예: `BROKEN_BLOCK`, `OBSTACLE`, `DAMAGED_ROAD`, `SIGNAL_ISSUE` |
| `status` | string | yes | 반드시 `APPROVED` |
| `reportPoint` | object | yes | 제보 위치 좌표 |
| `reportPoint.lat` | number | yes | 마커 latitude |
| `reportPoint.lng` | number | yes | 마커 longitude |
| `address` | string or null | no | 상세 시트 위치 문구. 없으면 FE fallback 문구 사용 |
| `description` | string or null | no | 상세 시트 설명. 없으면 FE fallback 문구 사용 |
| `imageUrls` | string array | yes | 상세 시트 이미지 URL 목록. 이미지가 없으면 빈 배열 |
| `approvedAt` | string or null | no | 승인 시각. ISO-8601 문자열 권장 |

## Filtering Rule

- Return all user reports within the requested `lat`/`lng`/`radius` area.
- Include APPROVED only reports.
- Exclude `PENDING`, `REJECTED`, deleted, hidden, or private reports.
- Do not scope the result to the authenticated user's own reports.
- Keep this API separate from `/hazard-reports/me`; FE must not reuse `/hazard-reports/me` for public map markers.
- Radius filtering should use meters from the `radius` query parameter. If pagination becomes necessary later, add it as an explicit extension without changing the base contract above.

## Error Policy

| case | expected status | policy |
| --- | --- | --- |
| invalid `lat`, `lng`, or `radius` | `400 Bad Request` | Return the standard API error body with a validation message |
| missing or invalid auth token, if auth is required | `401 Unauthorized` | Return the standard auth error body |
| caller has no permission to view public approved reports | `403 Forbidden` | Return the standard permission error body |
| server or upstream failure | `5xx` | Return the standard server error body; FE will keep the existing marker state or show a non-blocking load failure |

For an empty area, return `200 OK` with `"reports": []` rather than an error.

## Image URL Policy

- `imageUrls` must contain URLs that the mobile client can load directly with Coil.
- If images are stored privately, BE should return signed or proxied URLs and document the TTL.
- Do not return local storage paths or BE-internal object keys.
- If no image is available, return an empty `imageUrls` array. FE will render the no-photo fallback state.
- The first item in `imageUrls` is treated as the representative image in the FE detail sheet.

## FE Connection Points

Planned FE integration points after BE API availability:

- `ApprovedReportMapRepository`: FE repository contract for loading approved reports by viewport query.
- `ApprovedReportMapQuery(center: GeoCoordinate, radiusMeters: Int)`: query model mapped to `lat`, `lng`, and `radius`.
- `ApprovedReportMapEntry`: FE entry model expected to map `reportId`, `reportType`, `status`, `reportPoint`, `address`, `description`, `imageUrls`, and `approvedAt`.
- `HazardReportsRemoteDataSource`: future remote data source method should call `GET /hazard-reports/approved`.
- `MapViewModel`: will request the repository when the map viewport changes and expose approved report marker state.
- `MapScreen`/map overlay: will render approved report markers and open the approved report detail sheet on marker click.

## Backend Handoff Summary

BE 작업은 `/hazard-reports/me`를 수정하거나 확장하지 말고, public map marker 용도의 별도 endpoint인 `GET /hazard-reports/approved?lat={lat}&lng={lng}&radius={meters}`를 제공해야 한다. 이 endpoint는 모든 사용자 제보 중 `APPROVED` 상태만 반환해야 하며, FE가 지도 마커와 상세 시트에 필요한 response fields를 안정적으로 받을 수 있어야 한다.
