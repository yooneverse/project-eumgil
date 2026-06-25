import os
import time
import requests
from providers.base_provider import BaseProvider, LLMResponse
from providers.utils import clean_text


class GeminiProvider(BaseProvider):
    BASE_URL = (
        os.getenv("GMS_BASE_URL", "https://generativelanguage.googleapis.com").rstrip("/")
        + "/v1beta/models/gemini-2.5-flash:generateContent"
    )

    SYSTEM_PROMPT_MOBILITY_FC = """
당신은 부산이음길 이동약자 음성 에이전트입니다.
사용자의 발화를 분석해 적절한 도구(Tool)를 호출하세요.

[도구 선택 규칙]
- 장소명이 명확하면: confirm_place_search 호출
- "북마크 목록" 또는 "저장된 경로"/"즐겨찾는 경로": confirm_show_bookmarks 호출
- "로그아웃": confirm_logout 호출
- "제보", "신고" 발화했지만 유형 모름: ask_report_type 호출
- 제보 유형 확인됨: confirm_report 호출
- currentRoute=navigation/guidance 상황에서 "종료", "그만": confirm_navigation_end 호출
- "마이페이지", "내 정보": confirm_open_my_page 호출
- "지도", "홈으로": confirm_open_map 호출
- 장소명/카테고리 등 정보가 부족: ask_place_name 호출
- 의도 파악 불가: unknown 호출

이동약자 모드에서는 사용자 확인 과정이 없습니다.
confirmed 파라미터는 절대 사용하지 마세요.
ask_ 계열 도구를 호출할 때는 반드시 confirmation_message를 포함해야 합니다.
사용자가 다음에 무엇을 말해야 하는지 안내하는 문구를 생성하세요.
예시:
- ask_place_name → '어떤 장소를 찾으시나요?'
- ask_report_type → '어떤 문제인가요? 계단·단차, 점자블록, 인도 없음, 경사로, 인도폭, 기타 중 말씀해 주세요'
대화 히스토리가 있으면 이전 맥락을 참고하세요.
currentRoute가 제공되면 현재 화면 위치로 활용하세요.
"""

    SYSTEM_PROMPT_VISUALLY_FC = """
당신은 부산이음길 저시력자 음성 에이전트입니다.
사용자의 발화를 분석해 적절한 도구(Tool)를 호출하세요.

[도구 선택 규칙]
1. 사용자가 기능을 요청하면 → confirm_ 계열 도구를 confirmed 파라미터 없이 호출 (확인 질문 생성)
2. 사용자가 긍정 응답("응", "네", "맞아", "좋아")하면
   → 히스토리의 직전 confirm_ 도구를 confirmed=true로 재호출
3. 사용자가 부정 응답("아니", "아니요")하고 새 정보를 주면
   → 새 정보로 confirm_ 도구를 confirmed 없이 재호출 (새 확인 질문)
4. 사용자가 부정 응답만 하면 → unknown 호출
5. 정보가 부족하면 → ask_ 계열 도구 호출
6. 의도 파악 불가 → unknown 호출

대화 히스토리를 반드시 참고하세요.
confirmed 파라미터 없이 confirm_ 도구를 호출할 때는 반드시 confirmation_message를 포함해야 합니다. confirmation_message를 생략하면 사용자에게 안내 문구가 전달되지 않습니다.
"""

    VOICE_TOOLS = [{"functionDeclarations": [
        # ── ask_ 계열 ──────────────────────────────────────────────────────────
        {
            "name": "ask_place_name",
            "description": "장소명을 알 수 없을 때 사용자에게 장소명을 물어봅니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "사용자에게 전달할 질문 문구"
                    }
                },
                "required": ["confirmation_message"]
            }
        },
        {
            "name": "ask_report_type",
            "description": "제보 유형을 알 수 없을 때 사용자에게 유형을 물어봅니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "어떤 문제인가요? 계단·단차, 점자블록, 인도 없음, 경사로, 인도폭, 기타 중 말씀해주세요"
                    }
                },
                "required": ["confirmation_message"]
            }
        },
        # ── confirm_ 계열 ──────────────────────────────────────────────────────
        {
            "name": "confirm_place_search",
            "description": "장소명을 검색합니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "place_name": {
                        "type": "STRING",
                        "description": "장소명. 한국어 발음 표기는 반드시 로마자로 복원할 것. 예: 지에스이십오→GS25, 씨유→CU, 케이에프씨→KFC, 에이치앤엠→H&M, 이케아→IKEA"
                    },
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "확인 질문. confirmed 있으면 null"
                    },
                    "confirmed": {
                        "type": "BOOLEAN",
                        "description": "긍정=true, 부정=false. 확인 요청 단계면 생략"
                    }
                },
                "required": ["place_name"]
            }
        },
        {
            "name": "confirm_show_bookmarks",
            "description": "북마크 및 저장된(즐겨찾는) 경로 목록을 표시합니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "확인 질문. confirmed 있으면 null"
                    },
                    "confirmed": {
                        "type": "BOOLEAN",
                        "description": "긍정=true, 부정=false. 확인 요청 단계면 생략"
                    }
                },
                "required": []
            }
        },
        {
            "name": "confirm_logout",
            "description": "로그아웃을 수행합니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "확인 질문. confirmed 있으면 null"
                    },
                    "confirmed": {
                        "type": "BOOLEAN",
                        "description": "긍정=true, 부정=false. 확인 요청 단계면 생략"
                    }
                },
                "required": []
            }
        },
        {
            "name": "confirm_report",
            "description": "장애물·위험 요소를 제보합니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "report_type": {
                        "type": "STRING",
                        "description": "STAIRS_STEP | BRAILLE_BLOCK | SIDEWALK_MISSING | RAMP | SIDEWALK_WIDTH | OTHER_OBSTACLE"
                    },
                    "description": {
                        "type": "STRING",
                        "description": "음성에서 추출한 제보 설명"
                    },
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "확인 질문. confirmed 있으면 null"
                    },
                    "confirmed": {
                        "type": "BOOLEAN",
                        "description": "긍정=true, 부정=false. 확인 요청 단계면 생략"
                    }
                },
                "required": ["report_type"]
            }
        },
        {
            "name": "confirm_navigation_end",
            "description": "진행 중인 경로 안내를 종료합니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "확인 질문. confirmed 있으면 null"
                    },
                    "confirmed": {
                        "type": "BOOLEAN",
                        "description": "긍정=true, 부정=false. 확인 요청 단계면 생략"
                    }
                },
                "required": []
            }
        },
        {
            "name": "confirm_open_my_page",
            "description": "마이페이지로 이동합니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "확인 질문. confirmed 있으면 null"
                    },
                    "confirmed": {
                        "type": "BOOLEAN",
                        "description": "긍정=true, 부정=false. 확인 요청 단계면 생략"
                    }
                },
                "required": []
            }
        },
        {
            "name": "confirm_open_map",
            "description": "지도 화면으로 이동합니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "확인 질문. confirmed 있으면 null"
                    },
                    "confirmed": {
                        "type": "BOOLEAN",
                        "description": "긍정=true, 부정=false. 확인 요청 단계면 생략"
                    }
                },
                "required": []
            }
        },
        # ── 공통 ──────────────────────────────────────────────────────────────
        {
            "name": "unknown",
            "description": "의도를 파악할 수 없거나 부정 응답만 한 경우 사용합니다.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "confirmation_message": {
                        "type": "STRING",
                        "description": "재입력 안내 문구"
                    }
                },
                "required": ["confirmation_message"]
            }
        },
    ]}]

    def __init__(self):
        self.gms_key = os.getenv("GMS_KEY")

    @property
    def provider_name(self):
        return "gemini"

    def _parse_function_call(self, fn_name: str, args: dict) -> dict:
        mapping = {
            "ask_place_name":               {"intent": "ASK"},
            "ask_report_type":              {"intent": "ASK"},
            "confirm_place_search":         {"intent": "PLACE_SEARCH",           "confirmed": args.get("confirmed")},
            "confirm_show_bookmarks":       {"intent": "SHOW_BOOKMARKS",         "confirmed": args.get("confirmed")},
            "confirm_logout":               {"intent": "LOGOUT",                 "confirmed": args.get("confirmed")},
            "confirm_report":               {"intent": "REPORT",                 "confirmed": args.get("confirmed")},
            "confirm_navigation_end":       {"intent": "NAVIGATION_END",         "confirmed": args.get("confirmed")},
            "confirm_open_my_page":         {"intent": "OPEN_MY_PAGE",           "confirmed": args.get("confirmed")},
            "confirm_open_map":             {"intent": "OPEN_MAP",               "confirmed": args.get("confirmed")},
            "unknown":                      {"intent": "UNKNOWN"},
        }
        result = mapping.get(fn_name, {"intent": "UNKNOWN"})
        result["place_name"]           = args.get("place_name")
        result["category"]             = args.get("category")
        result["departure"]            = args.get("departure")
        result["destination"]          = args.get("destination")
        result["report_type"]          = args.get("report_type")
        result["description"]          = args.get("description")
        result["confirmation_message"] = clean_text(args.get("confirmation_message") or "") or None
        return result

    def call(self, user_input: str, system_prompt: str = "", messages: list = None, mode: str = "mobility", current_route: str = None) -> LLMResponse:
        system_prompt_final = (
            self.SYSTEM_PROMPT_VISUALLY_FC if mode == "visually"
            else self.SYSTEM_PROMPT_MOBILITY_FC
        )
        if current_route:
            system_prompt_final += f"\n현재 사용자 화면: {current_route}"

        if messages:
            contents = [
                {
                    "role": "model" if m["role"] == "assistant" else m["role"],
                    "parts": [{"text": m["content"]}]
                }
                for m in messages
            ]
        else:
            contents = [{"role": "user", "parts": [{"text": user_input}]}]

        body = {
            "system_instruction": {"parts": [{"text": system_prompt_final}]},
            "contents": contents,
            "tools": self.VOICE_TOOLS,
            "tool_config": {"function_calling_config": {"mode": "ANY"}},
        }
        headers = {
            "Content-Type": "application/json",
            "x-goog-api-key": self.gms_key,
        }

        start = time.time()
        try:
            resp = requests.post(self.BASE_URL, headers=headers, json=body, timeout=15)
            resp.raise_for_status()
            data = resp.json()
            latency_ms = (time.time() - start) * 1000

            part = data["candidates"][0]["content"]["parts"][0]
            fn_call = part.get("functionCall")
            if fn_call:
                fn_name = fn_call["name"]
                args = fn_call.get("args", {})
            else:
                fn_name = "unknown"
                args = {"confirmation_message": "다시 말씀해 주세요"}

            parsed = self._parse_function_call(fn_name, args)
            raw_text = f"{fn_name}({args})"

            input_tokens = data["usageMetadata"]["promptTokenCount"]
            output_tokens = data["usageMetadata"]["candidatesTokenCount"]

            return LLMResponse(
                provider="gemini",
                raw_text=raw_text,
                intent=parsed["intent"],
                place_name=parsed.get("place_name"),
                category=parsed.get("category"),
                departure=parsed.get("departure"),
                destination=parsed.get("destination"),
                facility_type=None,
                report_type=parsed.get("report_type"),
                description=parsed.get("description"),
                bookmark_action=parsed.get("bookmark_action"),
                confirmed=parsed.get("confirmed"),
                confirmation_message=parsed.get("confirmation_message"),
                llm_latency_ms=latency_ms,
                total_latency_ms=0.0,
                input_tokens=input_tokens,
                output_tokens=output_tokens,
                success=True,
                error=None,
            )
        except Exception as e:
            return LLMResponse(
                provider="gemini",
                raw_text="",
                intent="UNKNOWN",
                success=False,
                error=str(e) or type(e).__name__,
            )
