# 챗봇 API

회원 전용입니다. 공통 규칙(응답 형식, CSRF)은 [conventions.md](conventions.md)를 따릅니다.
대화는 본인 것만 조회·사용할 수 있고, 다른 회원의 대화는 존재하지 않는 것처럼 404 `CHAT_CONVERSATION_NOT_FOUND` 입니다 (NFR-03).

| 메서드 | 경로 | 요구사항 | 설명 |
|---|---|---|---|
| POST | `/api/chat/conversations` | CB-03 | 새 대화 (201). 본문 `{ "language": "ko" }` 생략 가능 (회원 언어) |
| GET | `/api/chat/conversations?page=0&size=20` | CB-03 | 이전 대화 목록 (최근 대화 순, 제목 = 첫 질문) |
| GET | `/api/chat/conversations/{id}/messages` | CB-03, CB-17 | 대화 내용 (답변 근거 포함) |
| POST | `/api/chat/conversations/{id}/messages` | CB-16 | 질문 → 답변 |
| PUT | `/api/chat/conversations/{id}/messages/{messageId}/feedback` | CB-04 | 답변 평가 (204) |
| GET | `/api/chat/suggestions?language=` | CB-08 | 사용자 유형별 추천 질문 3개 |

## 질문

```http
POST /api/chat/conversations/12/messages
{ "content": "외국인등록은 언제까지 해야 하나요?" }
```

**200**

```json
{ "data": {
  "question": { "messageId": 31, "role": "USER", "content": "외국인등록은 언제까지 해야 하나요?",
                "answerStatus": null, "sources": [], "createdAt": "2026-11-14T16:10:02+09:00" },
  "answer":   { "messageId": 32, "role": "ASSISTANT", "content": "입국 후 90일 이내에 ...",
                "answerStatus": "ANSWERED",
                "sources": [ { "order": 1, "documentName": "시흥시 외국인주민 생활안내",
                               "pageStart": 12, "pageEnd": 13,
                               "sourceUrl": "https://...", "relevanceScore": 0.82 } ],
                "relatedPages": [ { "type": "CHECKLIST", "code": "CL-01", "title": "외국인등록" } ],
                "emergencyContacts": [],
                "helpful": null,
                "createdAt": "2026-11-14T16:10:04+09:00" },
  "masked": false } }
```

- 질문은 1~1000자 (`CHAT_QUESTION_LENGTH`).
- `masked` 가 true 면 질문에서 개인정보를 가렸다는 뜻입니다. 화면에 "개인정보는 가려서 저장했어요" 같은 안내를 보여 줍니다.

### answerStatus

| 값 | 의미 | 화면 |
|---|---|---|
| `ANSWERED` | 검색된 자료를 근거로 답변 (CB-13) | 답변 + 출처 (`sources`) |
| `NO_EVIDENCE` | 관련 자료를 찾지 못함 (CB-12). 답을 지어내지 않고 문의처(☎1345 등)를 안내하는 문구가 `content` 에 들어 있음 | 그대로 표시, 출처 없음 |
| `ERROR` | Gemini 호출 실패·키 미설정 (CB-02). 안내 문구가 `content` 에 들어 있음 | 그대로 표시 + 다시 시도 버튼 |

`NO_EVIDENCE`·`ERROR` 도 HTTP 200 입니다 (대화 기록에 남음).

### 출처 (CB-17)

화면에는 `documentName` 만 보이고, 누르면 `pageStart`~`pageEnd` 쪽과 `sourceUrl` 을 펼칩니다.
문서가 삭제된 뒤 예전 대화를 열면 `documentName` 등이 null 입니다.

### 응급 연락처 (CB-09)

질문에 화재·부상·범죄 등 응급 관련 단어(4개 언어)가 있으면 답변의 `emergencyContacts` 에 119·112 가 대화 언어 라벨과 함께 들어 있습니다. 답변 **맨 위에** 크게 보여 주세요. 답변 자체는 평소처럼 만들고, 대화 기록에서도 다시 보입니다(`chat_message.is_emergency`).

```json
"emergencyContacts": [ { "number": "119", "label": "화재·구급" }, { "number": "112", "label": "경찰" } ]
```

### 관련 페이지 (CB-10)

답변 근거 조각과 같은 문서의 겹치는 쪽을 근거로 삼는 체크리스트 항목(`guide_document_reference`)이 있으면 `relatedPages` 에 최대 3개를 담습니다. 프론트엔드는 `code`(CL-01, LC-01-1 …)로 상세 화면 경로를 만들어 '관련 내용 보기' 버튼을 보여 줍니다. 행사·생활수칙은 아직 문서와 연결하는 데이터가 없어 대상이 아닙니다.

## 답변 평가 (CB-04)

```http
PUT /api/chat/conversations/12/messages/32/feedback
{ "helpful": false, "comment": "주소 변경 방법이 없어요" }
```

