# Config And External Integration Convention

## 목적

Spring profile, config class, 외부 API 연동 위치를 맞춘다.

## profile 파일 기준

```text
src/main/resources
├─ application.yml
├─ application-local.yml
├─ application-dev.yml
└─ application-prod.yml
```

| 파일 | 역할 |
| --- | --- |
| application.yml | 공통 설정 |
| application-local.yml | 로컬 개발용 설정 |
| application-dev.yml | 개발 서버용 설정 |
| application-prod.yml | 운영 서버용 설정 |

예시:

```yaml
external:
  kakao:
    base-url: https://dapi.kakao.com
    api-key: ${KAKAO_API_KEY:}
```

## 초기 Config 기준

초기 세팅에서 둘 수 있는 Config:

- `CorsConfig`
- `OpenApiConfig`
- `JpaAuditingConfig`
- Geometry 관련 설정

기능 착수 또는 PoC 이후 추가:

- `OAuthConfig`
- `RedisConfig`
- `RabbitMqConfig`
- `GraphHopperConfig`

## CORS 기준

- CORS는 브라우저 기반 프론트엔드 또는 관리자 화면에서 백엔드를 호출할 때 필요한 설정이다.
- 프론트 로컬 개발 주소와 개발 서버 주소가 정해졌을 때 최소 범위로 추가한다.

## 외부 API client 위치

외부 API 연동 코드는 `global.external` 하위에 둔다.

```text
global.external
├─ kakao
├─ odsay
├─ bims
└─ graphhopper
```

도메인 Service는 외부 API의 원본 응답 구조에 직접 의존하지 않는다.

## 외부 설정값 기준

- 외부 API key는 코드에 직접 작성하지 않는다.
- API key는 환경변수 또는 로컬 전용 설정 파일로 주입한다.
- 로컬 전용 비밀 설정 파일은 Git에 올리지 않는다.
- 호출 제한, 장애 응답, timeout은 PoC 단계에서 먼저 확인한다.
- 확인되지 않은 외부 API 응답 필드는 API 명세에 확정값처럼 쓰지 않는다.
