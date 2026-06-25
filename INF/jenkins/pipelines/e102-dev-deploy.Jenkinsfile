import groovy.json.JsonOutput

String resolveTriggerDescription(def build) {
  List causes = build.getBuildCauses()
  if (!causes) {
    return 'unknown'
  }

  def userCause = causes.find { (it._class ?: '').contains('UserIdCause') }
  if (userCause) {
    return userCause.userName ? "사용자 ${userCause.userName}" : (userCause.shortDescription ?: '사용자 수동 실행')
  }

  def scmCause = causes.find { (it._class ?: '').contains('SCMTriggerCause') }
  if (scmCause) {
    return 'SCM 변경'
  }

  def timerCause = causes.find { (it._class ?: '').contains('TimerTriggerCause') }
  if (timerCause) {
    return '스케줄 트리거'
  }

  return causes[0].shortDescription ?: causes[0]._class ?: 'unknown'
}

String safeValue(String value, String fallback = '-') {
  String normalized = value?.trim()
  return normalized ? normalized : fallback
}

void sendMattermost(def script, String message) {
  String webhookUrl = script.env.MATTERMOST_WEBHOOK_URL ?: System.getenv('MATTERMOST_WEBHOOK_URL') ?: ''
  if (!webhookUrl?.trim()) {
    script.echo 'MATTERMOST_WEBHOOK_URL is not set. Skipping Mattermost notification.'
    return
  }

  String payloadFile = ".mattermost-payload-${script.env.BUILD_NUMBER}.json"
  script.writeFile file: payloadFile, text: JsonOutput.toJson([text: message])
  script.sh """
    curl -fsS -X POST \\
      -H "Content-Type: application/json" \\
      --data @${payloadFile} \\
      "\$MATTERMOST_WEBHOOK_URL" >/dev/null 2>&1 || true
    rm -f ${payloadFile}
  """
}

