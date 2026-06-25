#!/usr/bin/env python3
"""모니터링 단순화 설정 회귀 테스트다.

이번 회귀 포인트는 두 가지다.
- S1 Prometheus가 dev backend를 host loopback 우회 경로가 아닌 dev network의 `backend:18080`으로 직접 scrape 해야 한다.
- prod 대시보드의 DB/Redis 카드는 실제 managed resource 상태가 아니라 backend dependency health라는 의미를 드러내야 한다.
"""

from pathlib import Path
import unittest


ROOT_DIR = Path(__file__).resolve().parents[2]
MONITORING_COMPOSE = ROOT_DIR / "INF" / "monitoring" / "s1" / "docker-compose.yml"
PROMETHEUS_CONFIG = ROOT_DIR / "INF" / "monitoring" / "s1" / "prometheus" / "prometheus.yml"
S1_PROMTAIL_CONFIG = ROOT_DIR / "INF" / "monitoring" / "s1" / "promtail" / "config.yml"
S2_PROMTAIL_CONFIG = ROOT_DIR / "INF" / "monitoring" / "s2" / "promtail" / "config.yml"
MONITORING_README = ROOT_DIR / "INF" / "monitoring" / "README.md"
PROD_DASHBOARD = ROOT_DIR / "INF" / "monitoring" / "s1" / "grafana" / "provisioning" / "dashboards" / "json" / "e102-prod-observability.json"
DEV_DASHBOARD = ROOT_DIR / "INF" / "monitoring" / "s1" / "grafana" / "provisioning" / "dashboards" / "json" / "e102-observability-overview.json"
JENKINS_PROXY = ROOT_DIR / "INF" / "jenkins" / "s1" / "nginx.conf"
MONITORING_DEPLOY_PIPELINE = ROOT_DIR / "INF" / "jenkins" / "pipelines" / "e102-monitoring-deploy.Jenkinsfile"
MONITORING_SYNC_SCRIPT = ROOT_DIR / "scripts" / "deploy" / "s1-monitoring-sync.sh"


