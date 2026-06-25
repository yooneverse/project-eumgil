# LLM Test — AI 음성 명령 추론 서버

Android STT 텍스트를 받아 LLM으로 의미를 추론하고 intent와 파라미터를 JSON으로 반환한다.
백엔드 서버가 내부적으로 호출하며, 프론트는 직접 호출하지 않는다.

```
프론트 (Android)
    ↓  STT 텍스트
백엔드 서버
    ↓  LLM 추론 요청
AI 서버 (여기)
    ↓  intent + 파라미터 반환
백엔드 서버
    ↓  장소명 → 좌표 변환, 경로/장소 API 호출
프론트 (Android)
```

---

## 디렉토리 구조

```
llm_test/
├── server/                     # Flask AI 서버
│   ├── app.py                  # 진입점 — 엔드포인트 정의
│   ├── config.py               # 서버 설정 (HOST, PORT, DEFAULT_MODEL)
│   ├── environment.yaml        # conda 환경 정의
│   ├── requirements.txt        # pip 의존 패키지
│   ├── .env.example            # 루트 .env.dev/.env.prod에 넣을 AI env 예시
│   │
│   ├── providers/              # LLM 모델별 구현
│   │   ├── base_provider.py    # LLMResponse 데이터클래스 + BaseProvider 인터페이스
│   │   ├── utils.py            # SYSTEM_PROMPT, parse_json_response, is_success
│   │   ├── gemini_provider.py  # Gemini 2.5 Flash
│   │   ├── claude_provider.py  # Claude Haiku 4.5
│   │   └── gpt_mini_provider.py # GPT-5 mini
│   │
│   ├── utils/                  # 공통 유틸리티
│   │   ├── logger.py           # 로깅 설정
│   │   ├── cost_calculator.py  # GMS 크레딧 비용 계산
│   │   └── result_logger.py    # API 호출 결과 JSON 저장
│   │
│   └── tests/
│       ├── test_batch.py       # 3개 모델 × 27개 프롬프트 배치 테스트
│       ├── results/            # /api/chat/llm 호출 결과 JSON
│       ├── test_audio/         # 테스트용 음성 파일
│       └── test_results/       # 배치 테스트 Markdown 결과
│
└── FE/                         # Android 테스트 앱 (PoC용)
    └── app/src/main/java/com/example/llmtest/
        ├── ModeSelectActivity.kt   # 시작 화면 — 보행약자 / 시각장애인 선택
        ├── WalkActivity.kt         # 보행약자 모드 (단발성 호출)
        ├── VisuallyActivity.kt     # 시각장애인 모드 (멀티턴 + TTS)
        └── network/
            ├── VoiceApiClient.kt       # Retrofit 클라이언트
            └── models/
                ├── VoiceAnalyzeRequest.kt
                └── VoiceAnalyzeResponse.kt
```

---

## 지원 모델 (GMS 프록시)

| 모델 키 | 실제 모델 | Input 단가 | Output 단가 |
|---------|-----------|-----------|------------|
| `gemini` | gemini-2.5-flash | 0.003 / 1K tokens | 0.025 / 1K tokens |
| `claude` | claude-haiku-4-5-20251001 | 0.01 / 1K tokens | 0.05 / 1K tokens |
| `gpt_mini` | gpt-5-mini | 0.0025 / 1K tokens | 0.02 / 1K tokens |

---

## 설치 및 실행

### 1. 환경변수 설정

실제 값은 저장소 루트의 `.env.dev` 또는 `.env.prod`에서 관리합니다.
AI 전용 예시는 `server/.env.example`에 있습니다.

```bash
# 개발용
cp AI/llm_test/server/.env.example .env.dev

# 운영용
cp AI/llm_test/server/.env.example .env.prod
```

최소 예시:

```dotenv
GMS_KEY=your_gms_key_here
DEFAULT_MODEL=gemini
```

### 2. 패키지 설치

```bash
# conda
conda env create -f server/environment.yaml
conda activate llmtest

# 또는 pip
pip install -r server/requirements.txt
```

