# ADR: Flyway 기반 DB migration 도입

## 논의 배경

BE는 Spring Boot, Spring Data JPA, Hibernate, PostgreSQL/PostGIS 기반으로 동작한다. 현재 환경은 dev와 prod로 분리되어 있으며, dev DB는 S1 서버의 Docker Compose PostGIS 컨테이너에 존재하고 prod DB는 AWS RDS PostgreSQL/PostGIS에 존재한다.

현재 BE 설정은 환경별로 Hibernate schema action이 다르다.

- `local`: `spring.jpa.hibernate.ddl-auto=none`
- `dev`: `spring.jpa.hibernate.ddl-auto=update`
- `prod`: `spring.jpa.hibernate.ddl-auto=validate`

이미 `users`, `road_nodes`, `road_segments`, `favorite_routes` 등 엔티티 기반 테이블이 존재하므로 Flyway는 "처음부터 빈 DB에 도입"하는 상황이 아니라 "이미 존재하는 dev/prod DB를 migration 이력 체계에 편입"하는 상황이다.

이 상태에서 `ddl-auto=update`에 계속 의존하면 DB 변경 이력이 코드 리뷰 대상에 명시적으로 남지 않고, dev/prod DB가 서로 다른 경로로 변화할 수 있다. 특히 운영 RDS는 데이터 보존과 배포 안정성이 중요하므로 Hibernate가 부팅 중 암묵적으로 스키마를 변경하는 방식을 피해야 한다.

엔티티와 DB가 맞지 않는다는 말은 Java 엔티티의 `@Table`, `@Column`, 관계 매핑, 타입, nullable, 길이, 제약조건 기대값과 실제 DB의 테이블/컬럼/제약조건/타입이 다른 상태를 의미한다.

Flyway는 엔티티와 DB 구조가 같은지 직접 검증하는 도구가 아니다. Flyway는 migration 파일의 적용 이력, 순서, checksum, SQL 실행 성공 여부를 관리한다. 엔티티와 실제 DB 구조의 호환성 검증은 Hibernate `ddl-auto=validate`가 담당한다. 따라서 목표 흐름은 "Flyway로 DB를 명시적 버전까지 올린 뒤, Hibernate validate로 현재 엔티티와 DB 구조의 큰 불일치를 감지"하는 것이다.

## 선택지

1. Hibernate `ddl-auto=update` 유지

   dev에서는 편하지만 DB 변경 SQL이 명시적으로 남지 않는다. 운영과 dev의 스키마 진화 경로가 달라질 수 있고, 컬럼 rename, drop, 제약조건 변경, PostGIS 확장, 인덱스 변경 같은 운영 DB 변경을 안전하게 다루기 어렵다.

2. Flyway 도입 없이 수동 SQL 운영

   운영자가 직접 SQL을 실행하면 단기적으로는 단순하지만 어떤 변경이 어느 환경에 언제 적용되었는지 애플리케이션 코드와 함께 추적하기 어렵다. 신규 DB 재현성도 약하다.

3. Flyway를 중간 도입하고 Hibernate는 `validate`로 고정

   현재 존재하는 dev/prod DB를 baseline으로 편입하고, 이후 모든 스키마 변경을 `BE/src/main/resources/db/migration`의 versioned SQL로 관리한다. 앱은 migration 이후 Hibernate `validate`로 엔티티-DB 매핑 불일치를 감지한다.

4. Liquibase 도입

   XML/YAML/JSON/changelog 기반으로 더 세밀한 변경 관리가 가능하지만 현재 팀 규모와 Spring Boot/PostgreSQL SQL 중심 운영에는 Flyway가 더 단순하다. SQL을 직접 리뷰하고 운영 DB에 적용할 수 있는 장점도 Flyway 쪽이 명확하다.

## 결정 사항

