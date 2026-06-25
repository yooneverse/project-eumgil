from __future__ import annotations

import os
from pathlib import Path


SERVER_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = SERVER_ROOT.parents[2]

ENV_ALIASES = {
    "dev": "dev",
    "development": "dev",
    "local": "dev",
    "prod": "prod",
    "production": "prod",
    "test": "test",
}


def normalize_env_name(raw: str | None) -> str | None:
    if not raw:
        return None

    for token in raw.replace(";", ",").split(","):
        name = token.strip().lower()
        if not name:
            continue
        return ENV_ALIASES.get(name, name)

    return None


def candidate_env_files(
    project_root: Path | None = None,
    server_root: Path | None = None,
    default_env: str = "dev",
) -> list[Path]:
    project_dir = Path(project_root) if project_root else PROJECT_ROOT
    server_dir = Path(server_root) if server_root else SERVER_ROOT

    candidates: list[Path] = []

    env_file = os.getenv("ENV_FILE")
    if env_file:
        candidates.append(Path(env_file).expanduser())

    app_env = normalize_env_name(os.getenv("APP_ENV") or os.getenv("SPRING_PROFILES_ACTIVE"))
    if app_env:
        candidates.append(project_dir / f".env.{app_env}")
    elif default_env:
        candidates.append(project_dir / f".env.{default_env}")

    # Legacy fallback for developers who still keep an AI-only env file locally.
    candidates.append(server_dir / ".env")

    deduped: list[Path] = []
    seen: set[Path] = set()
    for candidate in candidates:
        resolved = candidate.resolve(strict=False)
        if resolved in seen:
            continue
        seen.add(resolved)
        deduped.append(resolved)
    return deduped


def resolve_env_file(
    project_root: Path | None = None,
    server_root: Path | None = None,
    default_env: str = "dev",
) -> Path | None:
    for candidate in candidate_env_files(project_root=project_root, server_root=server_root, default_env=default_env):
        if candidate.is_file():
            return candidate
    return None


def load_runtime_env(
    project_root: Path | None = None,
    server_root: Path | None = None,
    default_env: str = "dev",
) -> Path | None:
    from dotenv import load_dotenv

    env_file = resolve_env_file(project_root=project_root, server_root=server_root, default_env=default_env)
    if env_file:
        load_dotenv(env_file, override=False)
    return env_file
