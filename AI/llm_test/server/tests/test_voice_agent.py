import sys
import os
import json

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from env_loader import load_runtime_env
load_runtime_env()

from providers.gemini_provider import GeminiProvider

provider = GeminiProvider()


def call(text, mode, history=None, current_route=None):
    if history is None:
        history = []
    messages = history + [{"role": "user", "content": text}]
    return provider.call(user_input=text, messages=messages, mode=mode, current_route=current_route)


def append_history(history, text, result):
    history.append({"role": "user", "content": text})
    history.append({"role": "assistant", "content": json.dumps({
        "intent": result.intent,
        "placeName": result.place_name,
        "category": result.category,
        "bookmarkAction": result.bookmark_action,
        "departure": result.departure,
        "destination": result.destination,
        "reportType": result.report_type,
        "description": result.description,
        "confirmed": result.confirmed,
        "confirmationMessage": result.confirmation_message,
    }, ensure_ascii=False)})


def print_turn(turn_num, text, result, verdict, fail_reason=""):
    label = "PASS" if verdict else f"FAIL ({fail_reason})"
    print(f"[{turn_num}턴] \"{text}\"")
    print(f"  intent             : {result.intent}")
    print(f"  place_name         : {result.place_name}")
    print(f"  category           : {result.category}")
    print(f"  departure          : {result.departure}")
    print(f"  destination        : {result.destination}")
    print(f"  report_type        : {result.report_type}")
    print(f"  bookmark_action    : {result.bookmark_action}")
    print(f"  confirmed          : {result.confirmed}")
    print(f"  confirmation_msg   : {result.confirmation_message}")
    print(f"  판정               : {label}")


