# =============================
# make 진입점
# =============================
#
# bash가 PATH에 있으면 그대로 쓰고,
# Windows에서는 Git 설치 경로에서 bash.exe를 찾아 쓴다.
BASH ?= bash
JIRA_PREFIX ?=
GIT_EXEC_PATH := $(subst \,/,$(shell git --exec-path))
MAKE_DOCKER_SCRIPT_DIR := scripts/make/docker
MAKE_HELP_SCRIPT := scripts/make/help.sh
MAKE_DB_SCRIPT_DIR := scripts/db
MAKE_OPS_SCRIPT_DIR := scripts/make/ops
MAKE_TERRAFORM_SCRIPT_DIR := scripts/make/terraform

ifeq ($(OS),Windows_NT)
BASH := $(patsubst %/mingw64/libexec/git-core,%/bin/bash.exe,$(GIT_EXEC_PATH))
endif

.PHONY: help init test-git-jira local-config local-up local-down local-logs dev-config dev-up dev-down dev-logs prod-config prod-up prod-up-graphhopper prod-graphhopper-bootstrap prod-schema-update be-local-up ai-local-up be-dev-config be-dev-up be-dev-down be-dev-logs road-network-dev-load road-network-prod-load accessibility-features-dev-load accessibility-features-prod-load graphhopper-dev-export-smoke graphhopper-dev-profile-smoke graphhopper-dev-up walk-route-dev-smoke route-end-rating-dev-smoke graphhopper-local-build graphhopper-dev-build graphhopper-prod-build portainer-tunnel terraform-bootstrap-init terraform-bootstrap-fmt terraform-bootstrap-validate terraform-bootstrap-plan terraform-prod-init terraform-prod-fmt terraform-prod-validate terraform-prod-plan

# 사용 가능한 make 타깃과 간단한 설명을 보여준다.
help:
	@"$(BASH)" $(MAKE_HELP_SCRIPT)

# Git/Jira 보조 스크립트
# 로컬 Git 설정과 hook을 한 번에 맞춘다.
init:
	@"$(BASH)" scripts/init/init-git-jira.sh "$(JIRA_PREFIX)"

# 자동화 규칙이 안 깨졌는지 빠르게 본다.
test-git-jira:
	@"$(BASH)" scripts/test/test-git-jira.sh

# Docker Compose: local
local-config local-up local-down local-logs:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

# Docker Compose: dev server
dev-config dev-up dev-down dev-logs:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

# Docker Compose: prod server
prod-config prod-up prod-up-graphhopper prod-graphhopper-bootstrap:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

prod-schema-update:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

# Docker host scripts
be-dev-config be-dev-up be-dev-down be-dev-logs:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

# Dev DB data load
road-network-dev-load:
	@"$(BASH)" $(MAKE_DB_SCRIPT_DIR)/load_road_network_dev.sh

road-network-prod-load:
	@"$(BASH)" $(MAKE_DB_SCRIPT_DIR)/load_road_network_prod.sh

accessibility-features-dev-load:
	@"$(BASH)" $(MAKE_DB_SCRIPT_DIR)/load_accessibility_features_dev.sh

accessibility-features-prod-load:
	@"$(BASH)" $(MAKE_DB_SCRIPT_DIR)/load_accessibility_features_prod.sh

# Partial local stacks
be-local-up ai-local-up:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

# GraphHopper graph-cache build jobs
graphhopper-dev-export-smoke:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

graphhopper-dev-profile-smoke:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

graphhopper-dev-up:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

walk-route-dev-smoke:
	@python3 scripts/smoke/dev_walk_route_search_smoke.py

route-end-rating-dev-smoke:
	@python3 scripts/smoke/dev_route_end_rating_smoke.py

graphhopper-local-build graphhopper-dev-build graphhopper-prod-build:
	@"$(BASH)" $(MAKE_DOCKER_SCRIPT_DIR)/$@.sh

# Ops access
portainer-tunnel:
	@"$(BASH)" $(MAKE_OPS_SCRIPT_DIR)/$@.sh

# Terraform: prod
terraform-bootstrap-init terraform-bootstrap-fmt terraform-bootstrap-validate terraform-bootstrap-plan:
	@"$(BASH)" $(MAKE_TERRAFORM_SCRIPT_DIR)/$@.sh

terraform-prod-init terraform-prod-fmt terraform-prod-validate terraform-prod-plan:
	@"$(BASH)" $(MAKE_TERRAFORM_SCRIPT_DIR)/$@.sh
