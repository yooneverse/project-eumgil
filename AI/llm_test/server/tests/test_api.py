import sys
import json
import requests

BASE_URL = "http://localhost:5000"


def call_api(text, mode, history=None, current_route=None):
    body = {"text": text, "mode": mode, "history": history or []}
    if current_route:
        body["currentRoute"] = current_route
    resp = requests.post(f"{BASE_URL}/voice/analyze", json=body, timeout=20)
    return resp


def append_history(history, text, data):
    history.append({"role": "user", "content": text})
    history.append({"role": "assistant", "content": json.dumps({
        "intent": data["intent"],
        "placeName": data["placeName"],
        "category": data["category"],
        "bookmarkAction": data["bookmarkAction"],
        "departure": data["departure"],
        "destination": data["destination"],
        "reportType": data["reportType"],
        "description": data["description"],
        "confirmed": data["confirmed"],
        "confirmationMessage": data["confirmationMessage"],
    }, ensure_ascii=False)})


def print_turn(turn_num, text, http_status, resp_body, verdict, fail_reason=""):
    label = "PASS" if verdict else f"FAIL ({fail_reason})"
    data = resp_body.get("data") or {}
    print(f"[{turn_num}턴] \"{text}\"")
    print(f"  HTTP status        : {http_status}")
    print(f"  status             : {resp_body.get('status')}")
    print(f"  intent             : {data.get('intent')}")
    print(f"  placeName          : {data.get('placeName')}")
    print(f"  category           : {data.get('category')}")
    print(f"  confirmed          : {data.get('confirmed')}")
    print(f"  confirmationMsg    : {data.get('confirmationMessage')}")
    print(f"  reportType         : {data.get('reportType')}")
    print(f"  bookmarkAction     : {data.get('bookmarkAction')}")
    print(f"  판정               : {label}")