### 3. 서버 실행

```bash
cd server/
APP_ENV=dev python app.py
```

기본 포트: `5000`

- `APP_ENV=dev`면 루트 `.env.dev`
- `APP_ENV=local`도 루트 `.env.dev`로 정규화됩니다.
- `APP_ENV=prod`면 루트 `.env.prod`
- `APP_ENV`가 없으면 기본으로 루트 `.env.dev`를 먼저 찾습니다.
- 기존 `server/.env`가 있으면 마지막 fallback으로만 사용합니다.

---

## 엔드포인트

### `GET /health`

서버 상태 및 로드된 provider 목록 확인

**Response**
```json
{
  "status": "healthy",
  "providers": ["gemini", "claude", "gpt_mini"],
  "modes": ["MOBILITY_IMPAIRED", "LOW_VISION"],
  "endpoints": ["POST /voice/analyze"]
}
```

---

### `POST /voice/analyze`

백엔드 연동용 메인 엔드포인트. STT 텍스트를 받아 LLM으로 의미 추론.

**Request**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `text` | string | Y | STT 변환 텍스트 |
| `mode` | string | Y | `MOBILITY_IMPAIRED` \| `LOW_VISION` |
| `model` | string | N | `gemini` \| `claude` \| `gpt_mini` (기본값: `gemini`) |
| `history` | array | N | 이전 대화 목록. `LOW_VISION` 전용. 기본값 `[]`. `MOBILITY_IMPAIRED`에서는 무시 |

```json
{
  "text": "부산역 어디야",
  "mode": "MOBILITY_IMPAIRED"
}
```

**history 형식** (`LOW_VISION` 멀티턴 시)

```json
[
  { "role": "user",      "content": "부산역 어디야" },
  { "role": "assistant", "content": "{\"intent\":\"PLACE_SEARCH\",\"placeName\":\"부산역\",\"confirmed\":null,\"confirmationMessage\":\"부산역을 찾으시나요?\"}" }
]
```

> `assistant`의 `content`는 이전 응답 JSON을 문자열로 직렬화한 값

---

**Response 필드**

| 필드 | 타입 | Null 가능 | 설명 |
|------|------|-----------|------|
| `intent` | string | N | `PLACE_SEARCH` \| `UNKNOWN` |
| `placeName` | string | Y | 추출된 장소명. `UNKNOWN`이면 `null` |
| `confirmed` | boolean | Y | `LOW_VISION` 전용. `true`=확인완료 / `false`=부정 또는 판단불가 / `null`=미확인. `MOBILITY_IMPAIRED`는 항상 `null` |
| `confirmationMessage` | string | Y | `LOW_VISION` 전용 TTS 문구. `MOBILITY_IMPAIRED`는 항상 `null` |
| `success` | boolean | N | 의도 추출 성공 여부 |
| `model` | string | N | 사용된 모델 키 |
| `mode` | string | N | 사용된 mode 값 |
| `latency_ms` | int | N | LLM 응답 시간 (ms) |
| `error` | string | Y | 실패 시 에러 메시지 |

---

**보행약자 — PLACE_SEARCH**
```json
{
  "success": true,
  "intent": "PLACE_SEARCH",
  "placeName": "부산역",
  "confirmed": null,
  "confirmationMessage": null,
  "model": "gemini",
  "mode": "MOBILITY_IMPAIRED",
  "latency_ms": 1538
}
```

**시각장애인 1턴 — 장소 추출**
```json
{
  "success": true,
  "intent": "PLACE_SEARCH",
  "placeName": "부산역",
  "confirmed": null,
  "confirmationMessage": "부산역을 찾으시나요?",
  "model": "gemini",
  "mode": "LOW_VISION",
  "latency_ms": 1859
}
```

**시각장애인 2턴 — 긍정 확인**
```json
{
  "success": true,
  "intent": "PLACE_SEARCH",
  "placeName": "부산역",
  "confirmed": true,
  "confirmationMessage": null,
  "model": "gemini",
  "mode": "LOW_VISION",
  "latency_ms": 963
}
```

