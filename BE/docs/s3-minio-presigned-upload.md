# S3 / MinIO Presigned Upload

## 선택 이유

- 이미지 바이너리를 백엔드 서버가 직접 중계하지 않고 클라이언트가 스토리지로 바로 업로드하게 만들어 애플리케이션 부하를 줄일 수 있다.
- AWS S3와 MinIO 호환 구성을 같은 코드 경로에서 지원할 수 있어 로컬/개발/운영 환경 전환이 쉽다.
- presigned URL은 권한 위임 범위를 object key와 만료 시간으로 제한할 수 있어 제보 이미지 업로드에 적합하다.

## 서비스에서 맡는 역할

- 위험 제보 이미지 업로드용 presigned PUT URL 발급
- 이미지 조회용 presigned GET URL 발급
- 업로드 대상 object key 검증
- 로컬 MinIO와 S3 호환 운영

## 핵심 구현 방식

### 1. 환경별 스토리지 추상화

- `ReportImageStorageConfig`가 endpoint override, path-style access, 정적 자격 증명을 조합해 S3 또는 MinIO용 `S3Presigner`를 만든다.
- 같은 서비스 코드에서 로컬 MinIO와 운영 S3를 모두 지원한다.

### 2. presigned PUT 기반 직접 업로드

- `HazardReportImageUploadService`가 업로드 전용 object key를 발급하고 presigned PUT URL을 생성한다.
- object key는 `prefix/userId/date/uuid.ext` 규칙으로 만들어 사용자 소유 범위를 분리한다.
- 서버는 파일 자체를 받지 않고 메타데이터와 key만 다룬다.

### 3. 제보 생성 시 소유권 검증

- 제보 저장 시 사용자가 제출한 object key가 자기 prefix에 속하는지 다시 확인한다.
- `://`, `?`, `#`, 선행 `/`, `..` 같은 위험 패턴은 사전에 차단한다.

### 4. 업로드 제약

- 허용 content type은 이미지 형식으로 제한한다.
- 개수는 최대 5개, 용량은 10MB 이하로 강제한다.
- 조회 시에도 presigned GET URL을 내려 원본 버킷을 공개하지 않는다.

## 예외 처리와 운영 이슈 대응

### 업로드 남용 방지

- presigned URL 만료 시간을 짧게 두고, 저장 단계에서 key 소유권을 다시 확인해 임의 key 주입을 막는다.
- 파일 형식, 개수, 크기를 백엔드가 사전 검증해 스토리지 비용과 악성 업로드 가능성을 낮춘다.

### 백엔드 부하 절감

- 파일 업로드 스트림이 애플리케이션을 통과하지 않아 서버 메모리와 네트워크 사용량이 안정적이다.
- Nginx 업로드 한도와 결합해 너무 큰 요청이 앱까지 도달하지 않도록 했다.

### SDK 예외 표준화

- `S3ReportImagePresigner`는 SDK 예외를 도메인 예외로 감싸 응답 포맷과 에러 코드를 통일한다.

## 한계와 후속 과제

- presigned 업로드는 클라이언트가 업로드 성공 여부를 직접 관리해야 하므로 UX 보조가 중요하다.
- 바이러스 검사, 이미지 리사이징, 장기 미참조 파일 정리 정책은 별도 비동기 파이프라인으로 확장할 수 있다.
