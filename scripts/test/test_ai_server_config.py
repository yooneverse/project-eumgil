#!/usr/bin/env python3

import importlib.util
import os
from pathlib import Path
import tempfile
import unittest


ROOT_DIR = Path(__file__).resolve().parents[2]
CONFIG_PATH = ROOT_DIR / "AI" / "llm_test" / "server" / "config.py"
ENV_LOADER_PATH = ROOT_DIR / "AI" / "llm_test" / "server" / "env_loader.py"


def load_module(module_name: str, path: Path):
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def load_config_module():
    return load_module("ai_server_config_test", CONFIG_PATH)


def load_env_loader_module():
    return load_module("ai_env_loader_test", ENV_LOADER_PATH)


class AiServerConfigTest(unittest.TestCase):
    def test_config_reads_runtime_values_from_env(self):
        old_env = os.environ.copy()
        try:
            os.environ["HOST"] = "127.0.0.1"
            os.environ["PORT"] = "8123"
            os.environ["DEBUG"] = "false"
            os.environ["DEFAULT_MODEL"] = "claude"
            module = load_config_module()

            self.assertEqual(module.Config.HOST, "127.0.0.1")
            self.assertEqual(module.Config.PORT, 8123)
            self.assertFalse(module.Config.DEBUG)
            self.assertEqual(module.Config.DEFAULT_MODEL, "claude")
        finally:
            os.environ.clear()
            os.environ.update(old_env)

    def test_config_falls_back_to_ai_host_and_ai_port(self):
        old_env = os.environ.copy()
        try:
            os.environ.pop("HOST", None)
            os.environ.pop("PORT", None)
            os.environ["AI_HOST"] = "0.0.0.0"
            os.environ["AI_PORT"] = "5001"
            module = load_config_module()

            self.assertEqual(module.Config.HOST, "0.0.0.0")
            self.assertEqual(module.Config.PORT, 5001)
        finally:
            os.environ.clear()
            os.environ.update(old_env)

    def test_env_loader_defaults_to_root_env_dev(self):
        old_env = os.environ.copy()
        try:
            os.environ.pop("ENV_FILE", None)
            os.environ.pop("APP_ENV", None)
            os.environ.pop("SPRING_PROFILES_ACTIVE", None)

            module = load_env_loader_module()

            with tempfile.TemporaryDirectory() as tmpdir:
                project_root = Path(tmpdir)
                server_root = project_root / "AI" / "llm_test" / "server"
                server_root.mkdir(parents=True)
                env_dev = project_root / ".env.dev"
                env_dev.write_text("GMS_KEY=dev-key\n", encoding="utf-8")
                legacy_env = server_root / ".env"
                legacy_env.write_text("GMS_KEY=legacy-key\n", encoding="utf-8")

                resolved = module.resolve_env_file(project_root=project_root, server_root=server_root)

                self.assertEqual(resolved, env_dev.resolve())
        finally:
            os.environ.clear()
            os.environ.update(old_env)

    def test_env_loader_uses_app_env_specific_file(self):
        old_env = os.environ.copy()
        try:
            os.environ["APP_ENV"] = "prod"
            os.environ.pop("ENV_FILE", None)
            os.environ.pop("SPRING_PROFILES_ACTIVE", None)

            module = load_env_loader_module()

            with tempfile.TemporaryDirectory() as tmpdir:
                project_root = Path(tmpdir)
                server_root = project_root / "AI" / "llm_test" / "server"
                server_root.mkdir(parents=True)
                env_prod = project_root / ".env.prod"
                env_prod.write_text("GMS_KEY=prod-key\n", encoding="utf-8")
                (project_root / ".env.dev").write_text("GMS_KEY=dev-key\n", encoding="utf-8")

                resolved = module.resolve_env_file(project_root=project_root, server_root=server_root)

                self.assertEqual(resolved, env_prod.resolve())
        finally:
            os.environ.clear()
            os.environ.update(old_env)

    def test_env_loader_maps_local_to_root_env_dev(self):
        old_env = os.environ.copy()
        try:
            os.environ["APP_ENV"] = "local"
            os.environ.pop("ENV_FILE", None)
            os.environ.pop("SPRING_PROFILES_ACTIVE", None)

            module = load_env_loader_module()

            with tempfile.TemporaryDirectory() as tmpdir:
                project_root = Path(tmpdir)
                server_root = project_root / "AI" / "llm_test" / "server"
                server_root.mkdir(parents=True)
                env_dev = project_root / ".env.dev"
                env_dev.write_text("GMS_KEY=dev-key\n", encoding="utf-8")

                resolved = module.resolve_env_file(project_root=project_root, server_root=server_root)

                self.assertEqual(resolved, env_dev.resolve())
        finally:
            os.environ.clear()
            os.environ.update(old_env)

    def test_env_loader_respects_env_file_override(self):
        old_env = os.environ.copy()
        try:
            module = load_env_loader_module()

            with tempfile.TemporaryDirectory() as tmpdir:
                project_root = Path(tmpdir)
                server_root = project_root / "AI" / "llm_test" / "server"
                server_root.mkdir(parents=True)
                explicit_env = project_root / "custom.env"
                explicit_env.write_text("GMS_KEY=override-key\n", encoding="utf-8")
                os.environ["ENV_FILE"] = str(explicit_env)
                os.environ["APP_ENV"] = "prod"

                resolved = module.resolve_env_file(project_root=project_root, server_root=server_root)

                self.assertEqual(resolved, explicit_env.resolve())
        finally:
            os.environ.clear()
            os.environ.update(old_env)


if __name__ == "__main__":
    unittest.main()
