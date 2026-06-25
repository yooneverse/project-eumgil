# Transit Search Leg Response Plan

## Work Lane

- Lane: BE
- Scope: `POST /routes/search/transit` response contract and backend implementation plan
- Out of scope: FE implementation changes, DB schema changes, `transit-refresh` response shape changes

## Source Documents

- `Docs/API/길안내_도메인/2026-05-06_경로_API_명세.md`
- `BE/src/main/java/com/ssafy/e102/domain/route/dto/response/RouteLegResponse.java`
- `BE/src/main/java/com/ssafy/e102/domain/route/dto/response/TransitLaneOptionResponse.java`
- `BE/src/main/java/com/ssafy/e102/domain/route/service/TransitRouteSearchService.java`
- `BE/src/main/java/com/ssafy/e102/domain/route/service/TransitRefreshService.java`
- `BE/src/main/java/com/ssafy/e102/domain/route/repository/SubwayTimetableRepository.java`
- `BE/src/main/java/com/ssafy/e102/domain/route/entity/SubwayTimetable.java`

## Target Contract

`routes[].legs[]` keeps a common leg envelope and exposes type-specific transit fields.

### BUS leg

```json
{
  "sequence": 1,
  "type": "BUS",
  "role": "TRANSIT",
  "routeNo": "100",
  "laneOptions": [
    {
      "routeNo": "100",
      "remainingMinute": 3,
      "durationSecond": 720,
      "estimatedTimeMinute": 12,
      "isLowFloor": true
    },
    {
      "routeNo": "101",
      "remainingMinute": 8,
      "durationSecond": 720,
      "estimatedTimeMinute": 12,
      "isLowFloor": false
    }
  ],
  "boardingStop": {
    "name": "서면역",
    "lat": 35.1579,
    "lng": 129.0592
  },
  "arrivingStop": {
    "name": "부산역",
    "lat": 35.1152,
    "lng": 129.0415
  },
  "isLowFloor": true
}
```

- `laneOptions[]` is required for BUS legs.
- `routeNo` and `isLowFloor` on the leg are representative summary values.
- Candidate-specific arrival and low-floor status remain under `laneOptions[]`.

### SUBWAY leg

```json
{
  "sequence": 2,
  "type": "SUBWAY",
  "role": "TRANSIT",
  "instruction": "부산 2호선에 탑승하세요.",
  "distanceMeter": 4100.0,
  "durationSecond": 780,
  "estimatedTimeMinute": 13,
  "geometry": "LINESTRING(...)",
  "guidanceEvents": [],
  "routeNo": "부산 2호선",
  "remainingMinute": 4,
  "headsign": "장산행",
  "boardingStop": {
    "name": "서면",
    "lat": 35.1579,
    "lng": 129.0592
  },
  "arrivingStop": {
    "name": "해운대",
    "lat": 35.1631,
    "lng": 129.1636
  }
}
```

- SUBWAY legs do not use `laneOptions[]`.
- `remainingMinute` is the initial timetable-based wait time at search time.
- `headsign` is built from `SubwayTimetable.endStationName + "행"`.
- `departureTimeText` is not exposed in the public search response.

## Current Implementation Facts

- `SubwayTimetable` already stores `odsayStationId`, `serviceDayType`, `wayCode`, `departureSecondOfDay`, and `endStationName`.
- `SubwayTimetableRepository` already supports next same-day and first next-day departure lookup.
- `TransitRefreshService` already computes SUBWAY `remainingMinute` from the current Seoul time and timetable departure seconds.
- `TransitRouteSearchService` already stores SUBWAY `wayCode` and `nextDeparture` metadata in the route snapshot.
- `RouteSelectService` recursively strips `remainingMinute` before persisting selected route context, which should continue to prevent volatile wait times from being stored as durable route facts.

## Implementation Plan

1. Extend `RouteLegResponse` with nullable `Integer remainingMinute` and `String headsign`, both `@JsonInclude(NON_NULL)`.
2. Update every `new RouteLegResponse(...)` call site to pass the new fields without changing WALK/BUS behavior.
3. In `TransitRouteSearchService.toTransitLeg`, when `odsayLeg.type() == SUBWAY`, call the existing next-departure lookup and derive:
   - `remainingMinute`: same calculation rule as `TransitRefreshService`, including next-day rollover.
   - `headsign`: `endStationName + "행"` when `endStationName` is non-blank.