class MonitoringConfigsTest(unittest.TestCase):
    def test_prometheus_scrapes_dev_backend_on_dev_network(self):
        compose_content = MONITORING_COMPOSE.read_text(encoding="utf-8")
        prometheus_content = PROMETHEUS_CONFIG.read_text(encoding="utf-8")
        readme_content = MONITORING_README.read_text(encoding="utf-8")

        self.assertIn("dev-stack:", compose_content)
        self.assertIn("name: s14p31e102-dev_default", compose_content)
        self.assertIn('targets: ["backend:18080"]', prometheus_content)
        self.assertNotIn('targets: ["host.docker.internal:18080"]', prometheus_content)
        self.assertIn("backend:18080", readme_content)

    def test_redis_exporter_uses_dev_network_redis_by_default(self):
        compose_content = MONITORING_COMPOSE.read_text(encoding="utf-8")

        self.assertIn("REDIS_ADDR: ${DEV_REDIS_EXPORTER_ADDR:-redis://redis:6379}", compose_content)
        self.assertNotIn("redis://host.docker.internal:6379", compose_content)

    def test_blackbox_exporter_joins_dev_network_for_dev_http_probes(self):
        compose_content = MONITORING_COMPOSE.read_text(encoding="utf-8")
        prometheus_content = PROMETHEUS_CONFIG.read_text(encoding="utf-8")

        self.assertIn("blackbox-exporter:", compose_content)
        self.assertIn("- dev-stack", compose_content)
        self.assertIn("job_name: blackbox-dev-http", prometheus_content)
        self.assertIn('targets: ["http://graphhopper:8990/healthcheck"]', prometheus_content)
        self.assertIn('targets: ["https://api.busaneumgil.com/graphhopper/healthcheck"]', prometheus_content)
        self.assertIn('targets: ["https://api.busaneumgil.com/graphhopper-blue/healthcheck"]', prometheus_content)
        self.assertIn('targets: ["https://api.busaneumgil.com/graphhopper-green/healthcheck"]', prometheus_content)

    def test_promtail_separates_dev_and_ops_runtime_labels(self):
        dev_promtail = S1_PROMTAIL_CONFIG.read_text(encoding="utf-8")
        prod_promtail = S2_PROMTAIL_CONFIG.read_text(encoding="utf-8")

        self.assertIn("job_name: docker-dev", dev_promtail)
        self.assertIn("job_name: docker-ops", dev_promtail)
        self.assertIn('regex: "s14p31e102-dev"', dev_promtail)
        self.assertIn('regex: "e102-ops"', dev_promtail)
        self.assertIn("replacement: ops", dev_promtail)
        self.assertIn("replacement: s1-ops", dev_promtail)
        self.assertIn("replacement: prod", prod_promtail)
        self.assertIn("replacement: s2-prod", prod_promtail)
        self.assertIn("graphhopper-blue", prod_promtail)
        self.assertIn("graphhopper-green", prod_promtail)

    def test_prod_dashboard_explicitly_marks_dependency_health_cards(self):
        dashboard_content = PROD_DASHBOARD.read_text(encoding="utf-8")

        self.assertIn("DB 연결 상태", dashboard_content)
        self.assertIn("Redis 연결 상태", dashboard_content)
        self.assertIn("dependency health", dashboard_content)
        self.assertNotIn('"title": "DB 상태"', dashboard_content)
        self.assertNotIn('"title": "Redis 상태"', dashboard_content)

    def test_dashboards_include_expected_status_cards(self):
        prod_dashboard = PROD_DASHBOARD.read_text(encoding="utf-8")
        dev_dashboard = DEV_DASHBOARD.read_text(encoding="utf-8")

        self.assertIn("GraphHopper active / blue / green 상태", prod_dashboard)
        self.assertIn('target_name=~\\"graphhopper|graphhopper-blue|graphhopper-green\\"', prod_dashboard)
        self.assertIn("MinIO 상태", dev_dashboard)
        self.assertIn("GraphHopper 상태", dev_dashboard)

    def test_warning_error_queries_use_severity_positions_instead_of_free_text_matching(self):
        prod_dashboard = PROD_DASHBOARD.read_text(encoding="utf-8")
        dev_dashboard = DEV_DASHBOARD.read_text(encoding="utf-8")

        explicit_severity_filter = (
            '(?:level|lvl|severity)=(?:warn|warning|error|fatal|critical|'
            'WARN|WARNING|ERROR|FATAL|CRITICAL)'
        )
        klog_severity_filter = "^[WEFwef][0-9]{4}"

        self.assertIn(explicit_severity_filter, prod_dashboard)
        self.assertIn(explicit_severity_filter, dev_dashboard)
        self.assertIn(klog_severity_filter, prod_dashboard)
        self.assertIn(klog_severity_filter, dev_dashboard)
        self.assertNotIn('|~ \\"(?i)(warn|warning|error|fatal|critical|exception|timeout|failed|traceback)\\"', prod_dashboard)
        self.assertNotIn('|~ \\"(?i)(warn|warning|error|fatal|critical|exception|timeout|failed|traceback)\\"', dev_dashboard)
        self.assertNotIn('level=~\\"(?i)(warn|warning|error|fatal|critical|w|e|f)\\"', prod_dashboard)
        self.assertNotIn('level=~\\"(?i)(warn|warning|error|fatal|critical|w|e|f)\\"', dev_dashboard)

    def test_promtail_extracts_log_level_without_free_text_keyword_scanning(self):
        old_broad_level_regex = (
            "(?i)^(?:.*?[[:space:]])?(?:(?:level|lvl|severity)=)?"
            "(?P<level>debug|info|warn|warning|error|fatal|critical)"
        )
        explicit_level_regex = (
            "(?i)(?:^|[[:space:]])(?:level|lvl|severity)="
            "(?P<level>trace|debug|info|warn|warning|error|fatal|critical)"
        )
        klog_level_regex = "^(?P<level>[DIEWF])\\d{4}[[:space:]]"
        uppercase_level_regex = (
            "^(?:\\d{4}-\\d{2}-\\d{2}(?:[T ][^[:space:]]+)?[[:space:]]+)?"
            "(?P<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)"
        )

        for promtail_config in (S1_PROMTAIL_CONFIG, S2_PROMTAIL_CONFIG):
            content = promtail_config.read_text(encoding="utf-8")

            self.assertNotIn(old_broad_level_regex, content)
            self.assertIn(explicit_level_regex, content)
            self.assertIn(klog_level_regex, content)
            self.assertIn(uppercase_level_regex, content)

    def test_proxy_uses_dynamic_service_resolution_for_grafana_and_loki(self):
        proxy_content = JENKINS_PROXY.read_text(encoding="utf-8")

        self.assertIn('set $grafana_upstream "http://grafana:3000";', proxy_content)
        self.assertIn('proxy_pass $grafana_upstream;', proxy_content)
        self.assertIn('set $loki_upstream "http://loki:3100";', proxy_content)
        self.assertIn('proxy_pass $loki_upstream/loki/;', proxy_content)
        self.assertNotIn("proxy_pass http://grafana_upstream;", proxy_content)

    def test_monitoring_deploy_job_syncs_runtime_files(self):
        pipeline_content = MONITORING_DEPLOY_PIPELINE.read_text(encoding="utf-8")
        script_content = MONITORING_SYNC_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("bash scripts/deploy/s1-monitoring-sync.sh", pipeline_content)
        self.assertIn("docker network create e102-ops", script_content)
        self.assertIn("docker network create", script_content)
        self.assertIn("s14p31e102-dev_default", script_content)
        self.assertIn("docker restart e102-jenkins-proxy", script_content)
        self.assertIn("docker restart e102-grafana", script_content)
        self.assertIn("docker restart e102-prometheus", script_content)
        self.assertIn("docker restart e102-blackbox-exporter", script_content)
        self.assertIn("docker restart e102-promtail", script_content)


if __name__ == "__main__":
    unittest.main()
