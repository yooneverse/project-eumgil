# Layer And Package Convention

## 목적

백엔드 패키지 구조와 계층별 작성 기준을 맞춘다.

API path, request/response 필드, enum 값은 API 명세와 ERD를 우선한다.

## 패키지 구조

`com.ssafy.e102` 바로 아래는 `domain`, `global`로 나눈다.

```text
com.ssafy.e102
├─ domain
│  ├─ user
│  ├─ place
│  ├─ route
│  ├─ report
│  └─ publictransit
└─ global
   ├─ config
   ├─ response
   ├─ exception
   ├─ security
   ├─ external
   ├─ entity
   └─ util
```

도메인 패키지 내부는 아래 구조를 기본으로 둔다.

```text
{domain}
├─ controller
├─ dto
│  ├─ request
│  └─ response
├─ entity
├─ exception
├─ repository
└─ service
```

예시:

```text
place
├─ controller
├─ dto
│  ├─ request
│  └─ response
├─ entity
├─ exception
├─ repository
└─ service
```

## global 기준

- `global.response`: `ApiResponse`, `ErrorResponse`
- `global.exception`: `BusinessException`, `ErrorCode`, `CommonErrorCode`, `GlobalExceptionHandler`
- `global.config`: Spring 설정 클래스
- `global.security`: 인증/인가, JWT, Security 설정
- `global.external`: 외부 API client
- `global.entity`: `BaseEntity`
- `global.util`: 여러 도메인에서 반복해서 쓰는 순수 변환/계산 함수

## Controller 기준

- API는 API 전용 서브도메인에서 노출하며, 기본 path prefix로 `/api`를 붙이지 않는다.
- API path, method, status code는 API 명세서를 따른다.
- Request Body가 있는 API에는 `@Valid`를 붙인다.
- Entity를 API 응답으로 직접 반환하지 않는다.
- Controller에서 Repository나 외부 API client를 직접 호출하지 않는다.
- 성공 응답은 공통 응답 규격으로 감싼다.

예시:

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/places")
public class PlaceController {

    private final PlaceService placeService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PlaceSearchResponse>>> searchPlaces(
            @Valid PlaceSearchRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(placeService.searchPlaces(request)));
    }
}
```

## DTO 기준

- Request DTO와 Response DTO는 분리한다.
- DTO 필드명은 API 명세의 camelCase를 따른다.
- 좌표 요청/응답은 `lat`, `lng` 구조를 사용한다.
- Geometry 타입을 API DTO에 직접 노출하지 않는다.
- DTO는 Java record 사용을 우선 검토한다.
- Validation annotation은 Request DTO에 둔다.
- `from(entity)`는 Entity를 Response DTO로 변환할 때 사용한다.
- `of(...)`는 여러 값을 조합해 DTO를 생성할 때 사용한다.
- `toEntity()`는 Request DTO를 Entity로 단순 생성할 때만 사용한다.
- `toEntity()` 안에서 Repository 조회, 외부 API 호출, 권한 검증 같은 비즈니스 로직을 수행하지 않는다.
- 연관관계 연결이나 복잡한 생성 규칙이 필요하면 Service 또는 Entity factory에서 처리한다.

## Service 기준

- Service 인터페이스는 기본 생성하지 않는다.
- 구현체가 2개 이상 필요하거나 대체 구현이 필요한 경우에만 인터페이스를 둔다.
- 조회 메서드는 `@Transactional(readOnly = true)`를 사용한다.
- 생성/수정/삭제 메서드는 `@Transactional`을 사용한다.
- 외부 API 호출은 `global.external` 하위 client/service로 분리한다.

## Entity 기준

- Entity와 enum은 도메인 내부 `entity` 패키지에 둔다.
- Entity에는 무분별한 setter를 만들지 않는다.
- 값 변경은 의미 있는 메서드로 작성한다.
- enum은 `@Enumerated(EnumType.STRING)`을 사용한다.
- 공통 생성/수정 시각은 `global.entity.BaseEntity`와 JPA Auditing으로 관리한다.
- `BaseEntity`는 `@MappedSuperclass`로 두고, 생성일시/수정일시를 반복 선언하지 않는다.
- JPA Auditing 활성화 설정은 `global.config` 하위에 둔다.

## Exception 기준

- 전역 예외 핸들러는 `global.exception.GlobalExceptionHandler` 하나로 관리한다.
- 공통 예외 타입은 `global.exception.BusinessException`을 사용한다.
- 공통 에러 코드 인터페이스는 `global.exception.ErrorCode`로 둔다.
- 도메인에 속하지 않는 공통 에러 코드는 `global.exception.CommonErrorCode`로 둔다.
- 각 도메인의 에러 코드 enum은 `domain.{domain}.exception.{Domain}ErrorCode`로 둔다.
- 도메인별 enum 이름은 `UserErrorCode`, `PlaceErrorCode`, `RouteErrorCode`처럼 도메인명을 접두사로 붙인다.
- 도메인별 `{Domain}ErrorCode`는 `global.exception.ErrorCode`를 구현한다.