4. Keep BUS behavior unchanged:
   - `laneOptions[]` remains the BUS candidate list.
   - `laneOptions[].remainingMinute` remains BIMS-based.
   - leg-level `isLowFloor` remains the representative BUS low-floor summary.
5. Keep snapshot/cache behavior compatible:
   - It is acceptable to keep `departureTimeText` and `departureSecondOfDay` inside internal metadata.
   - Do not expose `departureTimeText` in `RouteLegResponse`.
   - Ensure selected route snapshots do not persist volatile `remainingMinute`.
6. Update backend tests:
   - Add/adjust a SUBWAY search test that stubs a timetable row and asserts `remainingMinute` and `headsign`.
   - Add a no-timetable case asserting the search still succeeds and omits/nulls those fields.
   - Keep existing BUS tests asserting `laneOptions[]` contains `routeNo`, `remainingMinute`, `durationSecond`, `estimatedTimeMinute`, and `isLowFloor`.
7. Update API docs after implementation if field null/omission behavior differs from this plan.

## Implementation Harness Preflight

- Predicted changed files:
  - `BE/src/main/java/com/ssafy/e102/domain/route/dto/response/RouteLegResponse.java`: add nullable SUBWAY-only response fields and preserve backward-compatible constructors.
  - `BE/src/main/java/com/ssafy/e102/domain/route/service/TransitRouteSearchService.java`: derive SUBWAY `remainingMinute` and `headsign` from timetable lookup and preserve them through leg copy steps.
  - `BE/src/main/java/com/ssafy/e102/domain/route/service/RerouteService.java`: preserve new leg fields when truncating/resequencing route payloads.
  - `BE/src/test/java/com/ssafy/e102/domain/route/service/TransitRouteSearchServiceTest.java`: assert SUBWAY response fields and no-timetable degradation.
  - `BE/src/test/java/com/ssafy/e102/domain/route/dto/RouteDtoJsonTest.java`: assert JSON omission and SUBWAY field serialization.
- Expected side effects:
  - PUBLIC_TRANSIT search JSON adds `remainingMinute` and `headsign` only on SUBWAY legs when timetable data exists.
  - WALK and BUS serialization remains backward-compatible; BUS candidate data stays in `laneOptions[]`.
  - Route select still removes `remainingMinute` recursively before durable session snapshot storage.
- Cases to test:
  - BUS-only route response retains required `laneOptions[]`.
  - SUBWAY route with timetable returns `remainingMinute` and `headsign`.
  - SUBWAY route without timetable still succeeds and omits nullable fields.
  - JSON serialization omits BUS/SUBWAY-only fields from WALK legs.
  - Local API smoke calls for one bus route and one subway route near Gangseo.
- Rollback path:
  - Revert the DTO and service/test changes in this slice; API docs can remain as target contract only if implementation is paused before release.

## Success Criteria

- `POST /routes/search/transit` returns BUS legs with required `laneOptions[]`; each BUS option can include `routeNo`, `remainingMinute`, `durationSecond`, `estimatedTimeMinute`, and `isLowFloor`.
- `POST /routes/search/transit` returns SUBWAY legs with `routeNo`, `boardingStop`, `arrivingStop`, `remainingMinute`, and `headsign` when timetable data exists.
- SUBWAY `headsign` renders as examples like `장산행`, `양산행`, `노포행`.
- SUBWAY `remainingMinute` uses the same current-time and next-day rollover semantics as `transit-refresh`.
- `departureTimeText` is not present in the public search response.
- WALK legs do not expose BUS/SUBWAY-only fields.
- Existing `transit-refresh` behavior remains backward-compatible.
- Route select/session persistence does not store volatile search-time `remainingMinute` values.
- Backend tests cover BUS response shape, SUBWAY response shape, no-timetable degradation, and JSON omission/null behavior.

## Validation Plan

- Run targeted backend unit tests:
  - `TransitRouteSearchServiceTest`
  - `RouteDtoJsonTest`
  - `RouteSelectServiceTest`
- Run route controller tests that serialize `POST /routes/search/transit`.
- Run one local/dev smoke request containing a BUS leg and one containing a SUBWAY leg, then verify the serialized JSON against this contract.

## Cross-Lane Handoff

- FE should map `legs[].type == BUS` to `laneOptions[]` for bus number, wait time, and low-floor labels.
- FE should map `legs[].type == SUBWAY` to leg-level `routeNo`, `remainingMinute`, and `headsign`.
- FE should continue to treat search `remainingMinute` as an initial display value and refresh it during navigation with `transit-refresh`.
