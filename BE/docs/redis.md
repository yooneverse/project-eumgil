# Redis

## 선택 이유

- 짧은 TTL 캐시와 토큰 저장소를 같은 인메모리 계층에서 처리할 수 있어 운영 단순성이 높다.
- 경로 탐색, 실시간 도착 정보, 인증 토큰처럼 읽기 빈도가 높고 만료 기반 관리가 중요한 데이터에 적합하다.
- Lua script를 통해 토큰 회전 같은 원자적 갱신을 구현할 수 있다.

## 서비스에서 맡는 역할

- 경로 탐색 결과 캐시
- BIMS 실시간 도착 정보 캐시
- refresh token, signup token, access token blacklist 저장
- GraphHopper active / previous slot 상태 저장

## 핵심 구현 방식

### 1. 경로 탐색 캐시

- `RouteSearchCacheService`가 도보/대중교통 경로 후보와 부가 metadata를 30분 TTL로 저장한다.
- 사용자가 경로를 선택하거나 재탐색할 때 외부 API와 GraphHopper 재호출을 줄인다.

### 2. 실시간 정보 캐시

- `BimsArrivalCacheService`는 버스 도착 정보를 1분 TTL로 저장한다.
- 같은 정류장/노선 조합에 대해 짧은 시간 안에 반복 호출이 몰릴 때 BIMS API 부하를 줄인다.

### 3. 인증 토큰 저장소

- `RedisAuthTokenStore`는 refresh token, signup token, blacklist를 저장한다.
- refresh token은 평문이 아니라 hash 형태로 관리해 저장소 노출 시 위험을 낮춘다.
- access token blacklist는 만료 시점까지 TTL을 걸어 로그아웃/강제 만료를 처리한다.

### 4. 원자적 refresh rotation

- refresh token 재발급은 Lua script로 old token 삭제와 new token 저장을 한 번에 수행한다.
- 같은 세션에서 중복 요청이 들어와도 old/new 상태가 꼬이지 않게 설계했다.

## 예외 처리와 동시성 대응

### 캐시 불일치 허용 범위 관리

- 경로 캐시는 일정 시간 stale 데이터를 허용하되, 위험 제보나 관리자 반영처럼 강한 최신성이 필요한 흐름은 별도 반영 시점으로 분리했다.
- 실시간 도착 정보는 TTL을 짧게 유지해 오래된 데이터를 오래 들고 있지 않도록 했다.

### 중복 요청과 레이스 컨디션 완화

- refresh rotation은 Lua script로 원자화해 다중 요청 시 토큰 상태 손상을 방지한다.
- GraphHopper slot 상태도 Redis 키를 기준으로 active/previous를 읽게 해 애플리케이션 인스턴스 간 상태 해석을 통일했다.

### 장애 시 처리

- 캐시 miss는 DB나 외부 API 재조회로 복구 가능하게 설계했다.
- Redis 장애 시 일부 응답 성능과 인증 편의성은 떨어지지만, 핵심 영속 데이터는 DB와 외부 시스템에 남는다.

## 한계와 후속 과제

- 캐시 키 설계가 커질수록 무효화 전략을 더 명시적으로 관리할 필요가 있다.
- 실시간 데이터와 세션 데이터가 한 Redis에 공존하므로 운영 단계에서는 메모리 정책과 keyspace 모니터링이 중요하다.