- BE DB schema migration 도구로 Flyway를 도입한다.
- Flyway versioned SQL을 DB 스키마 변경의 source of truth로 둔다.
- Hibernate `ddl-auto=update`는 사용하지 않고, local/dev/prod 모두 최종적으로 `ddl-auto=validate`를 기본값으로 맞춘다.
- 기존 dev/prod DB는 삭제 후 재생성하지 않고 baseline 및 보정 migration으로 Flyway 관리 체계에 편입한다.
- 빈 DB 재현성을 위해 현재 기준 스키마를 나타내는 `V1__baseline_schema.sql`을 작성한다.
- prod RDS는 앱 부팅 중 무조건 자동 migration하는 방식보다 Jenkins 또는 배포 파이프라인의 명시적 migration 단계에서 먼저 적용하는 방식을 우선한다.
- dev DB는 S1 Docker 컨테이너 DB이므로 필요 시 재생성이 가능하지만, 공유 dev 환경에서는 prod와 동일하게 migration 이력 기반으로 관리한다.
- Flyway migration 이후 Hibernate `validate`를 실행해 현재 엔티티 매핑과 실제 DB 구조의 주요 불일치를 감지한다.

## 결정 이유

Flyway는 기존 DB가 존재하는 상황에서도 사용할 수 있다. 단, 현재 DB를 무시하고 모든 테이블을 drop/create하는 방식으로 도입하지 않는다. 기존 DB를 기준 버전으로 인정하거나, 현재 DB를 목표 스키마로 보정한 뒤 baseline을 기록하는 방식으로 편입한다.

현재 프로젝트는 dev DB와 prod DB가 물리적으로 다르다. dev는 S1의 Docker PostGIS 컨테이너이고, prod는 RDS이다. Hibernate `ddl-auto=update`를 dev에서 계속 쓰면 dev DB가 코드 실행 시 암묵적으로 변경될 수 있고, prod RDS는 `validate`만 수행하므로 두 환경의 schema drift가 누적될 수 있다.

Flyway를 사용하면 DB 변경이 다음 형태로 Git에 남는다.

```text
BE/src/main/resources/db/migration/V1__baseline_schema.sql
BE/src/main/resources/db/migration/V2__add_route_sessions.sql
BE/src/main/resources/db/migration/V3__add_road_segment_indexes.sql
```

각 migration은 한 번 성공하면 DB의 `flyway_schema_history`에 기록되며 같은 versioned migration은 다시 실행되지 않는다. 서버를 재시작할 때마다 전체 SQL을 반복 실행하는 구조가 아니라, 아직 적용되지 않은 새 버전만 실행하는 구조다.

또한 빈 DB를 새로 만들 때는 `V1`부터 순서대로 적용해 현재 schema를 재현할 수 있다. 기존 DB는 baseline 처리 후 이후 변경분만 적용한다.

Flyway와 Hibernate `validate`는 검증 범위가 다르다.

- Flyway: migration 이력, 순서, checksum, SQL 적용 성공 여부를 검증한다.
- Hibernate `validate`: 현재 엔티티가 기대하는 테이블/컬럼/타입이 실제 DB에 존재하고 호환되는지 검증한다.

따라서 엔티티에 새 필드를 추가했는데 migration SQL을 빼먹으면 Flyway는 "적용할 새 migration 없음"으로 통과할 수 있지만, 이후 Hibernate `validate`가 해당 컬럼 부재를 감지해 서버 부팅을 실패시킬 수 있다. 이 조합이 현재 BE 운영 방식에 적합하다.

## 트레이드오프

Flyway 도입 후에는 엔티티만 수정해서 DB가 자동 변경되기를 기대할 수 없다. 컬럼 추가, 타입 변경, 인덱스 추가, 제약조건 변경, 테이블 추가는 반드시 migration SQL과 함께 변경해야 한다.

초기 도입 시 dev/prod DB의 실제 스키마를 조사하고, 엔티티 및 ERD와 다른 부분을 정리해야 한다. 특히 prod RDS는 데이터 보존이 필요하므로 drop/create 방식으로 맞추면 안 된다.

`V1__baseline_schema.sql`이 현재 목표 스키마를 정확히 표현하지 못하면 빈 DB 재현성이 깨진다. 반대로 기존 prod DB와 `V1`이 다르면 baseline 전에 보정 SQL 또는 수동 검증이 필요하다.