pipeline {
  agent any

  options {
    disableConcurrentBuilds()
  }

  environment {
    REPO_URL = credentials('e102-repo-url')
    DEPLOY_BRANCH = 'develop'
    RUNTIME_STATE_DIR = '/opt/e102-server/runtime-state'
  }

  stages {
    stage('Checkout') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        git branch: 'develop', credentialsId: 'gitlab-pat', url: env.REPO_URL
        script {
          env.DEPLOY_COMMIT = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
        }
      }
    }

    stage('Notify Start') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
          String message = """\
            ----
            ##### :jenkins: DEV 배포가 시작되었습니다! :rocket:

            - **대상 파이프라인:** `e102-dev-deploy`
            - **브랜치:** `${safeValue(env.DEPLOY_BRANCH)}`
            - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
            - **트리거:** `${safeValue(resolveTriggerDescription(currentBuild))}`
            - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
            - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

            ----

            :gunpang_timer: 배포 진행 상황을 확인해주세요.
            ----
          """.stripIndent().trim()
          sendMattermost(this, message)
        }
      }
    }

    stage('Prepare Server Config') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        withCredentials([file(credentialsId: 'e102-dev-env-file', variable: 'E102_DEV_ENV')]) {
          sh '''
            cp "$E102_DEV_ENV" .env.dev
            cp /opt/e102-server/docker-compose.s1.override.yml docker-compose.s1.override.yml
            chmod 600 .env.dev
          '''
        }
      }
    }

    stage('Compose Config') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        sh 'docker compose --env-file .env.dev -f docker-compose.dev.yml -f docker-compose.s1.override.yml --profile graphhopper-build config --quiet'
      }
    }

    stage('Infra Up') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        sh 'docker compose --env-file .env.dev -f docker-compose.dev.yml -f docker-compose.s1.override.yml up -d --build postgres redis minio minio-init ai'
      }
    }

    stage('GraphHopper Cache') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        sh '''
          set -eu
          CACHE_VOLUME="s14p31e102-dev_graphhopper-dev-data"
          CACHE_FINGERPRINT="$({
            sha256sum INF/graphhopper/Dockerfile
            sha256sum INF/graphhopper/config-build.yml
            sha256sum INF/graphhopper/config-runtime.yml
            find INF/graphhopper/custom_models -type f | LC_ALL=C sort | while read -r file; do sha256sum "$file"; done
            find INF/graphhopper/plugin/src -type f | LC_ALL=C sort | while read -r file; do sha256sum "$file"; done
            sha256sum scripts/graphhopper/export_postgis_to_osm.py
          } | sha256sum | awk '{print $1}')"

          compose() {
            docker compose --env-file .env.dev -f docker-compose.dev.yml -f docker-compose.s1.override.yml "$@"
          }

          write_cache_metadata() {
            docker run --rm \
              -e CACHE_FINGERPRINT="$CACHE_FINGERPRINT" \
              -e CACHE_BUILT_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
              -v "$CACHE_VOLUME:/graphhopper/data" alpine:3.20 sh -ceu '
                printf "%s\n" "$CACHE_FINGERPRINT" > /graphhopper/data/.ieum-graphhopper-cache-fingerprint
                printf "%s\n" "$CACHE_BUILT_AT" > /graphhopper/data/.ieum-graphhopper-cache-built-at
              '
          }

          rebuild_graphhopper_cache() {
            compose --profile graphhopper-build build graphhopper-build
            compose --profile graphhopper-build run --rm \
              -e GRAPHHOPPER_CACHE_FINGERPRINT="$CACHE_FINGERPRINT" \
              graphhopper-build
            write_cache_metadata
          }

          wait_graphhopper_health() {
            for i in $(seq 1 24); do
              if docker run --rm --network s14p31e102-dev_default curlimages/curl:latest -fsS \
                http://graphhopper:8990/healthcheck >/dev/null 2>&1; then
                return 0
              fi
              sleep 5
            done
            return 1
          }

          compose build graphhopper

          if docker volume inspect "$CACHE_VOLUME" >/dev/null 2>&1 \
            && docker run --rm -e EXPECTED_FINGERPRINT="$CACHE_FINGERPRINT" -v "$CACHE_VOLUME:/graphhopper/data" alpine:3.20 sh -ceu '
              [ -s /graphhopper/data/.ieum-graphhopper-cache-fingerprint ]
              [ "$(cat /graphhopper/data/.ieum-graphhopper-cache-fingerprint)" = "$EXPECTED_FINGERPRINT" ]
              test -n "$(find /graphhopper/data -mindepth 1 -maxdepth 1 ! -name .ieum-graphhopper-cache-fingerprint ! -name .ieum-graphhopper-cache-built-at 2>/dev/null)"
          '; then
            echo "GraphHopper graph-cache fingerprint matches current image/config. Skipping rebuild."
          else
            rebuild_graphhopper_cache
          fi
          compose up -d graphhopper
          if ! wait_graphhopper_health; then
            echo "GraphHopper healthcheck failed after start. Rebuilding cache and recreating runtime once."
            compose logs --tail=120 graphhopper || true
            compose stop graphhopper >/dev/null 2>&1 || true
            rebuild_graphhopper_cache
            compose up -d --force-recreate graphhopper
            wait_graphhopper_health
          fi
        '''
      }
    }

    stage('Backend Deploy') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        sh 'docker compose --env-file .env.dev -f docker-compose.dev.yml -f docker-compose.s1.override.yml up -d --build --no-deps backend'
      }
    }

    stage('Smoke Test') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        sh '''
          for i in $(seq 1 24); do
            AI_HEALTH_RAW="$(docker run --rm --network s14p31e102-dev_default curlimages/curl:latest -sS -w '\n%{http_code}' \
              http://ai:5000/health || true)"
            AI_HEALTH_STATUS="$(printf '%s' "$AI_HEALTH_RAW" | tail -n 1)"
            AI_HEALTH_BODY="$(printf '%s' "$AI_HEALTH_RAW" | sed '$d')"
            AI_RESPONSE_RAW="$(docker run --rm --network s14p31e102-dev_default curlimages/curl:latest -sS -w '\n%{http_code}' \
              -H 'Content-Type: application/json' \
              -d '{}' \
              http://ai:5000/voice/analyze || true)"
            AI_STATUS="$(printf '%s' "$AI_RESPONSE_RAW" | tail -n 1)"
            AI_RESPONSE_BODY="$(printf '%s' "$AI_RESPONSE_RAW" | sed '$d')"
            docker run --rm --network s14p31e102-dev_default curlimages/curl:latest -fsS http://backend:8080/v3/api-docs >/tmp/e102-api-docs.json \
              && [ "$AI_HEALTH_STATUS" = "200" ] \
              && printf '%s' "$AI_HEALTH_BODY" | grep -Eq '"providers"[[:space:]]*:' \
              && printf '%s' "$AI_HEALTH_BODY" | grep -Eq '"POST /voice/analyze"' \
              && [ "$AI_STATUS" = "400" ] \
              && printf '%s' "$AI_RESPONSE_BODY" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"C4000"' \
              && printf '%s' "$AI_RESPONSE_BODY" | grep -Eq '"message"[[:space:]]*:' \
              && printf '%s' "$AI_RESPONSE_BODY" | grep -Eq '"data"[[:space:]]*:[[:space:]]*null' \
              && docker run --rm --network s14p31e102-dev_default curlimages/curl:latest -fsS http://graphhopper:8990/healthcheck >/tmp/e102-graphhopper-health.txt \
              && exit 0
            sleep 5
          done
          docker compose --env-file .env.dev -f docker-compose.dev.yml -f docker-compose.s1.override.yml logs --tail=120 backend ai graphhopper
          exit 1
        '''
      }
    }

    stage('Write Release Manifest') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        sh '''
          mkdir -p "$RUNTIME_STATE_DIR"
          python3 scripts/deploy/write-release-manifest.py \
            --output "$RUNTIME_STATE_DIR/dev-release.json" \
            --environment dev \
            --branch "$DEPLOY_BRANCH" \
            --commit "$DEPLOY_COMMIT" \
            --build-number "$BUILD_NUMBER" \
            --build-url "$BUILD_URL" \
            --services backend ai graphhopper minio redis postgres \
            --metadata source=jenkins \
            --metadata pipeline=e102-dev-deploy
        '''
      }
    }

    stage('Status') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        sh 'docker compose --env-file .env.dev -f docker-compose.dev.yml -f docker-compose.s1.override.yml ps'
      }
    }
  }

  post {
    always {
      script {
        deleteDir()
      }
    }
    success {
      script {
        String message = """\
          ----
          ##### ✅ DEV 배포가 완료되었습니다.

          - **대상 파이프라인:** `e102-dev-deploy`
          - **브랜치:** `${safeValue(env.DEPLOY_BRANCH)}`
          - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
          - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
          - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

          ----

          dev 배포가 정상 완료되었습니다!
          ----
        """.stripIndent().trim()
        sendMattermost(this, message)
      }
    }
    failure {
      script {
        String message = """\
          ----
          ##### ❌ DEV 배포가 실패했습니다.

          - **대상 파이프라인:** `e102-dev-deploy`
          - **브랜치:** `${safeValue(env.DEPLOY_BRANCH)}`
          - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
          - **실패 스테이지:** `${safeValue(env.LAST_STAGE_NAME, 'unknown')}`
          - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
          - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

          ----

          :please_call: 로그 확인이 필요합니다.
          ----
        """.stripIndent().trim()
        sendMattermost(this, message)
      }
    }
  }
}
