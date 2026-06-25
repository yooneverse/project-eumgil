# Jenkins 운영 기준

`S1` Jenkins는 dev 배포 자동화를 담당한다. Jenkins 자체 설정 파일과 secret은 서버 로컬에만 두고 저장소에는 올리지 않는다.

## 현재 구성

- 외부 URL: `https://jenkins.busaneumgil.com/`
- dev API URL: `https://api.dev.busaneumgil.com/`
- dev AI URL: `https://ai.dev.busaneumgil.com/`
- 인증: GitLab OAuth
- 권한: Matrix Authorization
- dev 배포 잡: `e102-dev-deploy`
- prod 배포 잡: `e102-prod-deploy`
- GraphHopper 자동 갱신 잡: `e102-graphhopper-refresh`
- monitoring 배포 잡: `e102-monitoring-deploy`
- 관측 브리프 잡: `e102-observability-hourly-brief`
- 대상 브랜치: `develop`
- dev 배포 대상: S1 dev Docker Compose stack
- prod 배포 대상: S2 prod Docker Compose stack

## 설정 자산

S1 Jenkins의 기준 설정은 `INF/jenkins/s1`에서 관리한다.

- `docker-compose.yml`: Jenkins와 S1 nginx proxy compose
- `Dockerfile`: Jenkins Docker CLI/Compose plugin 포함 이미지
- `plugins.txt`: Jenkins 필수 plugin 목록
- `nginx.conf`: Jenkins, dev API, Grafana/PLG, SonarQube, Portainer host-based routing
Jenkins container는 monitoring 조회와 release manifest 저장을 위해 `e102-ops` network와 `/home/ubuntu/e102/runtime-state -> /opt/e102-server/runtime-state` mount를 함께 사용한다.
GitLab OAuth Application에는 아래 Redirect URI가 등록되어 있어야 한다.

```text
https://jenkins.busaneumgil.com/securityRealm/finishLogin
```

Jenkins OAuth secret은 노션의 운영 env 기준을 확인한 뒤 로컬 루트 `.env.ops`에 작성하고, S1 `/home/ubuntu/e102/INF/jenkins/s1/.env.jenkins`에 반영한다. GitLab Application 생성 기준은 `Docs/인프라/2026-04-29_운영도구_secret_관리_기준.md`를 따른다.

Mattermost 배포 알림 webhook도 같은 흐름으로 관리한다.

```text
MATTERMOST_WEBHOOK_URL=<INTERNAL_CHAT_WEBHOOK_URL>
```

시간별 로그 분석 브리프는 dev/prod 분리 webhook을 사용한다.

```text
LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL=<INTERNAL_CHAT_WEBHOOK_URL>
DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL=<INTERNAL_DEV_CHAT_WEBHOOK_URL>
PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL=<INTERNAL_PROD_CHAT_WEBHOOK_URL>
```

Git repository URL is injected through the `e102-repo-url` Secret text credential in pipeline jobs, and through the `E102_REPO_URL` environment variable while init groovy creates jobs. Observability LLM gateway URL is injected through the `e102-llm-gateway-base-url` Secret text credential.

Jenkins container는 `.env.jenkins` 값을 환경변수로 읽고, init groovy가 `e102-s2-host`, `e102-s2-ssh-key`, `e102-mattermost-webhook-url` 같은 운영 보조 credential을 동기화한다. 배포용 `.env.dev`와 `.env.prod`는 Jenkins Secret file credential이 원본이며, host 파일 mount로 동기화하지 않는다.
시간별 observability brief는 `e102-dev-log-analysis-webhook-url`, `e102-prod-log-analysis-webhook-url` credential을 통해 `E102_로그분석채널`용 dev/prod 분리 webhook을 사용한다.

## 2026-05-20 반영 상태