PostGIS를 사용하므로 `CREATE EXTENSION IF NOT EXISTS postgis;` 처리 위치를 정해야 한다. 앱 DB 계정에 extension 생성 권한이 없을 수 있으므로 prod RDS에서는 인프라 초기화 또는 DBA/운영자 작업으로 분리될 수 있다.

운영 RDS migration은 잘못된 DDL이 데이터 손실이나 장시간 lock으로 이어질 수 있다. 운영 migration은 사전 백업, lock 영향 검토, rollback 또는 forward-fix 전략을 포함해야 한다.

## 적용 규칙

- `BE/src/main/resources/db/migration` 아래의 Flyway versioned SQL만 DB schema 변경 경로로 인정한다.
- 이미 적용된 `Vn__*.sql` 파일은 수정하지 않는다. 수정이 필요하면 새 버전 migration을 추가한다.
- `spring.jpa.hibernate.ddl-auto=update`는 사용하지 않는다.
- `spring.jpa.hibernate.ddl-auto=validate`를 기본값으로 사용해 Flyway 적용 이후 엔티티-DB 매핑 불일치를 부팅 시점에 감지한다.
- 기존 prod RDS의 테이블은 drop/create로 맞추지 않는다. 필요한 변경은 `ALTER TABLE`, `CREATE INDEX`, `ADD CONSTRAINT`, 백필 SQL처럼 데이터 보존 가능한 migration으로 작성한다.
- dev Docker DB는 데이터 보존 필요가 낮은 경우 재생성할 수 있지만, 공유 dev 서버에서는 prod와 같은 migration 이력 체계를 따른다.
- 기존 dev/prod DB에 Flyway를 처음 도입할 때는 schema inventory를 먼저 수행하고, 현재 DB가 baseline으로 인정 가능한 상태인지 확인한다.
- 빈 DB 재현을 위해 `V1__baseline_schema.sql`은 현재 목표 스키마를 생성할 수 있어야 한다.
- 기존 DB가 `V1`과 다르면 baseline 전에 보정하거나, 조건부 보정 migration을 추가한다.
- 운영 migration은 배포 전 Jenkins 또는 별도 migration job에서 먼저 실행하고, 성공 후 BE 앱을 배포한다.
- prod 앱은 migration 이후 Hibernate `validate` 실패 시 정상 기동하지 않아야 한다.
- 여러 서버가 동시에 prod RDS에 migration을 시도하지 않도록 배포 파이프라인에서 migration 단계를 단일 실행으로 제한한다.
- 컬럼 삭제, nullable 강화, 타입 축소처럼 호환성이 깨질 수 있는 변경은 한 번의 배포에 처리하지 않고 expand-contract 방식으로 나눈다.
- PostGIS extension 생성은 prod 권한을 확인한 뒤 Flyway migration에 둘지, 인프라 초기화 작업으로 둘지 결정한다.

## 후속 작업

- dev DB와 prod RDS의 실제 schema dump를 확보하고 BE 엔티티 및 ERD와 차이를 비교한다.
- prod RDS를 기준으로 현재 운영 스키마와 목표 스키마를 확정한다.
- `BE/build.gradle`에 Flyway 의존성을 추가한다.
- `BE/src/main/resources/db/migration/V1__baseline_schema.sql`을 작성한다.
- 기존 dev/prod DB에 대한 baseline 적용 절차를 정한다.
- 기존 DB와 `V1` 사이에 차이가 있으면 데이터 보존형 보정 migration을 작성한다.
- `application-local.yml`, `application-dev.yml`, `application-prod.yml`의 JPA/Flyway 설정을 정리한다.
- Jenkins dev/prod 배포 파이프라인에 Flyway migration 실행 단계를 추가한다.
- prod RDS migration 전 백업, 실패 시 대응, lock 영향 확인 절차를 runbook으로 문서화한다.
- PostGIS extension 생성 권한과 적용 위치를 확인한다.
- migration 누락을 잡기 위해 BE 테스트 또는 CI에서 `ddl-auto=validate` 기반 부팅 검증을 추가한다.
