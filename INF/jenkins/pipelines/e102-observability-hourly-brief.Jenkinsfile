import groovy.json.JsonOutput

String safeValue(String value, String fallback = '-') {
  String normalized = value?.trim()
  return normalized ? normalized : fallback
}

void sendMattermost(def script, String webhookUrl, String message) {
  String payloadFile = ".mattermost-payload-${script.env.BUILD_NUMBER}.json"
  script.writeFile file: payloadFile, text: JsonOutput.toJson([text: message])
  script.withEnv(["MM_WEBHOOK_URL=${webhookUrl}"]) {
    script.sh """
      curl -fsS -X POST \
        -H "Content-Type: application/json" \
        --data @${payloadFile} \
        "\$MM_WEBHOOK_URL" >/dev/null 2>&1 || true
      rm -f ${payloadFile}
    """
  }
}

List<String> collectFailureWebhooks(def script) {
  List<String> urls = [
    script.env.PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL,
    script.env.DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL,
  ]
  return urls.findAll { it?.trim() }.collect { it.trim() }.unique()
}

pipeline {
  agent any

  options {
    disableConcurrentBuilds()
    timestamps()
  }

  environment {
    REPO_URL = credentials('e102-repo-url')
    REPORT_JSON = 'reports/observability/hourly-brief.json'
    DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL = credentials('e102-dev-log-analysis-webhook-url')
    PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL = credentials('e102-prod-log-analysis-webhook-url')
    OBS_BRIEF_AGENT_BASE_URL = credentials('e102-llm-gateway-base-url')
  }

  stages {
    stage('Checkout') {
      steps {
        git branch: 'master', credentialsId: 'gitlab-pat', url: env.REPO_URL
        sh '''
          git rev-parse --verify refs/remotes/origin/develop >/dev/null
          git rev-parse --verify refs/remotes/origin/master >/dev/null
        '''
      }
    }

    stage('Generate Brief') {
      steps {
        withCredentials([file(credentialsId: 'e102-prod-env-file', variable: 'E102_PROD_ENV')]) {
          sh '''
            set +x
            mkdir -p reports/observability
            GMS_KEY_VALUE="$(python3 - <<'PY' "$E102_PROD_ENV"
import sys
from pathlib import Path

env_path = Path(sys.argv[1])
for raw_line in env_path.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    if key.strip() == "GMS_KEY":
        cleaned = value.strip().strip("'").strip('"')
        print(cleaned)
        break
PY
)"

            if [ -n "$GMS_KEY_VALUE" ]; then
              export OBS_BRIEF_AGENT_ENABLED=true
              export OBS_BRIEF_AGENT_PROVIDER=anthropic-gms
              export OBS_BRIEF_AGENT_MODEL=claude-opus-4-5-20251101
              export OBS_BRIEF_AGENT_MAX_TOKENS=700
              export OBS_BRIEF_AGENT_API_KEY="$GMS_KEY_VALUE"
            fi

            python3 scripts/monitoring/hourly_observability_brief.py \
              --report-json "$REPORT_JSON" \
              --dry-run
          '''
        }
      }
    }

    stage('Archive Report') {
      steps {
        archiveArtifacts artifacts: 'reports/observability/*.json', fingerprint: false
      }
    }

    stage('Send Mattermost') {
      steps {
        sh '''
          python3 - <<'PY'
import json
from pathlib import Path

report_path = Path("reports/observability/hourly-brief.json")
report = json.loads(report_path.read_text(encoding="utf-8"))
out_dir = report_path.parent
fallback = report.get("mattermost_text") or ""
messages = {
    "prod-message.md": report.get("prod_mattermost_text") or fallback,
    "dev-message.md": report.get("dev_mattermost_text") or fallback,
}
for filename, message in messages.items():
    (out_dir / filename).write_text(message, encoding="utf-8")
PY
        '''
        script {
          String prodMessage = readFile('reports/observability/prod-message.md')
          String devMessage = readFile('reports/observability/dev-message.md')
          if (env.PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL?.trim() && prodMessage?.trim()) {
            sendMattermost(this, env.PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL, prodMessage)
          } else {
            echo 'PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL or prod message is missing. Skipping prod notification.'
          }
          if (env.DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL?.trim() && devMessage?.trim()) {
            sendMattermost(this, env.DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL, devMessage)
          } else {
            echo 'DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL or dev message is missing. Skipping dev notification.'
          }
        }
      }
    }
  }

  post {
    failure {
      script {
        String message = """\
          ----
          ##### ❌ observability hourly brief 생성이 실패했습니다.

          - **대상 파이프라인:** `e102-observability-hourly-brief`
          - **빌드 번호:** `#${safeValue(env.BUILD_NUMBER)}`
          - **빌드 링크:** [Jenkins 빌드 바로가기](${safeValue(env.BUILD_URL)})

          ----

          Loki/Prometheus/Git fetch 또는 Mattermost webhook 상태를 확인해주세요.
          ----
        """.stripIndent().trim()
        List<String> webhooks = collectFailureWebhooks(this)
        if (!webhooks) {
          echo 'No Mattermost webhook configured for observability hourly brief failure notification.'
        }
        webhooks.each { webhook ->
          sendMattermost(this, webhook, message)
        }
      }
    }
  }
}