- Jenkins는 `https://jenkins.busaneumgil.com/` 루트 경로로 접근한다.
- 과거 `/jenkins/` 경로는 루트로 redirect한다.
- dev backend는 `https://api.dev.busaneumgil.com/`로 접근한다.
- dev AI는 `https://ai.dev.busaneumgil.com/`로 접근한다.
- prod backend/AI/admin은 S2에서 `https://api.busaneumgil.com/`, `https://ai.busaneumgil.com/`, `https://admin.busaneumgil.com/`로 접근한다.
- `e102-prod-deploy`는 S2에 backend/AI/admin을 배포하고, 운영 기준으로 `DEPLOY_GRAPHHOPPER=true`를 기본값으로 둔다.
- `e102-graphhopper-refresh`는 3시간 주기로 S2 GraphHopper inactive slot을 갱신하며, 2026-05-20 확인 기준 최근 `jenkins-52`, `jenkins-53`, `jenkins-54` refresh report가 모두 `SUCCESS`다.
- S1 dev backend의 `/health/graphhopper`는 2026-05-20 확인 시 `DOWN`이지만, S1 GraphHopper runtime 직접 `/healthcheck`는 정상이다. dev backend GraphHopper health 설정과 Redis slot 초기화는 후속 정렬 항목이다.
- `api.dev.busaneumgil.com`, `ai.dev.busaneumgil.com` 인증서는 S1 host certbot standalone 방식으로 발급했고, S1 Docker nginx proxy에서 `/etc/letsencrypt`를 read-only mount해 사용한다.
- Docker nginx config는 bind mount이므로 `nginx.conf` 교체 후 `jenkins-proxy` 컨테이너를 recreate해야 한다.

```bash
cd /home/ubuntu/e102/jenkins
sudo docker compose up -d --force-recreate --no-deps jenkins-proxy
sudo docker exec e102-jenkins-proxy nginx -t
```

## Dev API Proxy

S1의 443 nginx proxy는 Jenkins와 dev backend, dev AI를 함께 라우팅한다.

- `https://jenkins.busaneumgil.com/`: Jenkins UI
- `https://api.dev.busaneumgil.com/`: S1 dev backend
- `https://ai.dev.busaneumgil.com/`: S1 dev AI

backend 원 포트는 dev Docker network 내부에서만 직접 접근하고, 외부에는 nginx 443 host-based routing으로만 공개한다. API 문서는 아래 주소로 확인한다.

```text
https://api.dev.busaneumgil.com/v3/api-docs
```

PostgreSQL은 HTTP reverse proxy 대상이 아니므로 `/db`로 열지 않는다. DB 접근이 필요하면 SSH tunnel 또는 SSM port forwarding을 사용한다.

## `e102-dev-deploy`

처리 순서:

1. GitLab `develop` checkout
2. Jenkins `e102-dev-env-file` credential을 `.env.dev`로 복사하고 S1 override compose 복사
3. `docker compose config --quiet`
4. `PostGIS`, `Redis`, `MinIO`, `AI` 기동
5. GraphHopper graph-cache volume 확인
6. cache가 비어 있으면 `graphhopper-build` profile로 PostgreSQL LineString 기반 cache 생성
7. GraphHopper runtime 기동
8. backend image build 및 컨테이너 재생성
9. AI `/health` payload, AI `/voice/analyze` invalid-request schema, backend `/v3/api-docs`, GraphHopper `/healthcheck` smoke test
10. compose 상태 출력

GraphHopper는 S1 dev stack에 포함한다. runtime은 graph-cache serve only 구조이며, Jenkins dev pipeline은 cache가 비어 있거나 fingerprint가 바뀌었을 때 build job을 실행한다. GraphHopper 기동 후 `/healthcheck`가 실패하면 cache rebuild와 runtime `--force-recreate`를 1회 자동 수행해 dev 환경의 꺼진 엔진을 복구한다.

Mattermost 알림:

- 시작: 발송
- 성공: 발송
- 실패: 발송

## Secrets

Jenkins job에서 사용하는 secret은 Jenkins Credentials를 source of truth로 둔다.

- 배포용 `.env.dev`, `.env.prod`는 Jenkins Secret file credential이다.
- S2 SSH key, Mattermost webhook처럼 Jenkins가 직접 참조해야 하는 값도 Jenkins Credentials로 관리한다.