def run_flows():
    results = []  # (flow_num, passed, fail_reason)

    # 서버 기동 확인
    try:
        requests.get(f"{BASE_URL}/health", timeout=5)
    except requests.exceptions.ConnectionError:
        print("서버가 실행 중이지 않습니다. python app.py로 먼저 서버를 실행하세요.")
        sys.exit(1)

    # ──────────────────────────────────────────────────────────────
    # 흐름 1: 이동약자 - 장소 검색 (단턴)
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 1: 이동약자 - 장소 검색 (단턴) ===\n")
    try:
        resp = call_api("부산역 어디야", "MOBILITY_IMPAIRED")
        body = resp.json()
        data = body.get("data") or {}
        ok = (
            resp.status_code == 200
            and body.get("status") == "S2000"
            and data.get("intent") == "PLACE_SEARCH"
            and data.get("placeName") is not None
        )
        fail_reason = "" if ok else f"status={resp.status_code}, status_code={body.get('status')}, intent={data.get('intent')}, placeName={data.get('placeName')}"
        print_turn(1, "부산역 어디야", resp.status_code, body, ok, fail_reason)
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
        resp = call_api("근처 카페 알려줘", "MOBILITY_IMPAIRED")
        body = resp.json()
        data = body.get("data") or {}
        ok = (
            resp.status_code == 200
            and data.get("intent") == "CATEGORY_SEARCH"
            and data.get("category") is not None
        )
        fail_reason = "" if ok else f"intent={data.get('intent')}, category={data.get('category')}"
        print_turn(1, "근처 카페 알려줘", resp.status_code, body, ok, fail_reason)
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

        resp1 = call_api("제보할게요", "MOBILITY_IMPAIRED", history)
        body1 = resp1.json()
        data1 = body1.get("data") or {}
        ok1 = data1.get("intent") == "ASK" and data1.get("confirmationMessage") is not None
        if not ok1:
            fail_reasons.append(f"1턴: intent={data1.get('intent')}")
            flow_ok = False
        print_turn(1, "제보할게요", resp1.status_code, body1, ok1, fail_reasons[-1] if not ok1 else "")
        append_history(history, "제보할게요", data1)

        resp2 = call_api("계단이요", "MOBILITY_IMPAIRED", history)
        body2 = resp2.json()
        data2 = body2.get("data") or {}
        ok2 = data2.get("intent") == "REPORT" and data2.get("reportType") == "STAIRS_STEP"
        if not ok2:
            fail_reasons.append(f"2턴: intent={data2.get('intent')}, reportType={data2.get('reportType')}")
            flow_ok = False
        print_turn(2, "계단이요", resp2.status_code, body2, ok2, fail_reasons[-1] if not ok2 else "")

        print(f"\n흐름 판정: {'PASS' if flow_ok else 'FAIL'}")
        results.append((3, flow_ok, ", ".join(fail_reasons)))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((3, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 4: 이동약자 - 길 안내 종료 (currentRoute)
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 4: 이동약자 - 길 안내 종료 (currentRoute) ===\n")
    try:
        resp = call_api("안내 종료해줘", "MOBILITY_IMPAIRED", current_route="navigation/guidance")
        body = resp.json()
        data = body.get("data") or {}
        ok = data.get("intent") == "NAVIGATION_END"
        fail_reason = "" if ok else f"intent={data.get('intent')}"
        print_turn(1, "안내 종료해줘", resp.status_code, body, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((4, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((4, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 5: 이동약자 - confirmed/confirmationMessage 항상 null 검증
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 5: 이동약자 - confirmed/confirmationMessage 항상 null 검증 ===\n")
    try:
        resp = call_api("부산역 어디야", "MOBILITY_IMPAIRED")
        body = resp.json()
        data = body.get("data") or {}
        ok = data.get("confirmed") is None and data.get("confirmationMessage") is None
        fail_reason = "" if ok else f"confirmed={data.get('confirmed')}, confirmationMessage={data.get('confirmationMessage')}"
        print_turn(1, "부산역 어디야", resp.status_code, body, ok, fail_reason)
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

        resp1 = call_api("부산역 어디야", "LOW_VISION", history)
        body1 = resp1.json()
        data1 = body1.get("data") or {}
        ok1 = (
            data1.get("intent") == "PLACE_SEARCH"
            and data1.get("confirmed") is None
            and data1.get("confirmationMessage") is not None
        )
        if not ok1:
            fail_reasons.append(f"1턴: intent={data1.get('intent')}, confirmed={data1.get('confirmed')}, confirmationMessage={data1.get('confirmationMessage')}")
            flow_ok = False
        print_turn(1, "부산역 어디야", resp1.status_code, body1, ok1, fail_reasons[-1] if not ok1 else "")
        append_history(history, "부산역 어디야", data1)

        resp2 = call_api("응", "LOW_VISION", history)
        body2 = resp2.json()
        data2 = body2.get("data") or {}
        ok2 = data2.get("intent") == "PLACE_SEARCH" and data2.get("confirmed") is True
        if not ok2:
            fail_reasons.append(f"2턴: intent={data2.get('intent')}, confirmed={data2.get('confirmed')}")
            flow_ok = False
        print_turn(2, "응", resp2.status_code, body2, ok2, fail_reasons[-1] if not ok2 else "")

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

        resp1 = call_api("부산역 어디야", "LOW_VISION", history)
        body1 = resp1.json()
        data1 = body1.get("data") or {}
        ok1 = data1.get("intent") == "PLACE_SEARCH" and data1.get("confirmed") is None
        if not ok1:
            fail_reasons.append(f"1턴: intent={data1.get('intent')}, confirmed={data1.get('confirmed')}")
            flow_ok = False
        print_turn(1, "부산역 어디야", resp1.status_code, body1, ok1, fail_reasons[-1] if not ok1 else "")
        append_history(history, "부산역 어디야", data1)

        resp2 = call_api("아니 부산대학교", "LOW_VISION", history)
        body2 = resp2.json()
        data2 = body2.get("data") or {}
        ok2 = (
            data2.get("intent") == "PLACE_SEARCH"
            and data2.get("placeName") == "부산대학교"
            and data2.get("confirmed") is None
        )
        if not ok2:
            fail_reasons.append(f"2턴: intent={data2.get('intent')}, placeName={data2.get('placeName')}, confirmed={data2.get('confirmed')}")
            flow_ok = False
        print_turn(2, "아니 부산대학교", resp2.status_code, body2, ok2, fail_reasons[-1] if not ok2 else "")
        append_history(history, "아니 부산대학교", data2)

        resp3 = call_api("응", "LOW_VISION", history)
        body3 = resp3.json()
        data3 = body3.get("data") or {}
        ok3 = data3.get("intent") == "PLACE_SEARCH" and data3.get("confirmed") is True
        if not ok3:
            fail_reasons.append(f"3턴: intent={data3.get('intent')}, confirmed={data3.get('confirmed')}")
            flow_ok = False
        print_turn(3, "응", resp3.status_code, body3, ok3, fail_reasons[-1] if not ok3 else "")

        print(f"\n흐름 판정: {'PASS' if flow_ok else 'FAIL'}")
        results.append((7, flow_ok, ", ".join(fail_reasons)))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((7, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 8: 영문 브랜드명 복원
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 8: 영문 브랜드명 복원 ===\n")
    try:
        resp = call_api("지에스이십오 찾아줘", "MOBILITY_IMPAIRED")
        body = resp.json()
        data = body.get("data") or {}
        ok = data.get("intent") == "PLACE_SEARCH" and data.get("placeName") == "GS25"
        fail_reason = "" if ok else f"intent={data.get('intent')}, placeName={data.get('placeName')}"
        print_turn(1, "지에스이십오 찾아줘", resp.status_code, body, ok, fail_reason)
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
        resp = call_api("그냥", "MOBILITY_IMPAIRED")
        body = resp.json()
        data = body.get("data") or {}
        ok = data.get("intent") == "UNKNOWN"
        fail_reason = "" if ok else f"intent={data.get('intent')}"
        print_turn(1, "그냥", resp.status_code, body, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((9, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((9, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 10: STT 마침표 후처리
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 10: STT 마침표 후처리 ===\n")
    try:
        resp = call_api("부산역 어디야.", "MOBILITY_IMPAIRED")
        body = resp.json()
        data = body.get("data") or {}
        ok = resp.status_code == 200 and data.get("intent") == "PLACE_SEARCH"
        fail_reason = "" if ok else f"status={resp.status_code}, intent={data.get('intent')}"
        print_turn(1, "부산역 어디야.", resp.status_code, body, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((10, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((10, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 11: 입력 검증 - 빈 text
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 11: 입력 검증 - 빈 text ===\n")
    try:
        resp = call_api("", "MOBILITY_IMPAIRED")
        body = resp.json()
        ok = resp.status_code == 400 and body.get("status") == "C4000"
        fail_reason = "" if ok else f"status={resp.status_code}, status_code={body.get('status')}"
        print_turn(1, "", resp.status_code, body, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((11, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((11, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 12: 입력 검증 - 잘못된 mode
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 12: 입력 검증 - 잘못된 mode ===\n")
    try:
        resp = call_api("부산역 어디야", "INVALID_MODE")
        body = resp.json()
        ok = resp.status_code == 400 and body.get("status") == "C4000"
        fail_reason = "" if ok else f"status={resp.status_code}, status_code={body.get('status')}"
        print_turn(1, "부산역 어디야", resp.status_code, body, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((12, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((12, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 13: 이동약자 - 북마크 추가
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 13: 이동약자 - 북마크 추가 ===\n")
    try:
        resp = call_api("부산역 북마크 추가해줘", "MOBILITY_IMPAIRED")
        body = resp.json()
        data = body.get("data") or {}
        ok = (
            resp.status_code == 200
            and data.get("intent") == "BOOKMARK_ADD"
            and data.get("placeName") is not None
            and data.get("bookmarkAction") == "add"
        )
        fail_reason = "" if ok else f"intent={data.get('intent')}, placeName={data.get('placeName')}, bookmarkAction={data.get('bookmarkAction')}"
        print_turn(1, "부산역 북마크 추가해줘", resp.status_code, body, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((13, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((13, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 흐름 14: 이동약자 - 북마크 삭제
    # ──────────────────────────────────────────────────────────────
    print("=== 흐름 14: 이동약자 - 북마크 삭제 ===\n")
    try:
        resp = call_api("부산역 북마크 삭제해줘", "MOBILITY_IMPAIRED")
        body = resp.json()
        data = body.get("data") or {}
        ok = (
            resp.status_code == 200
            and data.get("intent") == "BOOKMARK_DELETE"
            and data.get("placeName") is not None
            and data.get("bookmarkAction") == "delete"
        )
        fail_reason = "" if ok else f"intent={data.get('intent')}, placeName={data.get('placeName')}, bookmarkAction={data.get('bookmarkAction')}"
        print_turn(1, "부산역 북마크 삭제해줘", resp.status_code, body, ok, fail_reason)
        print(f"\n흐름 판정: {'PASS' if ok else 'FAIL'}")
        results.append((14, ok, fail_reason))
    except Exception as e:
        print(f"  오류: {e}")
        results.append((14, False, str(e)))
    print("---\n")

    # ──────────────────────────────────────────────────────────────
    # 전체 결과 요약
    # ──────────────────────────────────────────────────────────────
    passed = sum(1 for _, ok, _ in results if ok)
    failed = [(num, reason) for num, ok, reason in results if not ok]

    print("=== 전체 결과 요약 ===")
    print(f"통과: {passed}/14")
    if failed:
        print("실패 흐름:")
        for num, reason in failed:
            print(f"  흐름 {num}: {reason}")


if __name__ == "__main__":
    run_flows()
