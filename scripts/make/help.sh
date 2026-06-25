#!/usr/bin/env bash
set -euo pipefail

cat <<'EOF'
사용법: make <타깃>

Git/Jira
  init                         로컬 Git 설정과 hook을 한 번에 맞춘다
  test-git-jira                Git/Jira 자동화 규칙이 깨졌는지 확인한다

Docker Compose: local
  local-config                 local compose 설정을 렌더링한다
  local-up                     local 스택을 실행한다
  local-down                   local 스택을 중지한다
  local-logs                   local 스택 로그를 확인한다

Docker Compose: dev server
  dev-config                   dev compose 설정을 렌더링한다
  dev-up                       dev 스택을 실행한다
  dev-down                     dev 스택을 중지한다
  dev-logs                     dev 스택 로그를 확인한다

Docker Compose: prod server
  prod-config                  prod compose 설정을 렌더링한다
  prod-up                      prod backend/ai runtime을 실행한다
  prod-up-graphhopper          prod GraphHopper runtime까지 실행한다
  prod-graphhopper-bootstrap   prod schema/road CSV/accessibility CSV/graph-cache/runtime 최초 배포 절차를 순서대로 실행한다
  prod-schema-update           prod DB에 JPA 기반 road/segment_features schema를 생성하고 검증한다

Docker host scripts
  be-dev-config                백엔드 dev host 설정을 렌더링한다
  be-dev-up                    백엔드 dev host 서비스를 실행한다
  be-dev-down                  백엔드 dev host 서비스를 중지한다
  be-dev-logs                  백엔드 dev host 로그를 확인한다

Dev DB data
  road-network-dev-load        LOCAL nodes/segments CSV를 dev DB road 테이블에 적재한다
  road-network-prod-load       LOCAL nodes/segments CSV를 prod DB road 테이블에 적재한다
  accessibility-features-dev-load  LOCAL 접근성 CSV를 dev DB segment_features/road_segments에 반영한다
  accessibility-features-prod-load LOCAL 접근성 CSV를 prod DB에 dry-run report로 먼저 검증한다

GraphHopper
  graphhopper-dev-export-smoke dev DB export query와 validation sample을 빠르게 검증한다
  graphhopper-dev-profile-smoke dev graph-cache runtime에서 8개 접근성 profile route와 hard policy를 검증한다
  graphhopper-dev-up           dev graph-cache를 먼저 확인하고 GraphHopper runtime을 실행한다
  graphhopper-local-build      local DB에서 graph-cache를 생성한다
  graphhopper-dev-build        dev DB에서 graph-cache를 생성한다
  graphhopper-prod-build       prod DB에서 graph-cache를 생성한다

Partial local stacks
  be-local-up                  백엔드 local 서비스만 실행한다
  ai-local-up                  AI local 서비스만 실행한다

Ops
  portainer-tunnel             Portainer SSH 터널을 연다

Terraform: bootstrap
  terraform-bootstrap-init     bootstrap Terraform을 초기화한다
  terraform-bootstrap-fmt      bootstrap Terraform 포맷을 맞춘다
  terraform-bootstrap-validate bootstrap Terraform 설정을 검증한다
  terraform-bootstrap-plan     bootstrap Terraform 실행 계획을 확인한다

Terraform: prod
  terraform-prod-init          prod Terraform을 초기화한다
  terraform-prod-fmt           prod Terraform 포맷을 맞춘다
  terraform-prod-validate      prod Terraform 설정을 검증한다
  terraform-prod-plan          prod Terraform 실행 계획을 확인한다
EOF