**시각장애인 — UNKNOWN**
```json
{
  "success": true,
  "intent": "UNKNOWN",
  "placeName": null,
  "confirmed": false,
  "confirmationMessage": "찾으시는 장소를 다시 말씀해 주세요",
  "model": "gemini",
  "mode": "LOW_VISION",
  "latency_ms": 870
}
```

**실패 (400/500)**
```json
{
  "success": false,
  "intent": "UNKNOWN",
  "placeName": null,
  "confirmed": null,
  "confirmationMessage": null,
  "error": "에러 메시지",
  "model": "gemini",
  "mode": "MOBILITY_IMPAIRED",
  "latency_ms": 0
}
```

---

## 서비스 흐름

### 보행약자 (`MOBILITY_IMPAIRED`)

```
음성 입력 → POST /voice/analyze (mode: MOBILITY_IMPAIRED)
  → PLACE_SEARCH: placeName 반환 → 백엔드에서 장소 검색 API 호출
  → UNKNOWN: 검색 없이 종료
```

- history 무시, 단발성 1회 호출
- `confirmed`, `confirmationMessage` 항상 `null`

### 시각장애인 (`LOW_VISION`)

```
[1턴] POST /voice/analyze (mode: LOW_VISION, history: [])
  → PLACE_SEARCH: confirmationMessage → TTS 재생
  → UNKNOWN: "찾으시는 장소를 다시 말씀해 주세요" TTS → 재시도

[2턴~] POST /voice/analyze (mode: LOW_VISION, history: 이전 대화 포함)
  → confirmed: true  → 백엔드에서 장소 검색 API 호출
  → confirmed: null  → 새 장소 추출 → TTS 재생 → 다음 턴
  → UNKNOWN          → history 초기화 → TTS 재생 → 재시도
```

- history는 FE 메모리에서 관리, 서버에 저장하지 않음
- 앱 세션 종료 또는 UNKNOWN 응답 시 history 초기화

---

## Intent 분류

| intent | 설명 | 추출 필드 |
|--------|------|-----------|
| `PLACE_SEARCH` | 특정 장소 이름 검색 | `placeName` |
| `UNKNOWN` | 의도 파악 불가 — 재입력 유도 | — |

**성공 조건** (`is_success`):
- `PLACE_SEARCH`: `placeName`이 존재할 때
- `UNKNOWN`: 항상 `true` (정상 처리로 간주)
- `confirmed: true`인 경우: 항상 `true`

---

## 채택 프롬프트 (테스트 결과 기준)

| 모드 | 채택 프롬프트 | 테스트 성공률 |
|------|-------------|-------------|
| `MOBILITY_IMPAIRED` | `claude_B` | 전 모델 100% |
| `LOW_VISION` | `claude_visually_B` | 전 모델 100% |

테스트 일시: 2026-04-28

---

## 테스트 실행

### 배치 테스트 (3개 모델 비교)

```bash
cd server/
python tests/test_batch.py
```

결과 파일: `server/tests/test_results/batch_test_YYYY-MM-DD_HH-MM-SS.md`

---

## 환경변수 목록

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `GMS_KEY` | GMS API 인증 키 (필수) | — |
| `HOST` | 서버 바인딩 주소 | `0.0.0.0` |
| `PORT` | 서버 포트 | `5000` |
| `DEBUG` | Flask 디버그 모드 | `True` |
| `DEFAULT_MODEL` | 모델 미지정 시 기본값 | `gemini` |

환경변수 로딩 우선순위:
1. 프로세스에 이미 주입된 runtime env
2. `ENV_FILE`이 지정한 파일
3. `APP_ENV` 또는 `SPRING_PROFILES_ACTIVE`에 맞는 루트 `.env.<env>`
4. `APP_ENV`가 없을 때 기본 루트 `.env.dev`
5. legacy fallback `server/.env`
