import re
import json


def parse_json_response(text: str) -> dict:
    text = re.sub(r'```json\s*', '', text)
    text = re.sub(r'```\s*', '', text)
    match = re.search(r'\{.*\}', text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group())
        except json.JSONDecodeError:
            return {}
    return {}


def clean_text(text: str) -> str:
    """STT 결과 마침표 등 후처리"""
    if not text:
        return text
    return re.sub(r'[.。、·]+$', '', text.strip())


def is_success(intent: str, parsed: dict, prompt_type: str = "mobility") -> bool:
    intent_upper = (intent or "").upper()

    always_success = {
        "SHOW_BOOKMARKS", "SHOW_FAVORITE_ROUTES", "LOGOUT",
        "NAVIGATION_END", "OPEN_MY_PAGE", "OPEN_MAP", "ASK", "UNKNOWN"
    }
    if intent_upper in always_success:
        return True

    if intent_upper == "PLACE_SEARCH":
        if parsed.get("confirmed") is True:
            return True
        return bool(parsed.get("place_name"))

    if intent_upper == "CATEGORY_SEARCH":
        return bool(parsed.get("category"))

    if intent_upper in ("BOOKMARK_ADD", "BOOKMARK_DELETE"):
        return bool(parsed.get("place_name"))

    if intent_upper == "NAVIGATE":
        return bool(parsed.get("destination"))

    if intent_upper == "REPORT":
        return bool(parsed.get("report_type"))

    return False