| Credential ID | 종류 | 용도 | 상태 |
|---|---|---|---|
| `gitlab-pat` | Username/Password 또는 Secret text | GitLab repository checkout | 적용 완료 |
| `e102-dev-env-file` | Secret file | S1 dev 배포 env 파일 | 적용 완료 |
| `e102-prod-env-file` | Secret file | S2 prod 배포 env 파일 | 적용 완료 |
| `e102-s2-host` | Secret text | S2 SSH host 또는 IP. `E102_S2_HOST` 또는 `/home/ubuntu/e102/prod-secrets/e102-s2-host`에서 bootstrap | 적용 완료 |
| `e102-s2-ssh-key` | SSH Username with private key | S2 배포 SSH 접속 | 적용 완료 |
| `e102-mattermost-webhook-url` | Secret text | Jenkins/MM 배포 알림 webhook | 적용 완료 |
| `e102-log-analysis-webhook-url` | Secret text | Jenkins/MM 공통 로그분석 webhook fallback. dev/prod 전용 credential이 있으면 없어도 됨 | 선택 |
| `e102-dev-log-analysis-webhook-url` | Secret text | Jenkins/MM DEV 로그분석 채널 webhook | 적용 예정 |
| `e102-prod-log-analysis-webhook-url` | Secret text | Jenkins/MM PROD 로그분석 채널 webhook | 적용 예정 |

`e102-dev-env-file`과 `e102-prod-env-file`은 Jenkins UI/API에서 Secret file credential로 직접 교체한다. 이 두 credential이 배포 env의 source of truth다. 값을 바꿀 때는 서버에 SSH로 접속해 host `.env` 파일을 수정하지 말고, Jenkins credential 파일을 새 버전으로 교체한 뒤 해당 배포 job을 실행한다.

S1 `/home/ubuntu/e102/prod-secrets` 하위는 S2 SSH key 같은 Jenkins bootstrap 보조 파일만 보관한다. Jenkins 컨테이너 재시작 시 `prod-deploy-credentials.groovy`는 `e102-s2-host`, `e102-s2-ssh-key`, `e102-mattermost-webhook-url`만 동기화하고, `e102-dev-env-file`/`e102-prod-env-file`은 덮어쓰지 않는다.

S2 host는 환경변수 없이 파일로도 bootstrap할 수 있다.

```bash
sudo install -m 644 -o root -g root /dev/null /home/ubuntu/e102/prod-secrets/e102-s2-host
echo '43.201.198.214' | sudo tee /home/ubuntu/e102/prod-secrets/e102-s2-host >/dev/null
```

필요하면 SSH username도 `/home/ubuntu/e102/prod-secrets/e102-s2-user` 파일로 둘 수 있다. 파일이 없으면 기본 username은 `ubuntu`다. S2 host/user 파일은 Jenkins 컨테이너의 `jenkins` 사용자가 읽어야 하므로 `600 root:root`로 두지 않는다.

현재 prod는 초기 환경 bootstrap 단계이므로 `.env.prod`의 `JPA_DDL_AUTO`를 `update`로 두고 테이블/컬럼을 먼저 생성한다. 운영 모드로 전환하기 전에는 반드시 `.env.prod` 값을 `validate`로 되돌리고 한 번 더 배포해 schema drift를 차단한다.

## `e102-prod-deploy`

prod 배포 pipeline 기준 파일은 `INF/jenkins/pipelines/e102-prod-deploy.Jenkinsfile`이다. Jenkins job 정의는 inline script를 저장하지 않고 GitLab SCM에서 이 파일을 직접 읽는다.

처리 순서:

1. 지정 브랜치 checkout
2. workspace를 archive로 패키징
3. S2 `/home/ubuntu/e102/prod`로 코드와 `.env.prod` 업로드
4. `scripts/deploy/prod-deploy.sh` 또는 `scripts/deploy/prod-rollback.sh` 실행
5. `scripts/deploy/prod-smoke.sh`로 backend/AI/GraphHopper 상태 확인
6. remote deploy state를 읽어 `/opt/e102-server/runtime-state/prod-release.json` 갱신

운영 주의:

