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

String formatBuildDuration(def build) {
  long durationMillis = build.duration ?: 0L
  long totalSeconds = Math.max(0L, Math.round(durationMillis / 1000.0d))
  long minutes = (long) (totalSeconds / 60L)
  long seconds = (long) (totalSeconds % 60L)
  return minutes > 0 ? "${minutes}분 ${seconds}초" : "${seconds}초"
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

String resolveTextOrFileCredential(def script, String value, String name) {
  if (!value?.trim()) {
    script.error "${name} is blank."
  }

  String resolved = ''
  script.withEnv(["CREDENTIAL_VALUE=${value}"]) {
    resolved = script.sh(
      script: '''
        set +x
        if [ -f "$CREDENTIAL_VALUE" ]; then
          tr -d '\\r\\n' < "$CREDENTIAL_VALUE"
        else
          printf '%s' "$CREDENTIAL_VALUE"
        fi
      ''',
      returnStdout: true
    ).trim()
  }

  if (!resolved) {
    script.error "${name} resolved to blank."
  }
  return resolved
}

pipeline {
  agent any

  options {
    disableConcurrentBuilds()
  }

  parameters {
    string(name: 'DEPLOY_BRANCH', defaultValue: 'master', description: 'Git branch to deploy to S2 prod')
    booleanParam(name: 'BUILD_GRAPHHOPPER', defaultValue: false, description: 'Build graph-cache from PostgreSQL before deploying GraphHopper')
    booleanParam(name: 'DEPLOY_GRAPHHOPPER', defaultValue: true, description: 'Start GraphHopper runtime after graph-cache is ready')
    booleanParam(name: 'ROLLBACK', defaultValue: false, description: 'Run rollback instead of deploy')
  }

  environment {
    REPO_URL = credentials('e102-repo-url')
    REMOTE_DIR = '/home/ubuntu/e102/prod'
    S2_HOST = credentials('e102-s2-host')
    RUNTIME_STATE_DIR = '/opt/e102-server/runtime-state'
  }

  stages {
    stage('Checkout') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        git branch: params.DEPLOY_BRANCH, credentialsId: 'gitlab-pat', url: env.REPO_URL
        script {
          env.DEPLOY_COMMIT = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
        }
      }
    }

    stage('Resolve S2 Host Credential') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
          env.S2_HOST = resolveTextOrFileCredential(this, env.S2_HOST, 'e102-s2-host')
        }
      }
    }

    stage('Package Workspace') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        script {
          env.APP_IMAGE_TAG = sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()
          env.GRAPHHOPPER_IMAGE_TAG = env.APP_IMAGE_TAG
        }
        sh '''
          rm -f e102-prod-workspace.tar.gz
          git archive --format=tar.gz --output=e102-prod-workspace.tar.gz HEAD
        '''
      }
    }

    stage('Notify Start') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
          String message = """\
            ----
            ##### :jenkins: PROD 배포가 시작되었습니다! :rocket:

            - **대상 파이프라인:** `e102-prod-deploy`
            - **브랜치:** `${safeValue(params.DEPLOY_BRANCH)}`
            - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
            - **트리거:** `${safeValue(resolveTriggerDescription(currentBuild))}`
            - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
            - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

            ----

            :warning: 운영 배포가 진행 중입니다.
            ----
          """.stripIndent().trim()
          sendMattermost(this, message)
        }
      }
    }

    stage('Upload To S2') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        withCredentials([
          file(credentialsId: 'e102-prod-env-file', variable: 'PROD_ENV'),
          sshUserPrivateKey(credentialsId: 'e102-s2-ssh-key', keyFileVariable: 'S2_KEY', usernameVariable: 'S2_USER')
        ]) {
          sh '''
            ssh -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$S2_USER@$S2_HOST" "mkdir -p '$REMOTE_DIR'"
            scp -i "$S2_KEY" -o StrictHostKeyChecking=accept-new e102-prod-workspace.tar.gz "$S2_USER@$S2_HOST:$REMOTE_DIR/"
            scp -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$PROD_ENV" "$S2_USER@$S2_HOST:$REMOTE_DIR/.env.prod.upload"
            ssh -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$S2_USER@$S2_HOST" "cd '$REMOTE_DIR' && mv -f .env.prod.upload .env.prod && chmod 600 .env.prod && mkdir -p .deploy-state && find . -mindepth 1 -maxdepth 1 ! -name .deploy-state ! -name .env.prod ! -name e102-prod-workspace.tar.gz -exec rm -rf {} + && tar -xzf e102-prod-workspace.tar.gz && chmod +x scripts/deploy/*.sh"
          '''
        }
      }
    }

    stage('Deploy Or Rollback') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        withCredentials([
          sshUserPrivateKey(credentialsId: 'e102-s2-ssh-key', keyFileVariable: 'S2_KEY', usernameVariable: 'S2_USER')
        ]) {
          script {
            String deployGraphhopper = params.DEPLOY_GRAPHHOPPER.toString()
            String remoteCmd

            if (params.ROLLBACK) {
              remoteCmd = "DEPLOY_GRAPHHOPPER=${deployGraphhopper} bash scripts/deploy/prod-rollback.sh"
            } else {
              String buildGraphhopper = params.BUILD_GRAPHHOPPER.toString()
              remoteCmd = "APP_IMAGE_TAG=${env.APP_IMAGE_TAG} GRAPHHOPPER_IMAGE_TAG=${env.GRAPHHOPPER_IMAGE_TAG} BUILD_GRAPHHOPPER=${buildGraphhopper} DEPLOY_GRAPHHOPPER=${deployGraphhopper} bash scripts/deploy/prod-deploy.sh"
            }

            sh """
              ssh -i "\$S2_KEY" -o StrictHostKeyChecking=accept-new "\$S2_USER@\$S2_HOST" "cd '$REMOTE_DIR' && ${remoteCmd}"
            """
          }
        }
      }
    }

    stage('Write Release Manifest') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        withCredentials([
          sshUserPrivateKey(credentialsId: 'e102-s2-ssh-key', keyFileVariable: 'S2_KEY', usernameVariable: 'S2_USER')
        ]) {
          script {
            env.PROD_DEPLOYED_COMMIT = sh(
              script: """
                ssh -i "\$S2_KEY" -o StrictHostKeyChecking=accept-new "\$S2_USER@\$S2_HOST" \
                  "cat '${env.REMOTE_DIR}/.deploy-state/current-app-image'"
              """,
              returnStdout: true,
            ).trim()
            env.PROD_HAS_GRAPHHOPPER = sh(
              script: """
                ssh -i "\$S2_KEY" -o StrictHostKeyChecking=accept-new "\$S2_USER@\$S2_HOST" \
                  "if [ -f '${env.REMOTE_DIR}/.deploy-state/current-graphhopper-image' ]; then echo true; else echo false; fi"
              """,
              returnStdout: true,
            ).trim()
          }
        }
        script {
          String services = env.PROD_HAS_GRAPHHOPPER == 'true'
            ? 'backend ai admin graphhopper-blue graphhopper-green'
            : 'backend ai admin'
          sh """
            mkdir -p "$RUNTIME_STATE_DIR"
            python3 scripts/deploy/write-release-manifest.py \
              --output "$RUNTIME_STATE_DIR/prod-release.json" \
              --environment prod \
              --branch "${params.DEPLOY_BRANCH}" \
              --commit "${env.PROD_DEPLOYED_COMMIT}" \
              --build-number "$BUILD_NUMBER" \
              --build-url "$BUILD_URL" \
              --services ${services} \
              --metadata source=jenkins \
              --metadata pipeline=e102-prod-deploy \
              --metadata rollback=${params.ROLLBACK.toString()} \
              --metadata deploy_graphhopper=${params.DEPLOY_GRAPHHOPPER.toString()} \
              --metadata build_graphhopper=${params.BUILD_GRAPHHOPPER.toString()}
          """
        }
      }
    }
  }

  post {
    always {
      withCredentials([
        sshUserPrivateKey(credentialsId: 'e102-s2-ssh-key', keyFileVariable: 'S2_KEY', usernameVariable: 'S2_USER')
      ]) {
        sh '''
          ssh -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$S2_USER@$S2_HOST" "cd '$REMOTE_DIR' && docker compose --env-file .env.prod -f docker-compose.prod.yml ps" || true
        '''
      }
      script {
        deleteDir()
      }
    }
    success {
      script {
        String message = """\
          ----
          ##### ✅ PROD 배포가 완료되었습니다.

          - **대상 파이프라인:** `e102-prod-deploy`
          - **브랜치:** `${safeValue(params.DEPLOY_BRANCH)}`
          - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
          - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
          - **소요 시간:** `${formatBuildDuration(currentBuild)}`
          - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

          ----

          @here 운영 배포가 정상 완료되었습니다!
          ----
        """.stripIndent().trim()
        sendMattermost(this, message)
      }
    }
    failure {
      withCredentials([
        sshUserPrivateKey(credentialsId: 'e102-s2-ssh-key', keyFileVariable: 'S2_KEY', usernameVariable: 'S2_USER')
      ]) {
        sh '''
          ssh -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$S2_USER@$S2_HOST" "cd '$REMOTE_DIR' && docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=160 backend ai admin || true"
        '''
      }
      script {
        String message = """\
          ----
          ##### ❌ PROD 배포가 실패했습니다.

          - **대상 파이프라인:** `e102-prod-deploy`
          - **브랜치:** `${safeValue(params.DEPLOY_BRANCH)}`
          - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
          - **실패 스테이지:** `${safeValue(env.LAST_STAGE_NAME, 'unknown')}`
          - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
          - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

          ----

          @here 운영 배포 실패로 로그 확인이 필요합니다.
          ----
        """.stripIndent().trim()
        sendMattermost(this, message)
      }
    }
  }
}