def run_flows():
    results = []  # (flow_num, passed, fail_reason)

    # ──────────────────────────────────────────────────────────────
    # 흐름 1: 이동약자 - 장소 검색 (단턴)
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 1: 이동약자 - 장소 검색 (단턴) ===\n")
    try:
        r = call("부산역 어디야", "mobility")
        ok = r.intent == "PLACE_SEARCH" and r.place_name is not None
        fail_reason = "" if ok else f"intent={r.intent}, place_name={r.place_name}"
        print_turn(1, "부산역 어디야", r, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((1, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((1, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 2: 이동약자 - 카테고리 검색 (단턴)
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 2: 이동약자 - 카테고리 검색 (단턴) ===\n")
    try:
        r = call("근처 카페 알려줘", "mobility")
        ok = r.intent == "CATEGORY_SEARCH" and r.category is not None
        fail_reason = "" if ok else f"intent={r.intent}, category={r.category}"
        print_turn(1, "근처 카페 알려줘", r, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((2, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((2, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 3: 이동약자 - 제보 (2턴 멀티턴)
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 3: 이동약자 - 제보 (2턴 멀티턴) ===\n")
    try:
        history = []
        flow_ok = True
        fail_reasons = []

        r1 = call("제보할게요", "mobility", history)
        ok1 = r1.intent == "ASK" and r1.confirmation_message is not None
        if not ok1:
            fail_reasons.append(f"1턴: intent={r1.intent}")
            flow_ok = False
        print_turn(1, "제보할게요", r1, ok1, f"intent={r1.intent}" if not ok1 else "")
        append_history(history, "제보할게요", r1)

        r2 = call("계단이요", "mobility", history)
        ok2 = r2.intent == "REPORT" and r2.report_type == "STAIRS_STEP"
        if not ok2:
            fail_reasons.append(f"2턴: intent={r2.intent}, report_type={r2.report_type}")
            flow_ok = False
        print_turn(2, "계단이요", r2, ok2, f"intent={r2.intent}, report_type={r2.report_type}" if not ok2 else "")

        print(f"\n흐름 판정: {'PASS' if flow_ok else 'FAIL'}")
        results.append((3, flow_ok, ", ".join(fail_reasons)))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((3, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 4: 이동약자 - 길 안내 종료 (currentRoute 활용)
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 4: 이동약자 - 길 안내 종료 (currentRoute 활용) ===\n")
    try:
        r = call("안내 종료해줘", "mobility", current_route="navigation/guidance")
        ok = r.intent == "NAVIGATION_END"
        fail_reason = "" if ok else f"intent={r.intent}"
        print_turn(1, "안내 종료해줘", r, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((4, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((4, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 5: 이동약자 - confirmed 항상 None 검증
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 5: 이동약자 - confirmed 항상 None 검증 ===\n")
    try:
        r = call("부산역 어디야", "mobility")
        ok = r.confirmed is None and r.confirmation_message is None
        fail_reason = "" if ok else f"confirmed={r.confirmed}, confirmation_message={r.confirmation_message}"
        print_turn(1, "부산역 어디야", r, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((5, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((5, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 6: 저시력자 - 장소 검색 확인 흐름 (2턴)
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 6: 저시력자 - 장소 검색 확인 흐름 (2턴) ===\n")
    try:
        history = []
        flow_ok = True
        fail_reasons = []

        r1 = call("부산역 어디야", "visually", history)
        ok1 = r1.intent == "PLACE_SEARCH" and r1.confirmed is None and r1.confirmation_message is not None
        if not ok1:
            fail_reasons.append(f"1턴: intent={r1.intent}, confirmed={r1.confirmed}, confirmation_message={r1.confirmation_message}")
            flow_ok = False
        print_turn(1, "부산역 어디야", r1, ok1, fail_reasons[-1] if not ok1 else "")
        append_history(history, "부산역 어디야", r1)

        r2 = call("응", "visually", history)
        ok2 = r2.intent == "PLACE_SEARCH" and r2.confirmed is True
        if not ok2:
            fail_reasons.append(f"2턴: intent={r2.intent}, confirmed={r2.confirmed}")
            flow_ok = False
        print_turn(2, "응", r2, ok2, f"intent={r2.intent}, confirmed={r2.confirmed}" if not ok2 else "")

        print(f"\n흐름 판정: {'PASS' if flow_ok else 'FAIL'}")
        results.append((6, flow_ok, ", ".join(fail_reasons)))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((6, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 7: 저시력자 - 부정 후 재시도 (3턴)
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 7: 저시력자 - 부정 후 재시도 (3턴) ===\n")
    try:
        history = []
        flow_ok = True
        fail_reasons = []

        r1 = call("부산역 어디야", "visually", history)
        ok1 = r1.intent == "PLACE_SEARCH" and r1.confirmed is None
        if not ok1:
            fail_reasons.append(f"1턴: intent={r1.intent}, confirmed={r1.confirmed}")
            flow_ok = False
        print_turn(1, "부산역 어디야", r1, ok1, fail_reasons[-1] if not ok1 else "")
        append_history(history, "부산역 어디야", r1)

        r2 = call("아니 부산대학교", "visually", history)
        ok2 = r2.intent == "PLACE_SEARCH" and r2.place_name == "부산대학교" and r2.confirmed is None
        if not ok2:
            fail_reasons.append(f"2턴: intent={r2.intent}, place_name={r2.place_name}, confirmed={r2.confirmed}")
            flow_ok = False
        print_turn(2, "아니 부산대학교", r2, ok2, fail_reasons[-1] if not ok2 else "")
        append_history(history, "아니 부산대학교", r2)

        r3 = call("응", "visually", history)
        ok3 = r3.intent == "PLACE_SEARCH" and r3.confirmed is True
        if not ok3:
            fail_reasons.append(f"3턴: intent={r3.intent}, confirmed={r3.confirmed}")
            flow_ok = False
        print_turn(3, "응", r3, ok3, f"intent={r3.intent}, confirmed={r3.confirmed}" if not ok3 else "")

        print(f"\n흐름 판정: {'PASS' if flow_ok else 'FAIL'}")
        results.append((7, flow_ok, ", ".join(fail_reasons)))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((7, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 8: 이동약자 - 영문 브랜드명 복원
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 8: 이동약자 - 영문 브랜드명 복원 ===\n")
    try:
        r = call("지에스이십오 찾아줘", "mobility")
        ok = r.intent == "PLACE_SEARCH" and r.place_name == "GS25"
        fail_reason = "" if ok else f"intent={r.intent}, place_name={r.place_name}"
        print_turn(1, "지에스이십오 찾아줘", r, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((8, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((8, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 9: 의도 불명
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 9: 의도 불명 ===\n")
    try:
        r = call("그냥", "mobility")
        ok = r.intent == "UNKNOWN"
        fail_reason = "" if ok else f"intent={r.intent}"
        print_turn(1, "그냥", r, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((9, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((9, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 10: 이동약자 - 북마크 추가
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 10: 이동약자 - 북마크 추가 ===\n")
    try:
        r = call("부산역 북마크 추가해줘", "mobility")
        ok = r.intent == "BOOKMARK_ADD" and r.place_name is not None and r.bookmark_action == "add"
        fail_reason = "" if ok else f"intent={r.intent}, place_name={r.place_name}, bookmark_action={r.bookmark_action}"
        print_turn(1, "부산역 북마크 추가해줘", r, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((10, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((10, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 11: 이동약자 - 북마크 삭제
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 11: 이동약자 - 북마크 삭제 ===\n")
    try:
        r = call("부산역 북마크 삭제해줘", "mobility")
        ok = r.intent == "BOOKMARK_DELETE" and r.place_name is not None and r.bookmark_action == "delete"
        fail_reason = "" if ok else f"intent={r.intent}, place_name={r.place_name}, bookmark_action={r.bookmark_action}"
        print_turn(1, "부산역 북마크 삭제해줘", r, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((11, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((11, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 전체 결과 요약
    # ──────────────────────────────────────────────────────────────
    passed = sum(1 for _, ok, _ in results if ok)
    failed = [(num, reason) for num, ok, reason in results if not ok]

    print("=== 전체 결과 요약 ===")
    print(f"통과: {passed}/11")
    if failed:
        print("실패 흐름:")
        for num, reason in failed:
            print(f"  흐름 {num}: {reason}")


if __name__ == "__main__":
    run_flows()