- `.env.prod`는 기존 파일을 직접 overwrite하지 않고 `.env.prod.upload`로 먼저 올린 뒤 서버에서 rename한다. 기존 파일 소유권이 root로 꼬여 있어도 directory write 권한만 있으면 교체 가능하게 하기 위함이다.
- workspace archive를 푼 직후 `chmod +x scripts/deploy/*.sh`를 적용한다. 현재 기준의 방어막은 내부 호출을 `bash scripts/deploy/*.sh` 형태로 고친 것이지만, master에 남아 있는 과거 스크립트가 직접 실행을 시도해도 같은 권한 오류를 한 번 더 막기 위함이다.
- Jenkins boolean parameter는 raw `sh` 내부에서 빈 문자열로 들어갈 수 있으므로, `params.*.toString()` 값으로 원격 명령을 조합한다.
- 현재 bootstrap 단계에서는 `.env.prod`의 `JPA_DDL_AUTO=update`를 기준으로 schema를 맞춘다. 사용자 유입 전 bootstrap이 끝나면 `JPA_DDL_AUTO=validate`로 복귀한 뒤 다시 배포해 운영 모드로 고정한다.

파라미터:

| 파라미터 | 기본값 | 설명 |
|---|---:|---|
| `DEPLOY_BRANCH` | `master` | S2 prod에 배포할 브랜치 |
| `BUILD_GRAPHHOPPER` | `false` | PostgreSQL LineString에서 graph-cache를 새로 생성 |
| `DEPLOY_GRAPHHOPPER` | `true` | GraphHopper runtime까지 기동 |
| `ROLLBACK` | `false` | 이전 app image tag와 이전 graph-cache로 rollback |

현재 운영에서는 경로 추천 기능이 GraphHopper를 기본 의존성으로 사용하므로 `DEPLOY_GRAPHHOPPER=true`를 기본값으로 둔다. 일반 애플리케이션 배포에서 graph-cache를 매번 새로 만드는 것은 아니므로 `BUILD_GRAPHHOPPER`만 선택적으로 켠다.

`BUILD_GRAPHHOPPER=true`는 기존 단일 graph-cache publish가 아니라 `scripts/graphhopper/prod-bluegreen-refresh.sh`를 실행한다. 운영 주기 갱신은 아래 `e102-graphhopper-refresh` 잡이 담당하므로, 일반 애플리케이션 배포에서 매번 켤 필요는 없다.

Mattermost 알림:

- 시작: 발송
- 성공: 발송
- 실패: 발송

메시지 포맷은 기존 GitLab/MR 알림 톤을 따라 Markdown block 형태로 맞춘다.

## `e102-graphhopper-refresh`

prod GraphHopper runtime은 blue/green slot으로 운영한다.

```text
backend
  -> Redis graphhopper:active-slot 조회
  -> graphhopper-blue 또는 graphhopper-green 호출
```

Jenkins `e102-graphhopper-refresh`는 3시간마다 실행된다.

처리 순서:

1. 지정 브랜치 checkout
2. workspace와 `.env.prod`를 S2 `/home/ubuntu/e102/prod`로 업로드
3. `scripts/graphhopper/prod-bluegreen-refresh.sh` 실행
4. 현재 active slot health를 확인하고, 꺼져 있으면 start/restart로 self-heal
5. active 복구가 실패하고 previous slot이 건강하면 Redis active를 previous로 임시 failover
6. blue/green slot은 유지한 채 임시 `graphhopper-candidate` volume에 OSM/PBF/graph-cache 생성
7. 임시 candidate GraphHopper runtime 기동
8. 임시 candidate `/healthcheck`와 8개 profile route smoke 실행
9. publish 직전 Redis previous slot URL을 임시 candidate runtime으로 돌려 fallback 공백을 줄임
10. 대상 blue/green slot에 cache를 복사하고 target slot health/profile smoke 실행
11. smoke 통과 시 Redis active slot 전환 및 active-slot 검증
12. 실패 시 기존 active slot 유지, switch 이후 실패면 previous slot으로 rollback 검증
13. refresh report JSON과 container 상태 출력

Redis key 계약:

| Key | 값 |
|---|---|
| `graphhopper:active-slot` | `blue` 또는 `green` |
| `graphhopper:previous-slot` | fallback 대상 slot |
| `graphhopper:active-build-id` | 마지막 성공 build id |
| `graphhopper:blue:url` | `http://graphhopper-blue:8989` |
| `graphhopper:green:url` | `http://graphhopper-green:8989` |

