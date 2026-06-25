# 경로 API 운영 DB Migration 체크리스트

> 기준 코드: `be/feat/transit-refresh-S14P31E102-501`
> 적용 SQL: `scripts/db/migrations/2026-05-10_route_api_schema.sql`

## 목적

경로 선택, 대중교통 도착정보 갱신, 재탐색, 안내 종료, 평가 API가 운영 DB에서 같은 schema 계약으로 동작하도록 `route_sessions`, `route_ratings`, `subway_stations`, `subway_timetables`를 명시적으로 보정한다.

## 적용 전 체크

- [ ] 운영 DB 백업 또는 point-in-time recovery 가능 시점을 확인한다.
- [ ] `users.user_id`가 존재하는지 확인한다.
- [ ] PostGIS extension 설치 권한이 있는 DB 계정인지 확인한다.
- [ ] `route_sessions` 기존 row 중 `user_id`, `route_id`, `start_point`, `end_point`, `route_snapshot_json`, `status`가 `NULL`인 row가 없는지 확인한다.
- [ ] `route_ratings` 기존 row 중 `user_id`, `session_id`, `route_id`, `score`, `route_context_json`이 `NULL`인 row가 없는지 확인한다.
- [ ] `route_ratings.session_id` 중복 row가 없는지 확인한다.
- [ ] `subway_stations.odsay_station_id` 중복 row가 없는지 확인한다.
- [ ] `subway_timetables`의 `(odsay_station_id, service_day_type, way_code, departure_second_of_day, end_station_name)` 중복 row가 없는지 확인한다.
- [ ] 운영 배포 전 지하철 시간표 CSV 적재 여부를 확인한다. 미적재 상태면 SUBWAY refresh는 `ARRIVAL_UNKNOWN`으로 떨어질 수 있다.

## 적용 명령

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f scripts/db/migrations/2026-05-10_route_api_schema.sql
```

SSH tunnel 기반으로 실행하는 경우:

```bash
ssh -i K14E102T.pem -N -L 15432:127.0.0.1:5432 ubuntu@<INTERNAL_S1_HOST>

DATABASE_URL='postgresql://<user>:<password>@127.0.0.1:15432/e102' \
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f scripts/db/migrations/2026-05-10_route_api_schema.sql
```

## 적용 후 검증

- [ ] `route_sessions.active_route_key` column이 존재한다.
- [ ] `route_sessions`에 `uk_route_sessions_user_active_route` unique 제약이 존재한다.
- [ ] `route_sessions`에 `idx_route_sessions_user_route_updated`, `idx_route_sessions_route_updated` index가 존재한다.
- [ ] `route_ratings.session_id` column과 `uk_route_ratings_session` unique 제약이 존재한다.
- [ ] `route_ratings.route_context_json`이 `NOT NULL`이다.
- [ ] `subway_stations.odsay_station_id` unique 제약이 존재한다.
- [ ] `subway_timetables`에 `idx_stt_next_dep` index와 `uk_subway_timetables_lookup` unique 제약이 존재한다.
- [ ] backend 기동 후 `POST /routes/{routeId}/select`가 `data.sessionId`를 반환한다.
- [ ] `POST /routes/{routeId}/transit-refresh`가 ACTIVE session에서만 성공한다.
- [ ] `POST /route-ratings`는 COMPLETED session에서만 성공하고 ACTIVE session에는 `RT4092`를 반환한다.

## 실패 시 판단

- `NULL values required` 예외: 기존 row가 현재 엔티티 계약과 맞지 않는다. row를 보정하거나 백업 후 삭제 정책을 결정한 뒤 재실행한다.
- unique 제약 생성 실패: 중복 row가 있다. 최신 row 보존 기준을 정해 중복을 정리한 뒤 재실행한다.
- PostGIS extension 실패: DB 계정 권한 또는 RDS extension 허용 상태를 확인한다.

## Rollback 기준

이 migration은 schema 보정 중심이며 자동으로 데이터를 삭제하지 않는다. 실패하면 transaction이 rollback된다. 이미 성공한 뒤 rollback이 필요하면 배포를 중단하고 백업/PITR 복구를 우선 검토한다.
