#!/usr/bin/env python3
"""hourly observability brief 자동화 회귀 테스트다."""

from pathlib import Path
import unittest


ROOT_DIR = Path(__file__).resolve().parents[2]
JENKINS_DOCKERFILE = ROOT_DIR / "INF" / "jenkins" / "s1" / "Dockerfile"
JENKINS_COMPOSE = ROOT_DIR / "INF" / "jenkins" / "s1" / "docker-compose.yml"
HOURLY_PIPELINE = ROOT_DIR / "INF" / "jenkins" / "pipelines" / "e102-observability-hourly-brief.Jenkinsfile"
HOURLY_JOB_BOOTSTRAP = ROOT_DIR / "INF" / "jenkins" / "s1" / "init.groovy.d" / "06-observability-hourly-brief-job.groovy"
DEV_PIPELINE = ROOT_DIR / "INF" / "jenkins" / "pipelines" / "e102-dev-deploy.Jenkinsfile"
PROD_PIPELINE = ROOT_DIR / "INF" / "jenkins" / "pipelines" / "e102-prod-deploy.Jenkinsfile"
README = ROOT_DIR / "INF" / "jenkins" / "README.md"
REPORT_SCRIPT = ROOT_DIR / "scripts" / "monitoring" / "hourly_observability_brief.py"
PROD_CREDENTIAL_BOOTSTRAP = ROOT_DIR / "INF" / "jenkins" / "s1" / "init.groovy.d" / "prod-deploy-credentials.groovy"


class ObservabilityHourlyAutomationTest(unittest.TestCase):
    def test_jenkins_runtime_supports_hourly_brief_dependencies(self):
        dockerfile = JENKINS_DOCKERFILE.read_text(encoding="utf-8")
        compose = JENKINS_COMPOSE.read_text(encoding="utf-8")

        self.assertIn("python3", dockerfile)
        self.assertIn("/home/ubuntu/e102/runtime-state:/opt/e102-server/runtime-state", compose)
        self.assertIn("- ops-stack", compose)

    def test_hourly_brief_pipeline_runs_python_report_and_archives_json(self):
        pipeline = HOURLY_PIPELINE.read_text(encoding="utf-8")

        self.assertIn("e102-observability-hourly-brief", pipeline)
        self.assertIn("git branch: 'master'", pipeline)
        self.assertIn("git fetch origin develop master --prune", pipeline)
        self.assertIn("DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL = credentials('e102-dev-log-analysis-webhook-url')", pipeline)
        self.assertIn("PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL = credentials('e102-prod-log-analysis-webhook-url')", pipeline)
        self.assertNotIn("LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL = credentials('e102-log-analysis-webhook-url')", pipeline)
        self.assertIn("withCredentials([file(credentialsId: 'e102-prod-env-file'", pipeline)
        self.assertIn("set +x", pipeline)
        self.assertLess(pipeline.index("set +x"), pipeline.index("GMS_KEY_VALUE="))
        self.assertIn('if key.strip() == "GMS_KEY"', pipeline)
        self.assertIn("OBS_BRIEF_AGENT_PROVIDER=anthropic-gms", pipeline)
        self.assertIn("claude-opus-4-5-20251101", pipeline)
        self.assertIn("python3 scripts/monitoring/hourly_observability_brief.py", pipeline)
        self.assertIn("--dry-run", pipeline)
        self.assertIn("archiveArtifacts artifacts: 'reports/observability/*.json'", pipeline)
        self.assertIn("prod_mattermost_text", pipeline)
        self.assertIn("dev_mattermost_text", pipeline)

    def test_hourly_brief_job_bootstrap_is_scm_backed_and_hourly(self):
        bootstrap = HOURLY_JOB_BOOTSTRAP.read_text(encoding="utf-8")

        self.assertIn("CpsScmFlowDefinition", bootstrap)
        self.assertIn("INF/jenkins/pipelines/e102-observability-hourly-brief.Jenkinsfile", bootstrap)
        self.assertIn("new TimerTrigger('H * * * *')", bootstrap)
        self.assertIn("*/master", bootstrap)

    def test_dev_and_prod_pipelines_write_release_manifests(self):
        dev_pipeline = DEV_PIPELINE.read_text(encoding="utf-8")
        prod_pipeline = PROD_PIPELINE.read_text(encoding="utf-8")

        self.assertIn("write-release-manifest.py", dev_pipeline)
        self.assertIn('dev-release.json', dev_pipeline)
        self.assertIn("--environment dev", dev_pipeline)
        self.assertIn("write-release-manifest.py", prod_pipeline)
        self.assertIn('prod-release.json', prod_pipeline)
        self.assertIn("current-app-image", prod_pipeline)
        self.assertIn("--environment prod", prod_pipeline)

    def test_readme_documents_hourly_brief_job_and_optional_agent_summary(self):
        readme = README.read_text(encoding="utf-8")

        self.assertIn("## `e102-observability-hourly-brief`", readme)
        self.assertIn("runtime-state", readme)
        self.assertIn("GMS_KEY", readme)
        self.assertIn("claude-opus-4-5-20251101", readme)
        self.assertIn("DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL", readme)
        self.assertIn("PROD_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL", readme)
        self.assertIn("E102_로그분석채널", readme)
        self.assertIn("fallback", readme)

    def test_report_script_supports_local_git_fallback_and_optional_agent(self):
        script = REPORT_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("fetch_local_commits", script)
        self.assertIn("OBS_BRIEF_AGENT_ENABLED", script)
        self.assertIn("OBS_BRIEF_GITLAB_TOKEN", script)
        self.assertIn('provider_name in {"anthropic", "anthropic-gms"}', script)
        self.assertNotIn("curl_json_request", script)
        self.assertNotIn('["-H", f"{key}: {value}"]', script)
        self.assertIn("DEV_LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL", script)
        self.assertIn("render_environment_mattermost", script)
        self.assertIn("observability-brief-context.json", script)

    def test_credential_bootstrap_includes_log_analysis_webhook(self):
        bootstrap = PROD_CREDENTIAL_BOOTSTRAP.read_text(encoding="utf-8")

        self.assertIn("e102-log-analysis-webhook-url", bootstrap)
        self.assertIn("LOG_ANALYSIS_MATTERMOST_WEBHOOK_URL", bootstrap)
        self.assertIn("e102-dev-log-analysis-webhook-url", bootstrap)
        self.assertIn("e102-prod-log-analysis-webhook-url", bootstrap)

    def test_credential_bootstrap_supports_s2_host_file_source(self):
        bootstrap = PROD_CREDENTIAL_BOOTSTRAP.read_text(encoding="utf-8")
        readme = README.read_text(encoding="utf-8")

        self.assertIn("envOrFile", bootstrap)
        self.assertIn("E102_S2_HOST_FILE", bootstrap)
        self.assertIn("/var/jenkins_home/prod-secrets/e102-s2-host", bootstrap)
        self.assertIn("/var/jenkins_home/prod-secrets/e102-s2-user", bootstrap)
        self.assertIn("'43.201.198.214'", bootstrap)
        self.assertIn("install -m 644", readme)
        self.assertIn("600 root:root", readme)


if __name__ == "__main__":
    unittest.main()