운영 원칙:

- Jenkins는 3시간 cron, SSH 실행, report/알림만 담당한다.
- GraphHopper 갱신 상태 전이는 S2의 `prod-bluegreen-refresh.sh`가 담당한다.
- active slot이 이미 내려가 있으면 refresh 전에 해당 slot을 먼저 start/restart한다.
- active self-heal이 실패해도 previous slot이 정상이면 previous로 failover한 뒤 candidate rebuild를 진행한다.
- candidate import나 smoke가 실패하면 Redis active slot은 바꾸지 않는다.
- publish 전 target slot cache를 snapshot하고, target slot 검증 전 publish 단계가 실패하면 snapshot restore 후 Redis previous fallback을 원복한다.
- GraphHopper overlay reload admin endpoint `/ieum/admin/**`는 BE 내부 호출 전용이다. S1 nginx는 `/api/ieum/admin/**`, `api.dev.busaneumgil.com/ieum/admin/**`를 모두 `404`로 차단하고, 외부 LB/ALB에도 동일 정책을 유지한다.
- 운영 smoke / 배포 checklist에는 `curl -i https://api.dev.busaneumgil.com/ieum/admin/overrides/reload`와 `curl -i https://<INTERNAL_S1_HOST>/api/ieum/admin/overrides/reload`가 외부에서 차단되는지 확인 절차를 포함한다.
- target restore 또는 Redis 원복이 실패할 때만 임시 candidate runtime을 previous fallback으로 남겨 active slot 장애 시 fallback을 유지한다.
- rollback Redis write 후에는 active slot을 다시 읽어 rollback 성공 여부를 검증한다.
- 전환 후 backend smoke는 기본적으로 `/health/graphhopper`를 호출해 Redis active slot 기준 GraphHopper 연결을 확인하고, 실패하면 active slot을 previous로 되돌린다.
- Mattermost 실패 알림은 Jenkins failure post action이 발송한다.
- Mattermost 성공 알림에도 refresh warning이 있으면 함께 노출한다.
- 공개 `/graphhopper/healthcheck`는 Redis active slot 기준 backend `/health/graphhopper`를 보고, 슬롯별 raw health는 `/graphhopper-blue/healthcheck`, `/graphhopper-green/healthcheck`로 확인한다.

## `e102-monitoring-deploy`

S1 monitoring/Grafana/Prometheus/nginx 설정은 prod 앱 배포와 별도 경로로 반영한다.

처리 순서:

1. 지정 브랜치 checkout
2. `scripts/deploy/s1-monitoring-sync.sh` 실행
3. `INF/monitoring/s1/**`를 `/home/ubuntu/e102/ops`로 동기화
4. `INF/jenkins/s1/nginx.conf`를 `/home/ubuntu/e102/jenkins/nginx.conf`로 동기화
5. monitoring stack 재적용
6. nginx 설정이 바뀐 경우 `e102-jenkins-proxy` 재시작

운영 원칙:

- prod backend에 새 `/health` endpoint가 배포돼도, S1 monitoring이 옛 probe를 보고 있으면 false DOWN이 날 수 있다.
- 따라서 `INF/monitoring/**` 또는 `INF/jenkins/s1/nginx.conf` 변경은 `e102-monitoring-deploy`를 같이 태우는 것을 기본 절차로 본다.
- sync 스크립트는 `s14p31e102-dev_default` 네트워크가 아직 없으면 bootstrap network를 먼저 만든다.

## `e102-observability-hourly-brief`

dev/prod warning/error와 health를 1시간 단위로 요약해 Mattermost에 보고하는 Jenkins 잡이다.

처리 순서:

1. `master` checkout
2. `origin/develop`, `origin/master` fetch
3. `scripts/monitoring/hourly_observability_brief.py` 실행
4. `reports/observability/hourly-brief.json` 아카이브
5. dev/prod 분리 Mattermost webhook으로 메시지 전송

수집 데이터:

- Loki warning/error 집계
- Prometheus blackbox health
- `/opt/e102-server/runtime-state/dev-release.json`
- `/opt/e102-server/runtime-state/prod-release.json`
- local git commit history
- optional GitLab MR metadata
- optional LLM summary

