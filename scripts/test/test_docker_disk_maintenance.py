#!/usr/bin/env python3
"""Docker disk maintenance automation regression tests."""

from pathlib import Path
import unittest


ROOT_DIR = Path(__file__).resolve().parents[2]
MAINTENANCE_SCRIPT = ROOT_DIR / "scripts" / "maintenance" / "docker-disk-maintenance.sh"
INSTALL_SCRIPT = ROOT_DIR / "scripts" / "maintenance" / "install-docker-disk-maintenance.sh"
PROD_DEPLOY = ROOT_DIR / "scripts" / "deploy" / "prod-deploy.sh"
REFRESH_SCRIPT = ROOT_DIR / "scripts" / "graphhopper" / "prod-bluegreen-refresh.sh"
SCRIPTS_README = ROOT_DIR / "scripts" / "README.md"
JENKINS_README = ROOT_DIR / "INF" / "jenkins" / "README.md"
JENKINSFILES = [
    ROOT_DIR / "INF" / "jenkins" / "pipelines" / "e102-dev-deploy.Jenkinsfile",
    ROOT_DIR / "INF" / "jenkins" / "pipelines" / "e102-prod-deploy.Jenkinsfile",
    ROOT_DIR / "INF" / "jenkins" / "pipelines" / "e102-graphhopper-refresh.Jenkinsfile",
    ROOT_DIR / "INF" / "jenkins" / "pipelines" / "e102-monitoring-deploy.Jenkinsfile",
]


class DockerDiskMaintenanceTest(unittest.TestCase):
    def test_maintenance_script_prunes_build_cache_without_default_volume_prune(self):
        content = MAINTENANCE_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("docker container prune --force", content)
        self.assertIn("image_prune_args=(docker image prune --force)", content)
        self.assertIn("DOCKER_DISK_PRUNE_IMAGES_ALL", content)
        self.assertIn("image_prune_args+=(--all)", content)
        self.assertNotIn("docker image prune --all --force", content)
        self.assertIn("docker builder prune --force", content)
        self.assertIn("--max-used-space", content)
        self.assertIn("--keep-storage", content)
        self.assertIn("builder_prune_args", content)
        self.assertIn("DOCKER_DISK_BUILDER_ALL", content)
        self.assertIn("builder_prune_args+=(--all)", content)
        self.assertIn("DEFAULT_PRUNE_VOLUMES=false", content)
        self.assertIn('if [ "$PRUNE_VOLUMES" = "true" ]; then', content)
        self.assertIn("docker system df", content)

    def test_maintenance_script_cleans_jenkins_workspace_and_archives(self):
        content = MAINTENANCE_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("JENKINS_WORKSPACE_DIR", content)
        self.assertIn("JENKINS_BACKUP_ARCHIVE_DIR", content)
        self.assertIn("cleanup_directory_children", content)
        self.assertIn("-mindepth 1 -maxdepth 1", content)

    def test_install_script_installs_cron_logrotate_and_docker_log_rotation(self):
        content = INSTALL_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("/etc/cron.d/e102-docker-disk-maintenance", content)
        self.assertIn("17 */3 * * *", content)
        self.assertIn("/etc/logrotate.d/e102-docker-disk-maintenance", content)
        self.assertIn("/etc/docker/daemon.json", content)
        self.assertIn('"max-size"', content)
        self.assertIn('"max-file"', content)
        self.assertIn("DOCKER_DISK_PRUNE_IMAGES_ALL=false", content)
        self.assertIn("DOCKER_DISK_BUILDER_ALL=true", content)
        self.assertIn("DOCKER_DISK_BUILDER_UNTIL=24h", content)
        self.assertIn("RESTART_DOCKER_DAEMON", content)

    def test_prod_deploy_and_graphhopper_refresh_run_pipeline_cleanup_after_success(self):
        prod_content = PROD_DEPLOY.read_text(encoding="utf-8")
        refresh_content = REFRESH_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("DOCKER_DISK_MAINTENANCE_AFTER_DEPLOY", prod_content)
        self.assertIn("docker-disk-maintenance.sh", prod_content)
        self.assertIn("DOCKER_DISK_MAINTENANCE_MODE:-pipeline", prod_content)
        self.assertLess(
            prod_content.index('bash "$ROOT_DIR/scripts/deploy/prod-smoke.sh"'),
            prod_content.index("DOCKER_DISK_MAINTENANCE_AFTER_DEPLOY"),
        )

        self.assertIn("DOCKER_DISK_MAINTENANCE_AFTER_GRAPHHOPPER_REFRESH", refresh_content)
        self.assertIn("docker-disk-maintenance.sh", refresh_content)
        self.assertIn("DOCKER_DISK_MAINTENANCE_MODE:-pipeline", refresh_content)
        self.assertLess(
            refresh_content.index('status="SUCCESS"'),
            refresh_content.index("DOCKER_DISK_MAINTENANCE_AFTER_GRAPHHOPPER_REFRESH"),
        )

    def test_jenkinsfiles_delete_workspace_after_jobs(self):
        for path in JENKINSFILES:
            with self.subTest(path=path.name):
                content = path.read_text(encoding="utf-8")
                self.assertIn("deleteDir()", content)

    def test_docs_describe_disk_maintenance_contract(self):
        scripts_readme = SCRIPTS_README.read_text(encoding="utf-8")
        jenkins_readme = JENKINS_README.read_text(encoding="utf-8")

        self.assertIn("Docker 디스크 자동관리", scripts_readme)
        self.assertIn("Docker volume: 기본 정리하지 않음", scripts_readme)
        self.assertIn("DOCKER_DISK_PRUNE_IMAGES_ALL=true", scripts_readme)
        self.assertIn("Disk Maintenance", jenkins_readme)
        self.assertIn("Docker volume은 graph-cache와 DB data를 보존", jenkins_readme)
        self.assertIn("tagged image 전체 정리", jenkins_readme)


if __name__ == "__main__":
    unittest.main()
