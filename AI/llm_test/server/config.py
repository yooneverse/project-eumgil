import os


def _env_flag(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


class Config:
    # 서버 설정
    HOST = os.getenv("HOST", os.getenv("AI_HOST", "0.0.0.0"))
    PORT = int(os.getenv("PORT", os.getenv("AI_PORT", "5000")))
    DEBUG = _env_flag("DEBUG", True)

    # 기본 모델 설정
    DEFAULT_MODEL = os.getenv("DEFAULT_MODEL", "gemini")

    # 로그 설정
    LOG_LEVEL = "DEBUG"