기본 LLM 요약 경로:

- source secret: Jenkins `e102-prod-env-file` 안의 `GMS_KEY`
- provider: Anthropic Messages API via `<INTERNAL_LLM_GATEWAY_BASE_URL>/v1/messages`
- model: `claude-opus-4-5-20251101`
- fallback: `GMS_KEY`가 없거나 호출 실패 시 deterministic summary만 전송

운영 원칙:

- 스케줄은 매시 1회다.
- GitLab token이 없으면 merged MR은 생략하고 local commit 기준으로 보강한다.
- LLM API key가 없거나 호출이 실패해도 deterministic fallback으로 계속 보고한다.
- report JSON은 Jenkins artifact로 남겨 장애 시점 비교 근거로 사용한다.

## Webhook

Jenkins job에는 GitLab Push Hook 수신 트리거가 설정되어 있다.

- Jenkins endpoint: `https://jenkins.busaneumgil.com/project/e102-dev-deploy`
- 이벤트: Push events
- 브랜치 필터: `develop`
- Secret token: S1 서버 로컬 `/home/ubuntu/e102/.jenkins-webhook-secret`

GitLab 프로젝트 webhook 등록은 Maintainer 또는 Owner 권한이 필요하다. 현재 Jenkins 수신 endpoint는 실제 GitLab Push Hook 형태의 payload로 빌드 생성 및 `SUCCESS`까지 검증했다.

## Poll SCM

초기 운영에서는 GitLab webhook 권한 의존도를 줄이기 위해 Poll SCM을 함께 사용한다.

- 스케줄: `H/30 * * * *`
- 의미: 30분마다 `develop` 변경 여부 확인
- 동작: 변경이 있을 때만 `e102-dev-deploy` 빌드 실행
- 용도: GitLab webhook 등록 전까지의 기본 dev 자동 배포 경로

마감에 가까워져 GitLab Maintainer 권한과 webhook 설정이 정리되면 `develop` Push Hook 중심으로 전환한다.

## Backup

Jenkins home volume은 S1 로컬에서 매일 백업한다.

- 스크립트: `/home/ubuntu/e102/jenkins-backups/bin/jenkins-backup.sh`
- 백업 위치: `/home/ubuntu/e102/jenkins-backups/archives`
- 스케줄: `/etc/cron.d/e102-jenkins-backup`
- 실행 시간: UTC 18:30, KST 03:30
- 보관 기간: 14일

백업 archive에는 Jenkins credential과 secret material이 포함될 수 있으므로 root 전용 권한으로 관리한다.

## Disk Maintenance

S1 Jenkins와 S2 prod는 Docker build cache와 image layer가 빠르게 누적될 수 있으므로 호스트 단위 자동 정리를 둔다.

- 설치 스크립트: `scripts/maintenance/install-docker-disk-maintenance.sh`
- 실행 스크립트: `/usr/local/sbin/e102-docker-disk-maintenance.sh`
- 설정 파일: `/etc/e102-docker-disk-maintenance.env`
- 스케줄: `/etc/cron.d/e102-docker-disk-maintenance`, 기본 3시간마다
- 로그: `/var/log/e102-docker-disk-maintenance.log`

정리 대상은 stopped container, 오래된 dangling image, BuildKit cache, Jenkins workspace/archive다. Docker volume은 graph-cache와 DB data를 보존하기 위해 기본값에서 정리하지 않는다. rollback용 tag image를 보존하기 위해 tagged image 전체 정리는 `DOCKER_DISK_PRUNE_IMAGES_ALL=true`를 명시한 수동 정리에서만 사용한다.

prod deploy와 GraphHopper refresh는 성공 후 `pipeline` mode로 정리 스크립트를 한 번 더 실행한다. 정리 실패는 이미 성공한 배포/refresh를 실패로 뒤집지 않고 경고 로그만 남긴다.

Docker container log rotation은 `/etc/docker/daemon.json`에 `json-file` `max-size=50m`, `max-file=3` 기본값을 기록한다. 이 설정은 Docker daemon 재시작 후 새로 만들어지는 container부터 적용된다.
