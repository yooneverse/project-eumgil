from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional


@dataclass
class LLMResponse:
    provider: str                              # "gemini" | "claude" | "gpt_mini"
    raw_text: str                              # LLM 원본 응답 텍스트
    intent: Optional[str] = None              # "place_search" | "unknown"
    place_name: Optional[str] = None          # 장소명 (place_search)
    departure: Optional[str] = None           # 출발지 (route_search)
    destination: Optional[str] = None         # 도착지 (route_search)
    facility_type: Optional[str] = None       # 시설 유형 (nearby_search)
    category: Optional[str] = None            # 카테고리명 (CATEGORY_SEARCH)
    bookmark_action: Optional[str] = None     # 북마크 액션 "add"/"delete"
    report_type: Optional[str] = None         # 제보 유형
    description: Optional[str] = None         # 제보 설명
    confirmed: Optional[bool] = None          # 시각장애인 모드 확인 응답 (true/false/null)
    confirmation_message: Optional[str] = None  # TTS용 확인 메시지
    llm_latency_ms: float = 0.0               # LLM 추론 시간만 (ms)
    total_latency_ms: float = 0.0             # STT 시작 ~ 버튼 생성 전체 시간 (ms)
    input_tokens: int = 0                     # 입력 토큰 수
    output_tokens: int = 0                    # 출력 토큰 수
    success: bool = False                     # 의도 추출 성공 여부
    error: Optional[str] = None              # 실패 시 에러 메시지


class BaseProvider(ABC):
    @abstractmethod
    def call(self, user_input: str, system_prompt: str = "", messages: list = None, mode: str = "mobility", current_route: str = None) -> LLMResponse:
        pass

    @property
    @abstractmethod
    def provider_name(self) -> str:
        pass
