from flask import Flask, request, jsonify, g
from flask_cors import CORS
import os
import re
import time
import uuid

from env_loader import load_runtime_env


load_runtime_env()

if not os.getenv("GMS_KEY"):
    raise EnvironmentError("GMS_KEY 환경변수가 설정되어 있지 않습니다. 루트 .env.dev/.env.prod 또는 runtime env를 확인하세요.")

from config import Config
from utils.logger import get_logger
from providers.gemini_provider import GeminiProvider

logger = get_logger(__name__)

app = Flask(__name__)
app.config.from_object(Config)
CORS(app)

PROVIDERS = {
    "gemini": GeminiProvider(),
}

logger.info("Providers ready: " + ", ".join(PROVIDERS.keys()))


def get_request_id() -> str:
    return getattr(g, "request_id", "-")


def format_remote_addr() -> str:
    forwarded_for = request.headers.get("X-Forwarded-For", "").split(",")[0].strip()
    return forwarded_for or request.remote_addr or "-"


@app.before_request
def mark_request_start() -> None:
    g.request_started_at = time.perf_counter()
    g.request_id = request.headers.get("X-Request-Id") or uuid.uuid4().hex[:12]


@app.after_request
def log_request_completion(response):
    if request.path == "/health":
        return response

    started_at = getattr(g, "request_started_at", None)
    latency_ms = 0
    if started_at is not None:
        latency_ms = int((time.perf_counter() - started_at) * 1000)

    logger.info(
        "event=request_completed request_id=%s method=%s path=%s status=%s latency_ms=%s remote_addr=%s content_length=%s",
        get_request_id(),
        request.method,
        request.path,
        response.status_code,
        latency_ms,
        format_remote_addr(),
        response.calculate_content_length() or 0,
    )
    response.headers["X-Request-Id"] = get_request_id()
    return response


@app.teardown_request
def log_unhandled_exception(exception) -> None:
    if exception is None:
        return

    logger.exception(
        "event=request_unhandled_exception request_id=%s method=%s path=%s remote_addr=%s",
        get_request_id(),
        request.method,
        request.path,
        format_remote_addr(),
    )


@app.route('/voice/analyze', methods=['POST'])
def voice_analyze():
    body = request.get_json()

    # ── 입력 검증 ──────────────────────────────────────────
    text = body.get("text", "").strip()
    text = re.sub(r'[.。、·]+$', '', text)

    if not text:
        return jsonify({
            "status": "C4000",
            "data": None,
            "message": "잘못된 입력입니다."
        }), 400

    mode = body.get("mode", "")
    if mode not in ("MOBILITY_IMPAIRED", "LOW_VISION"):
        return jsonify({
            "status": "C4000",
            "data": None,
            "message": "잘못된 입력입니다."
        }), 400

    history = body.get("history", [])
    current_route = body.get("currentRoute")

    # ── 모드 변환 ──────────────────────────────────────────
    internal_mode = "visually" if mode == "LOW_VISION" else "mobility"

    # ── messages 구성 ──────────────────────────────────────
    if history:
        messages = history + [{"role": "user", "content": text}]
    else:
        messages = [{"role": "user", "content": text}]

    # ── AI 호출 ────────────────────────────────────────────
    provider = PROVIDERS["gemini"]
    start_ms = int(time.time() * 1000)
    try:
        result = provider.call(
            user_input=text,
            messages=messages,
            mode=internal_mode,
            current_route=current_route,
        )
        latency_ms = int(time.time() * 1000) - start_ms

        # ── AI 호출 실패 체크 ──────────────────────────────
        if result.error:
            logger.warning(
                "event=voice_analyze_provider_error request_id=%s mode=%s error=%s latency_ms=%s",
                get_request_id(), mode, result.error, latency_ms,
            )
            return jsonify({
                "status": "V5020",
                "data": None,
                "message": "음성 분석 AI 호출에 실패했습니다."
            }), 502

        # ── 응답 필드 처리 ─────────────────────────────────
        confirmed_val = result.confirmed if mode == "LOW_VISION" else None
        confirmation_msg = result.confirmation_message

        logger.info(
            "event=voice_analyze_completed request_id=%s mode=%s intent=%s confirmed=%s latency_ms=%s",
            get_request_id(), mode, result.intent, confirmed_val, latency_ms,
        )

        return jsonify({
            "status": "S2000",
            "data": {
                "intent": (result.intent or "UNKNOWN").upper(),
                "placeName": result.place_name,
                "category": result.category,
                "bookmarkAction": result.bookmark_action,
                "departure": result.departure,
                "destination": result.destination,
                "reportType": result.report_type,
                "description": result.description,
                "confirmed": confirmed_val,
                "confirmationMessage": confirmation_msg,
            },
            "message": "정상 처리되었습니다."
        })

    except Exception as e:
        latency_ms = int(time.time() * 1000) - start_ms
        logger.exception(
            "event=voice_analyze_failed request_id=%s mode=%s latency_ms=%s",
            get_request_id(), mode, latency_ms,
        )
        return jsonify({
            "status": "V5020",
            "data": None,
            "message": "음성 분석 AI 호출에 실패했습니다."
        }), 502


# /api/voice/confirm 엔드포인트 폐기
# 확인 응답 판단은 /api/voice/analyze의 히스토리 기반으로 처리
# @app.route('/api/voice/confirm', methods=['POST'])
# def voice_confirm():
#     """시각장애인 모드 전용 - 확인 응답 판단 엔드포인트"""
#     body = request.get_json()
#
#     text = body.get("text", "").strip()
#     if not text:
#         return jsonify({
#             "success": False,
#             "confirmed": False,
#             "message": "다시 말씀해 주세요",
#             "error": "text 필드가 비어 있습니다",
#             "model": body.get("model", Config.DEFAULT_MODEL),
#             "latency_ms": 0
#         }), 400
#
#     model_key = body.get("model", Config.DEFAULT_MODEL)
#     provider = PROVIDERS.get(model_key)
#     if not provider:
#         return jsonify({
#             "success": False,
#             "confirmed": False,
#             "message": "다시 말씀해 주세요",
#             "error": f"지원하지 않는 모델입니다: {model_key}",
#             "model": model_key,
#             "latency_ms": 0
#         }), 400
#
#     start_ms = int(time.time() * 1000)
#     try:
#         result = provider.call(text, system_prompt=SYSTEM_PROMPT_CONFIRM)
#         latency_ms = int(time.time() * 1000) - start_ms
#         parsed = parse_json_response(result.raw_text)
#         confirmed = parsed.get("confirmed", False)
#         message = parsed.get("message", "다시 말씀해 주세요")
#         return jsonify({
#             "success": is_success("", parsed, prompt_type="confirm"),
#             "confirmed": confirmed,
#             "message": message,
#             "model": model_key,
#             "latency_ms": latency_ms
#         })
#     except Exception as e:
#         latency_ms = int(time.time() * 1000) - start_ms
#         logger.error(f"voice_confirm 오류: {e}")
#         return jsonify({
#             "success": False,
#             "confirmed": False,
#             "message": "다시 말씀해 주세요",
#             "error": str(e),
#             "model": model_key,
#             "latency_ms": latency_ms
#         }), 500


@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        "status": "healthy",
        "providers": list(PROVIDERS.keys()),
        "modes": ["MOBILITY_IMPAIRED", "LOW_VISION"],
        "endpoints": ["POST /voice/analyze"],
    })


if __name__ == '__main__':
    logger.info("=" * 60)
    logger.info("LLM Voice Server Starting...")
    logger.info("Providers: " + ", ".join(PROVIDERS.keys()))
    logger.info("=" * 60)
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)
