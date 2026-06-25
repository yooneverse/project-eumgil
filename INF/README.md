# INF

`INF`는 부산이음길 서비스의 인프라 운영 설정 자산을 모아두는 폴더다.

정식 설명 문서, 설계안, 회의용 문서는 `Docs/인프라`에서 관리한다. `INF`는 실제 운영에 가까운 설정 파일과 디렉토리 기준을 관리하는 용도로 사용한다.
현재 기준에서 `INF`는 AWS 구조, Jenkins 운영 설정, 모니터링 설정처럼 운영 관점에서 함께 봐야 하는 설정 자산을 관리한다.
Terraform, S1 Jenkins, S1 운영도구 compose/proxy 설정은 코드 기준으로 관리한다.

2026-05-20 실서버 확인 기준 최신 런타임 스냅샷은 `Docs/인프라/2026-05-20_인프라_현재상태_및_운영_기준.md`를 기준으로 한다.

## 결정 요약

- 루트 디렉토리는 실행 진입점으로 사용한다. (루트 디렉토리는 INF가 아닌 INF의 상위 디렉토리를 말한다.)
- `docker-compose.local.yml`, `docker-compose.dev.yml`, `docker-compose.prod.yml`는 루트에 둔다.
- `.env.local`, `.env.dev`, `.env.prod` 같은 환경 변수 파일도 루트에 둔다.
- `scripts/`는 `make`에서 호출하는 자동화 스크립트 폴더로 사용한다.
- `INF/`는 실행 스크립트가 아니라 운영 설정 자산을 관리한다.
- 모니터링은 `S1`의 `Grafana/PLG` 중심으로 관리한다.
- AWS 1차 알람 체계와 상세 운영 설명은 `Docs/인프라`에서 관리한다.

## 실행 원칙

- 루트 디렉토리는 실행 진입점으로 사용한다.
- 실제 실행에 바로 쓰이는 compose 파일은 가능한 한 루트에 둔다.
- 환경 변수 파일은 루트에서 한 번에 관리한다.
- `INF` 안에는 "샘플인지 실제 운영 파일인지"가 애매한 파일을 두지 않는다.
- 파일명만 보고 용도를 알 수 있게 환경명 또는 대상 시스템명을 명시한다.
- 설명 중심 문서는 `Docs/인프라`에 두고, `INF`에는 가능한 한 설정 자산만 둔다.
- 운영 절차가 바뀌면 `Docs/인프라` 문서와 `INF` 설정 파일을 함께 갱신한다.

## 디렉토리 구조

```text
INF/
|-- README.md
|-- aws/
|-- graphhopper/
|-- jenkins/
|-- monitoring/
`-- terraform/
```

## 폴더 역할
인프라와 관련된 설정파일은 모두 INF 폴더에 적재한다.
단, make를 위한 스크립트는 '/scripts' 폴더에서 관리한다.

### `aws/`

AWS 인프라 운영 설정 자산과 환경별 기준 파일을 둔다.

예시:

- 보안그룹 기준 파일
- Route53/Nginx 라우팅 관련 설정 참고 파일
- 환경별 운영 기준 메모
- Terraform 기준 설정

설계안, 다이어그램, runbook 같은 정식 문서는 `Docs/인프라`에서 관리한다.

### `jenkins/`

Jenkins 운영에 필요한 설정 파일을 둔다.

현재 기준:

- `jenkins/s1`: S1 Jenkins compose, Dockerfile, plugin 목록, nginx reverse proxy 기준 설정
- Jenkins 인증은 GitLab OAuth를 사용한다.
- Jenkins는 `https://jenkins.busaneumgil.com/` 루트 경로로 접근한다.

배포 흐름 설명 문서는 `Docs/인프라` 또는 `Docs/컨벤션`에서 관리한다.

### `graphhopper/`

GraphHopper runtime/build 이미지, 설정, custom model, overlay plugin 기준 파일을 둔다.