- 204. 본인 대화의 **답변**만 평가할 수 있고(질문이면 400 `CHAT_FEEDBACK_NOT_ALLOWED`), 다른 대화의 메시지면 404 `CHAT_MESSAGE_NOT_FOUND`.
- 다시 보내면 덮어씁니다. `comment` 는 선택, 500자 이하, 개인정보는 가려서 저장합니다.
- '도움이 안 됐어요' 답변과 근거 없음(`NO_EVIDENCE`) 질문은 관리자 화면(ADM-08)에서 확인할 대상입니다.

## 추천 질문 (CB-08)

```http
GET /api/chat/suggestions?language=en
```

```json
{ "data": { "userType": "NEW", "language": "en",
            "questions": [ "How do I register as a foreign resident?", "How do I report a change of address after moving?", "How do I throw away trash?" ] } }
```

- `userType`: NEW(처음 왔어요) → 외국인등록·체류지 변경신고·쓰레기 배출 / LIVING(살고 있어요) → 주민세·체류기간 연장·건강보험 / 온보딩 전(null) → 공통.
- `language` 생략 시 회원 언어. ko/en/zh/vi 외에는 400 `INVALID_INPUT`.
- 버튼을 누르면 그 문장을 질문으로 보냅니다.

## 처리 순서

1. **개인정보 가리기 (CB-05)**: 외국인등록번호·주민등록번호 형식, 여권번호, 전화번호를 `[등록번호]` `[여권번호]` `[전화번호]` 로 바꿉니다. 이름·주소·이메일은 가리지 않습니다. AI 로 보내는 질문과 DB 에 저장하는 질문 모두 가린 값입니다.
2. **이어지는 질문 (CB-07)**: 같은 대화에 앞선 질문이 있으면 최근 3턴을 보고 질문을 검색용 독립 질문으로 바꿉니다. 예: "그거 온라인으로도 돼요?" → "외국인등록은 온라인으로도 신청할 수 있나요?". 첫 질문은 바꾸지 않고, 바꾸기에 실패하면 직전 질문을 앞에 붙여 검색합니다. 저장되는 질문은 사용자가 쓴 그대로입니다.
3. **검색 (CB-14)**: 질문을 `gemini-embedding-001`(768차원, RETRIEVAL_QUERY)로 임베딩해 `document_chunk` 에서 코사인 거리로 가까운 조각 `top-k`(기본 5)개를 찾습니다. 사용 중인 문서(`document.is_active`)의 현재 버전(`document_version.is_current`) 조각만 대상입니다.
4. **근거 판단 (CB-12)**: 관련도(1 − 코사인 거리)가 `min-relevance`(기본 0.6) 이상인 조각만 근거로 씁니다. 하나도 없으면 Gemini 를 호출하지 않고 `NO_EVIDENCE`.
5. **답변 (CB-13)**: 근거 조각과 최근 3턴(질문 + 근거로 답한 답변)을 Gemini(`gemini-2.5-flash`)에 넘겨 답변합니다. 앞 대화는 무엇을 가리키는지 이해하는 데만 쓰고, 사실은 근거 조각에서만 가져오게 합니다. 근거에 답이 없으면 모델이 `NO_ANSWER` 를 돌려주고, 이것도 `NO_EVIDENCE` 로 처리합니다. 답변 언어는 질문 언어, 판별이 안 되면 대화 언어입니다.
6. **저장**: 답변과 근거(최대 3개, `chat_message_source`)를 저장합니다.

Gemini 호출 중에는 DB 트랜잭션을 잡지 않습니다.

## 설정

| 환경변수 | 설명 | 기본값 |
|---|---|---|
| `GEMINI_API_KEY` | Google AI Studio API 키. 비우면 모든 질문이 `ERROR` 안내로 응답 (앱은 정상 기동) | - |
| `GEMINI_CHAT_MODEL` | 답변 모델 | `gemini-2.5-flash` |
| `CHATBOT_MIN_RELEVANCE` | 근거 최소 관련도 (0~1) | 0.6 |
| `CHATBOT_TOP_K` | 검색 조각 수 | 5 |
| `siot.chatbot.history-turns` | 이어지는 질문에 쓰는 최근 턴 수 (0이면 끔) | 3 |

`min-relevance` 는 실제 문서가 적재되면 평가 세트(CB-01)로 다시 맞춥니다.

## 문서 적재(역할 B)와의 약속

- 조각 임베딩은 **같은 모델 `gemini-embedding-001`, 768차원**으로 만들고 `embedding_model` 컬럼에 모델 이름을 넣습니다. 문서는 `RETRIEVAL_DOCUMENT` 용도로 임베딩합니다.
- 새 버전은 반영(추출·분할·임베딩)에 **성공한 뒤에만** `is_current = true` 로 바꾸고 이전 버전은 false 로 바꿉니다 (ADM-04). 챗봇은 `is_current` 만 봅니다.
- 출처 표시를 위해 `document.name`, `document.source_url`, `document_chunk.page_start/page_end` 를 채웁니다.

## 아직 없는 것 (다음 단계)

담당 기관 안내 강화(CB-11), 질문 언어 판별 강화(CB-15), 관리자 부정 평가·미응답 질문 목록(ADM-08). 질문 횟수는 제한하지 않습니다 (팀 결정).
