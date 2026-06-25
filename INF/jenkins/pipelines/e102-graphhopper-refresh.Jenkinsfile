import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

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

String warningFromRefreshReport(String reportJson) {
  if (!reportJson?.trim()) {
    return ''
  }
  try {
    def report = new JsonSlurperClassic().parseText(reportJson)
    return safeValue(report.warningMessage as String, '')
  } catch (ignored) {
    return ''
  }
}

pipeline {
  agent any

  options {
    disableConcurrentBuilds()
  }

  triggers {
    cron('H H/3 * * *')
  }

  parameters {
    string(name: 'DEPLOY_BRANCH', defaultValue: 'master', description: 'Git branch that contains GraphHopper refresh scripts')
  }

  environment {
    REPO_URL = credentials('e102-repo-url')
    REMOTE_DIR = '/home/ubuntu/e102/prod'
    S2_HOST = credentials('e102-s2-host')
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
        sh '''
          rm -f e102-graphhopper-refresh-workspace.tar.gz
          git archive --format=tar.gz --output=e102-graphhopper-refresh-workspace.tar.gz HEAD
        '''
      }
    }

    stage('Notify Start') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
          String message = """\
            ----
            ##### :jenkins: GraphHopper 자동 갱신이 시작되었습니다.

            - **대상 파이프라인:** `e102-graphhopper-refresh`
            - **브랜치:** `${safeValue(params.DEPLOY_BRANCH)}`
            - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
            - **주기:** `3시간`
            - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
            - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

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
            scp -i "$S2_KEY" -o StrictHostKeyChecking=accept-new e102-graphhopper-refresh-workspace.tar.gz "$S2_USER@$S2_HOST:$REMOTE_DIR/"
            scp -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$PROD_ENV" "$S2_USER@$S2_HOST:$REMOTE_DIR/.env.prod.upload"
            ssh -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$S2_USER@$S2_HOST" "cd '$REMOTE_DIR' && mv -f .env.prod.upload .env.prod && chmod 600 .env.prod && mkdir -p .deploy-state runtime/graphhopper/refresh && find . -mindepth 1 -maxdepth 1 ! -name .deploy-state ! -name runtime ! -name .env.prod ! -name e102-graphhopper-refresh-workspace.tar.gz -exec rm -rf {} + && tar -xzf e102-graphhopper-refresh-workspace.tar.gz"
          '''
        }
      }
    }

    stage('Refresh GraphHopper') {
      steps {
        script {
          env.LAST_STAGE_NAME = env.STAGE_NAME
        }
        withCredentials([
          sshUserPrivateKey(credentialsId: 'e102-s2-ssh-key', keyFileVariable: 'S2_KEY', usernameVariable: 'S2_USER')
        ]) {
          sh """
            ssh -i "\$S2_KEY" -o StrictHostKeyChecking=accept-new "\$S2_USER@\$S2_HOST" "cd '$REMOTE_DIR' && GRAPHHOPPER_BACKEND_SMOKE_REQUIRED='true' GRAPHHOPPER_REFRESH_BUILD_ID='jenkins-${env.BUILD_NUMBER}-${env.DEPLOY_COMMIT}' bash scripts/graphhopper/prod-bluegreen-refresh.sh"
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
          ssh -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$S2_USER@$S2_HOST" "cd '$REMOTE_DIR' && ls -t runtime/graphhopper/refresh/*.json 2>/dev/null | head -n 1 | xargs -r cat && docker compose --env-file .env.prod -f docker-compose.prod.yml --profile graphhopper ps graphhopper-blue graphhopper-green" || true
        '''
      }
      script {
        deleteDir()
      }
    }
    success {
      script {
        String warning = ''
        withCredentials([
          sshUserPrivateKey(credentialsId: 'e102-s2-ssh-key', keyFileVariable: 'S2_KEY', usernameVariable: 'S2_USER')
        ]) {
          String reportJson = sh(script: '''
            ssh -i "$S2_KEY" -o StrictHostKeyChecking=accept-new "$S2_USER@$S2_HOST" "cd '$REMOTE_DIR' && latest=\$(ls -t runtime/graphhopper/refresh/*.json 2>/dev/null | head -n 1); if [ -n \"\$latest\" ]; then cat \"\$latest\"; fi" || true
          ''', returnStdout: true).trim()
          warning = warningFromRefreshReport(reportJson)
        }
        String warningLine = warning ? "\n          - **경고:** `${warning}`" : ''
        String message = """\
          ----
          ##### ✅ GraphHopper 자동 갱신이 완료되었습니다.

          - **대상 파이프라인:** `e102-graphhopper-refresh`
          - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
          - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
          - **소요 시간:** `${formatBuildDuration(currentBuild)}`
          ${warningLine}
          - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

          ----
        """.stripIndent().trim()
        sendMattermost(this, message)
      }
    }
    failure {
      script {
        String message = """\
          ----
          ##### ❌ GraphHopper 자동 갱신이 실패했습니다.

          - **대상 파이프라인:** `e102-graphhopper-refresh`
          - **커밋:** `${safeValue(env.DEPLOY_COMMIT, 'unknown')}`
          - **실패 스테이지:** `${safeValue(env.LAST_STAGE_NAME, 'unknown')}`
          - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
          - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

          ----

          기존 active GraphHopper slot은 유지되거나 rollback됩니다. S2 refresh report와 container log 확인이 필요합니다.
          ----
        """.stripIndent().trim()
        sendMattermost(this, message)
      }
    }
  }
}