현재 prod GraphHopper는 `S2`의 `graphhopper-blue`/`graphhopper-green` runtime slot으로 실행하며, Redis의 `graphhopper:active-slot` 계약을 통해 backend가 active slot을 선택한다.

### `monitoring/`

모니터링 관련 설정 파일을 둔다.

현재 기준:

- 보조 모니터링은 `S1`의 `Grafana/PLG`를 사용한다.
- `Portainer`, `SonarQube`도 `S1` 운영도구 stack으로 둔다.
- `Portainer`는 외부 공개하지 않고 SSH 터널로만 접근한다.
- `S2` prod의 로그 수집은 `monitoring/s2` promtail 템플릿을 기준으로 한다.
- `Grafana/PLG`는 운영 보조 조회 용도이며, 핵심 장애 알람 체계를 대체하지 않는다.

예시:

- `Grafana/PLG` 관련 설정 파일
- `Portainer`, `SonarQube` 관련 설정 파일
- 대시보드/알람용 보조 설정
- `monitoring/s1`: S1 운영도구 compose, Grafana datasource, Prometheus/Loki/Promtail 설정
- `monitoring/s1/scripts/apply-sonarqube-oauth.sh`: SonarQube GitLab OAuth Settings API 적용 스크립트

운영 가이드와 상세 설명 문서는 `Docs/인프라`에서 관리한다.

## 경계 규칙

### 루트에 두는 것

- `docker-compose.local.yml`
- `docker-compose.dev.yml`
- `docker-compose.prod.yml`
- `.env.local`
- `.env.dev`
- `.env.prod`
- `Makefile`

### `scripts/`에 두는 것

- `make`에서 직접 호출하는 배포 스크립트
- 서버 시작/중지 스크립트
- QA 자동화 스크립트
- GIS 데이터 구축 스크립트

### `INF/`에 두는 것

- 운영 설정 파일
- 배포/모니터링 관련 설정 자산
- 시스템별 기준 파일

### `Docs/인프라`에 두는 것

- 인프라 설계안
- 배포 절차 문서
- 장애 대응 절차 문서
- 운영 가이드

## 현재 운영 기준

- 운영 기준은 EC2 2대 구조다.
- `S1`은 `dev/Jenkins/build runner + Grafana/Portainer/SonarQube/PLG` 역할을 가진다.
- `S2`는 `primary prod runtime` 역할을 가지며, backend/AI/admin/GraphHopper blue-green runtime을 실행한다.
- `RDS`, `ElastiCache`는 관리형 서비스로 운영한다.
- EC2 shell 접속은 `SSH`를 기본으로 하며, `22`는 관리자 고정 IP만 허용한다.
- `RDS`, `ElastiCache` 같은 private resource 접근은 `SSM Session Manager` port forwarding 기준으로 잡는다.
- `SSM Session Manager`는 SSH 장애 시 복구 채널로도 유지한다.
- 서비스 API와 필요한 관리자 UI는 각 서버 `Nginx`의 `80/443` host-based routing으로 접근한다.
- `Portainer`는 host-based routing에서 제외하고 SSH 터널로만 접근한다.
- EC2에서 외부에 직접 여는 포트는 `80/443`과 `22`만 두며, `22`는 관리자 고정 IP만 허용한다.
- `monitoring/`은 현재 `S1` 운영도구 자산 중심으로 정리한다.
- S1 dev backend의 `/health/graphhopper`는 2026-05-20 확인 시 `DOWN`이지만, S1 GraphHopper runtime 직접 `/healthcheck`는 정상이다. dev backend GraphHopper health 설정과 Redis slot 초기화는 후속 정렬 항목이다.

## 정렬 완료 기준

- 루트, `scripts/`, `INF/`, `Docs/인프라`의 책임이 서로 겹치지 않는다.
- 실행 파일은 루트 또는 `scripts/`에서 찾고, 설정 자산은 `INF/`에서 찾을 수 있다.
- 설명 문서는 `Docs/인프라`에서 찾을 수 있다.
- 현재 운영 구조와 폴더 구조 설명이 서로 모순되지 않는다.
