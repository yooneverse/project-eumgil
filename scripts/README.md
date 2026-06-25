# scripts 운영 기준

이 디렉터리는 저장소 루트에서 실행하는 보조 스크립트를 모아 둔다.
스크립트는 "어디서 실행되는가"와 "무엇을 운영하는가"를 기준으로 분리한다.

## 기본 원칙

- 모든 스크립트는 저장소 루트에서 실행되는 것을 기준으로 작성한다.
- 개발자가 직접 실행하는 명령은 가능하면 `Makefile` target으로 노출한다.
- 컨테이너 내부에서 실행되는 스크립트는 `entrypoints` 아래에 둔다.
- 운영 리소스를 직접 변경하는 스크립트는 실행 전에 dry-run 또는 config 확인 target을 먼저 둔다.
- 비밀값은 스크립트에 쓰지 않고 `.env.*`, AWS SSM, Secrets Manager 같은 외부 설정에서 읽는다.

## 디렉터리 구조

```text
scripts/
  init/                 # Git/Jira hook, 초기 개발 환경 설정
  test/                 # 저장소 규칙과 자동화 검증
  make/
    docker/             # Makefile docker target 구현체
    ops/                # 운영도구 접속용 Makefile target 구현체
    terraform/          # Makefile terraform target 구현체
    lib/                # make 스크립트가 공유하는 내부 함수
  docker/
    entrypoints/        # Dockerfile이 이미지 안으로 복사하는 컨테이너 내부 실행 스크립트
  aws/                  # AWS CLI, SSM, RDS, S3, ALB 관련 자동화
  deploy/               # 배포, Blue/Green 전환, smoke test
  maintenance/          # 백업, 로그 수집, 정리, 운영 점검
```

## Docker 디스크 자동관리

S1/S2 호스트의 루트 디스크 증가는 주로 Docker BuildKit cache, 오래된 image, stopped container, Jenkins workspace/archive에서 발생한다. 운영 서버에는 `scripts/maintenance/install-docker-disk-maintenance.sh`로 아래 자동관리 체계를 설치한다.

```bash
sudo bash scripts/maintenance/install-docker-disk-maintenance.sh
```

설치 항목:

- `/usr/local/sbin/e102-docker-disk-maintenance.sh`: 실제 정리 스크립트
- `/etc/e102-docker-disk-maintenance.env`: 보관 기간과 prune 범위
- `/etc/cron.d/e102-docker-disk-maintenance`: 3시간 주기 정리
- `/etc/logrotate.d/e102-docker-disk-maintenance`: 정리 로그 회전
- `/etc/docker/daemon.json`: Docker json-file log rotation 기본값

기본 정책:

- stopped container: 24시간 초과분 정리
- old image: 7일 초과 dangling image만 정리
- BuildKit cache: dangling 여부와 무관하게 24시간 초과분 정리, 8GB 보관 상한 적용
- Docker volume: 기본 정리하지 않음
- Jenkins workspace: 3일 초과분 정리
- Jenkins backup archive: 14일 초과분 정리

rollback용 tag image를 보존하기 위해 tagged image 전체 정리는 기본값에서 하지 않는다. 디스크 압박으로 수동 정리가 필요할 때만 `DOCKER_DISK_PRUNE_IMAGES_ALL=true`를 명시한다.

운영 배포와 GraphHopper refresh가 성공한 뒤에도 `pipeline` mode로 한 번 더 보수적인 정리를 수행한다. 수동 점검은 아래처럼 dry-run으로 먼저 확인한다.

```bash
DOCKER_DISK_MAINTENANCE_DRY_RUN=true bash scripts/maintenance/docker-disk-maintenance.sh report
DOCKER_DISK_MAINTENANCE_DRY_RUN=true bash scripts/maintenance/docker-disk-maintenance.sh scheduled
```

## Docker 스크립트 구분

`scripts/make/docker/`는 Makefile의 Docker 관련 target 구현체다.
예를 들어 `make local-up`은 `scripts/make/docker/local-up.sh`를 실행한다.

Make target과 스크립트 파일명은 1:1로 맞춘다.

`scripts/docker/entrypoints/`는 컨테이너 내부에서 실행되는 스크립트다.
Dockerfile의 `COPY` 대상이며, 개발자가 직접 실행하지 않는다.

`scripts/make/lib/`는 Make target 구현체가 공유하는 내부 함수만 둔다.
여기 있는 파일은 Makefile target과 직접 연결하지 않는다.

## 이름 규칙

- Make target 구현 스크립트 이름은 target 이름과 맞춘다.
  예: `local-up.sh`, `be-dev-up.sh`
- 엔트리포인트 스크립트는 컨테이너 역할을 드러낸다.
  예: `graphhopper.sh`
- 범용 이름은 피한다.
  예: `run.sh`, `start.sh`, `entrypoint.sh`

## Makefile 연동

개발자가 자주 쓰는 스크립트는 `Makefile`에 target을 둔다.
스크립트 경로는 Makefile 변수로 관리해 폴더 이동 시 수정 범위를 줄인다.

예:

```makefile
MAKE_DOCKER_SCRIPT_DIR := scripts/make/docker

local-up:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh
```

Terraform target도 같은 규칙을 따른다.
예를 들어 `make terraform-prod-plan`은 `scripts/make/terraform/terraform-prod-plan.sh`를 실행한다.

Terraform은 실제 AWS 리소스를 생성/변경할 수 있으므로 Makefile에는 `apply` target을 기본 노출하지 않는다.
`plan` 결과를 검토하고 승인한 뒤 환경 디렉터리에서 직접 `terraform apply`를 실행한다.

운영도구 중 외부에 직접 공개하지 않는 도구는 `scripts/make/ops/`에 접속 전용 target을 둔다.
예를 들어 `make portainer-tunnel`은 S1 서버로 SSH 터널을 열고 로컬 `http://localhost:19000`에서 Portainer에 접속하게 한다.

## 빈 디렉터리

아직 스크립트가 없는 운영 영역은 `.gitkeep`으로 유지한다.
스크립트가 추가되면 `.gitkeep`은 그대로 둬도 되지만, 해당 디렉터리에 실제 파일이 충분히 생기면 제거해도 된다.
