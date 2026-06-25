import sys
import os
import requests
import unittest
from unittest.mock import patch, MagicMock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from env_loader import load_runtime_env
load_runtime_env()

from app import app as flask_app
from providers.base_provider import LLMResponse

client = flask_app.test_client()


def post_analyze(text="부산역", mode="MOBILITY_IMPAIRED"):
    resp = client.post(
        "/voice/analyze",
        json={"text": text, "mode": mode},
        content_type="application/json",
    )
    return resp, resp.get_json()


def print_case(num, title, http_status, status_code, verdict, fail_reason=""):
    label = "PASS" if verdict else f"FAIL ({fail_reason})"
    print(f"케이스 {num}: {title}")
    print(f"  HTTP status : {http_status}")
    print(f"  status      : {status_code}")
    print(f"  판정        : {label}")


def run():
    results = []

    print("=== 에러 처리 테스트 ===\n")

    # ──────────────────────────────────────────────────────────────
    # 케이스 1: Gemini API timeout
    # ──────────────────────────────────────────────────────────────
    try:
        with patch("providers.gemini_provider.requests.post",
                   side_effect=requests.exceptions.Timeout()):
            resp, body = post_analyze()
        ok = resp.status_code == 502 and (body or {}).get("status") == "V5020"
        fail_reason = "" if ok else f"status={resp.status_code}, body_status={body.get('status') if body else None}"
        print_case(1, "Gemini timeout -> V5020 502", resp.status_code, (body or {}).get("status"), ok, fail_reason)
        results.append((1, ok, fail_reason))
    except Exception as e:
        print_case(1, "Gemini timeout -> V5020 502", "-", "-", False, str(e))
        results.append((1, False, str(e)))

    print()

    # ──────────────────────────────────────────────────────────────
    # 케이스 2: Gemini API 5xx (HTTPError)
    # ──────────────────────────────────────────────────────────────
    try:
        with patch("providers.gemini_provider.requests.post",
                   side_effect=requests.exceptions.HTTPError("500 Server Error")):
            resp, body = post_analyze()
        ok = resp.status_code == 502 and (body or {}).get("status") == "V5020"
        fail_reason = "" if ok else f"status={resp.status_code}, body_status={body.get('status') if body else None}"
        print_case(2, "Gemini 5xx -> V5020 502", resp.status_code, (body or {}).get("status"), ok, fail_reason)
        results.append((2, ok, fail_reason))
    except Exception as e:
        print_case(2, "Gemini 5xx -> V5020 502", "-", "-", False, str(e))
        results.append((2, False, str(e)))

    print()

    # ──────────────────────────────────────────────────────────────
    # 케이스 3: 네트워크 단절 (ConnectionError)
    # ──────────────────────────────────────────────────────────────
    try:
        with patch("providers.gemini_provider.requests.post",
                   side_effect=requests.exceptions.ConnectionError()):
            resp, body = post_analyze()
        ok = resp.status_code == 502 and (body or {}).get("status") == "V5020"
        fail_reason = "" if ok else f"status={resp.status_code}, body_status={body.get('status') if body else None}"
        print_case(3, "네트워크 단절 (ConnectionError) -> V5020 502", resp.status_code, (body or {}).get("status"), ok, fail_reason)
        results.append((3, ok, fail_reason))
    except Exception as e:
        print_case(3, "네트워크 단절 (ConnectionError) -> V5020 502", "-", "-", False, str(e))
        results.append((3, False, str(e)))

    print()

    # ──────────────────────────────────────────────────────────────
    # 케이스 4: functionCall 없이 텍스트만 반환 -> UNKNOWN fallback
    # ──────────────────────────────────────────────────────────────
    try:
        mock_resp = MagicMock()
        mock_resp.raise_for_status.return_value = None
        mock_resp.json.return_value = {
            "candidates": [{
                "content": {
                    "parts": [{"text": "죄송합니다. 이해하지 못했습니다."}]
                }
            }],
            "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5},
        }
        with patch("providers.gemini_provider.requests.post", return_value=mock_resp):
            resp, body = post_analyze()
        data = (body or {}).get("data") or {}
        ok = (
            resp.status_code == 200
            and (body or {}).get("status") == "S2000"
            and data.get("intent") == "UNKNOWN"
        )
        fail_reason = "" if ok else f"status={resp.status_code}, body_status={(body or {}).get('status')}, intent={data.get('intent')}"
        print_case(4, "functionCall 없는 응답 -> UNKNOWN fallback", resp.status_code, (body or {}).get("status"), ok, fail_reason)
        results.append((4, ok, fail_reason))
    except Exception as e:
        print_case(4, "functionCall 없는 응답 -> UNKNOWN fallback", "-", "-", False, str(e))
        results.append((4, False, str(e)))

    print()

    # ──────────────────────────────────────────────────────────────
    # 케이스 5: result.error 필드 반환 시 502
    # ──────────────────────────────────────────────────────────────
    try:
        error_result = LLMResponse(
            provider="gemini",
            raw_text="",
            error="Simulated provider error",
            success=False,
        )
        with patch("providers.gemini_provider.GeminiProvider.call", return_value=error_result):
            resp, body = post_analyze()
        ok = resp.status_code == 502 and (body or {}).get("status") == "V5020"
        fail_reason = "" if ok else f"status={resp.status_code}, body_status={body.get('status') if body else None}"
        print_case(5, "result.error 체크 -> V5020 502", resp.status_code, (body or {}).get("status"), ok, fail_reason)
        results.append((5, ok, fail_reason))
    except Exception as e:
        print_case(5, "result.error 체크 -> V5020 502", "-", "-", False, str(e))
        results.append((5, False, str(e)))

    print()

    # ──────────────────────────────────────────────────────────────
    # 전체 결과 요약
    # ──────────────────────────────────────────────────────────────
    passed = sum(1 for _, ok, _ in results if ok)
    failed = [(num, reason) for num, ok, reason in results if not ok]

    print("=== 전체 결과 요약 ===")
    print(f"통과: {passed}/5")
    if failed:
        print("실패:")
        for num, reason in failed:
            print(f"  케이스 {num}: {reason}")


if __name__ == "__main__":
    run()
