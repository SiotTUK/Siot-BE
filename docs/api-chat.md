# 챗봇 API

회원 전용입니다. 공통 규칙(응답 형식, CSRF)은 [conventions.md](conventions.md)를 따릅니다.
대화는 본인 것만 조회·사용할 수 있고, 다른 회원의 대화는 존재하지 않는 것처럼 404 `CHAT_CONVERSATION_NOT_FOUND` 입니다 (NFR-03).

| 메서드 | 경로 | 요구사항 | 설명 |
|---|---|---|---|
| POST | `/api/chat/conversations` | CB-03 | 새 대화 (201). 본문 `{ "language": "ko" }` 생략 가능 (회원 언어) |
| GET | `/api/chat/conversations?page=0&size=20` | CB-03 | 이전 대화 목록 (최근 대화 순, 제목 = 첫 질문) |
| GET | `/api/chat/conversations/{id}/messages` | CB-03, CB-17 | 대화 내용 (답변 근거 포함) |
| DELETE | `/api/chat/conversations/{id}` | CB-03 | 대화 삭제 (204). 질문·답변·출처를 모두 지우며 복구할 수 없음. 남의 대화·이미 지운 대화는 404 `CHAT_CONVERSATION_NOT_FOUND`. 지운 대화의 평가는 관리자 검토 목록(ADM-08)에서도 빠짐 |
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
                "referral": null,
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
| `ERROR` | Gemini 호출 실패·설정 없음 (CB-02). 문의처 안내 문구가 `content` 에 들어 있음 | 그대로 표시 + 관련 페이지 링크(`relatedPages`) + 다시 시도 버튼 |

`NO_EVIDENCE`·`ERROR` 도 HTTP 200 입니다 (대화 기록에 남음).

**응답 시간 (CB-02, 목표 평균 5초 이하)**: 검색·답변 생성에 걸린 시간을 상태별로 기록합니다. 관리자 로그인 후
`GET /actuator/metrics/siot.chatbot.answer` (평균 = `TOTAL_TIME / COUNT`, `?tag=status:ANSWERED` 로 상태별). 서버 로그에도 `Chatbot answered: status=... took=...ms` 가 남습니다.

### 출처 (CB-17)

화면에는 `documentName` 만 보이고, 누르면 `pageStart`~`pageEnd` 쪽과 `sourceUrl` 을 펼칩니다.
문서가 삭제된 뒤 예전 대화를 열면 `documentName` 등이 null 입니다.
답변 본문에는 `[1]` 같은 근거 번호를 쓰지 않습니다 (모델에게도 번호 대신 문서 이름·쪽으로 근거를 넘기고, 새어 나오면 서버가 지움).

### 대화 자체에 대한 질문 (메타 질문)

대화 기록·지난 답변·답변 근거를 묻는 질문은 문서를 다시 검색하지 않고 **전체 대화 기록과 그 답변을 만들 때 저장한 근거**(`chat_message_source`)로 답합니다.
지난 질문을 다시 검색하면 그때와 다른 자료가 나와, 엉뚱한 자료를 지난 답변의 근거처럼 보여 줄 수 있기 때문입니다. 4개 언어 규칙으로 가려냅니다.

| 질문 예 | 답 |
|---|---|
| 맨 처음 질문이 뭐야? / 두 번째 질문은 뭐였어? | 그 번째 질문 원문 |
| 첫 번째 질문에 뭐라고 답했어? | 그 답변 원문. 답하지 못한 턴이면 그 사실 |
| 첫 번째 답변의 근거는? / 아까 알려준 내용은 어디에 나와 있어? / 그 자료 몇 페이지야? | 저장된 근거를 `「문서 이름」 12~13쪽` + 원문 일부로. `sources` 에도 같은 근거 |
| 왜 그렇게 생각했어? / 첫 번째 답변에서 종량제 봉투를 언급한 이유는? | 저장된 근거 조각으로 Gemini 가 근거와 결론의 관계를 설명. 실패하면 근거 목록 |

- `answerStatus` 는 `ANSWERED`, 응급 연락처·담당 기관·관련 페이지는 붙이지 않습니다.
- "n번째"가 없으면 직전 질문을 가리킵니다. 근거·설명 질문이 메타 질문 턴을 가리키면 그 질문이 가리킨 원래 답변을 따라갑니다 ("첫 번째 답변의 근거는?" → "그 자료 몇 페이지야?" 는 첫 번째 답변).
- 근거 없이 답한 턴(`NO_EVIDENCE`·`ERROR`)이나 근거 문서가 지워진 경우에는 지어내지 않고 그 사실을 알립니다.

### 답변 언어 (CB-15)

서버가 질문의 글자로 언어를 판별해 **질문한 언어로 답합니다**. 대화 언어와 달라도 됩니다 (예: 한국어 대화에서 베트남어 질문 → 베트남어 답변).

| 판별 | 기준 |
|---|---|
| `ko` / `zh` | 한글 / 한자가 가장 많음 |
| `vi` | 라틴 문자 + 베트남어 성조·모음 글자, 또는 성조 없이 쓴 베트남어 단어(toi, khong …) |
| `en` | 라틴 문자 + 영어 기능어(how, what, the …) |
| 판별 못 함 | 숫자·기호만, 기능어 없는 짧은 영문(`CL-01`), 일본어 → **대화 언어** |

- 근거 없음(`NO_EVIDENCE`)·오류(`ERROR`) 안내 문구, 응급 연락처 라벨, 담당 기관 안내(`referral`), 관련 페이지 제목도 이 언어로 나옵니다.
- 답한 언어는 저장(`chat_message.language`)되어 대화 기록에서도 같은 언어로 보입니다. 이 기능 이전 답변은 대화 언어로 보입니다.

### 응급 연락처 (CB-09)

질문에 화재·부상·범죄 등 응급 관련 단어(4개 언어)가 있으면 답변의 `emergencyContacts` 에 119·112 가 대화 언어 라벨과 함께 들어 있습니다. 답변 **맨 위에** 크게 보여 주세요. 답변 자체는 평소처럼 만들고, 대화 기록에서도 다시 보입니다(`chat_message.is_emergency`).

```json
"emergencyContacts": [ { "number": "119", "label": "화재·구급" }, { "number": "112", "label": "경찰" } ]
```

### 담당 기관 안내 (CB-11)

비자 발급 가능 여부, 법률·의료·세금·노동 판단을 묻는 질문(4개 언어 단어 감지)이면 답변의 `referral` 에 "개인 상황은 판단할 수 없다"는 고정 문구(`notice`)와 담당 기관이 들어 있습니다. 아니면 `null` 입니다. Gemini 지시문과 별개로 서버가 감지해 붙이므로, 답변 내용과 상관없이 항상 표시해 주세요. 대화 기록에서도 다시 보입니다(`chat_message.referral_topics`).

| topic | 기관 | 감지 예 |
|---|---|---|
| `VISA` | ☎1345 외국인종합안내센터 | 비자·체류·연장 등 + "가능할까요 / 될까요 / Can I" ("비자 연장 서류는?" 같은 절차 질문은 제외) |
| `LEGAL` | ☎132 대한법률구조공단 | 불법인가요, 고소, 소송, 변호사, 처벌, 이길 수 있나요, 계약서에 서명해도 될까요 |
| `MEDICAL` | ☎119 구급상황관리센터 | 증상, 진단, 아파요, 약 먹어도, 부작용 |
| `TAX` | ☎126 국세청 국세상담센터 | 세금·세율·연말정산·환급 + "제 경우 / 더 유리한가요 / 얼마나 받을까요" ("연말정산은 언제?" 같은 제도 질문은 제외) |
| `LABOR` | ☎1350 고용노동부 고객상담센터 | 해고, 부당해고, 임금 체불, 월급을 못 받 |
| `WORK_INJURY` | ☎1588-0075 근로복지공단 | 산재, 산업재해, 일하다가 다쳤 |

```json
"referral": {
  "notice": "개인 상황에 대한 판단(비자 발급 가능 여부, 법률·의료·세금·노동 문제)은 해 드릴 수 없어요. 정확한 답은 아래 기관에 확인해 주세요.",
  "contacts": [ { "topic": "VISA", "number": "1345", "label": "외국인종합안내센터 (비자·체류)" } ]
}
```

질문 하나에 여러 종류가 있으면 `contacts` 에 VISA → LEGAL → MEDICAL → TAX → LABOR → WORK_INJURY 순서로 모두 들어 있습니다. 응급(`emergencyContacts`)과 함께 나올 수 있으며, 그때는 응급 연락처를 위에 보여 주세요.

### 관련 페이지 (CB-10)

`relatedPages` 에 최대 3개를 담습니다. 프론트엔드는 `type`·`code`(CL-01, LC-01-1 …)로 상세 화면 경로를 만들어 '관련 내용 보기' 버튼을 보여 줍니다. 찾는 순서:

1. **문서 연결**: 답변 근거 조각과 같은 문서의 겹치는 쪽을 근거로 삼는 항목(`guide_document_reference`).
   AI 오류(`ERROR`)로 답변을 만들지 못했어도 검색까지 됐으면 검색된 조각으로 찾습니다 (출처는 보이지 않음).
2. **제목 일치** (1에서 하나도 없을 때): 질문에 항목 제목(공백 무시)이나 제목의 첫 단어(3자 이상)가 들어 있는 항목.
   제목 전체가 맞는 항목, 긴 단어가 맞는 항목 순입니다 (예: "자동차세는 언제 내요?" → CL-10 자동차세 내기).
   문서 연결 데이터가 없는 생활수칙(`RULE`), 임베딩부터 실패한 오류 응답도 이것으로 링크가 나옵니다.

3. **행사** (`EVENT`): 질문에 행사 단어(행사·축제·공연·전시 / event·festival / 活动·节日 / sự kiện·lễ hội 등)가 있으면
   끝나지 않은 행사를 **앞에** 둡니다. `code` 는 `eventId` 이고 프론트엔드는 행사 상세(`/events/{eventId}`)로 보냅니다.
   - 대상: `status = 'ACTIVE'` 이고 종료일(없으면 시작일)이 오늘(한국 시간) 이후인 행사. 지난 행사·삭제된 행사는 나오지 않습니다.
   - 지역: 사용자에게 보여 주는 지역의 행사만 (기본 시흥, [api-events.md](api-events.md#사용자에게-보여-주는-지역)).
   - 순서: 질문에 들어 있는 제목 단어 수(많은 순) → 질문이 말한 카테고리의 행사("공연 있어요?" → 공연, EVE-10) → 다문화 행사 → 시작일 빠른 순.
   - 1·2에서 항목이 나왔으면 행사는 2개까지 (항목 1개 자리를 남김), 없으면 3개까지.
   - 제목은 대화 언어 번역, 없으면 한국어.
   - 행사는 RAG 문서에 없어 답변 근거와 연결할 수 없으므로 질문 단어로만 찾습니다. 그래서 답변이 `NO_EVIDENCE` 여도 행사 링크는 나옵니다.

체크리스트·생활 변화 할 일·생활수칙·행사(`CHECKLIST` / `LIFE_CHANGE_TASK` / `RULE` / `EVENT`)가 대상입니다.

**대화 기록**: 답변할 때 보여 준 링크를 `chat_message_related_page` 에 저장해 두고 그대로 다시 보여 줍니다 (제목도 그때의 값).
그래서 다시 볼 때 링크가 바뀌지 않지만, 그사이 끝나거나 삭제된 행사·항목의 링크도 남아 있습니다 — 상세 화면이 404 면 "삭제된 내용" 으로 처리해 주세요.
이 기능(2026-11-19) 이전 답변(`chat_message.related_pages_recorded = false`)만 예전처럼 볼 때 다시 찾습니다.

#### 행사 연결: 역할 B(행사 데이터)와 맞출 내용

챗봇은 `event`·`event_translation` 을 **읽기만** 합니다 (`EventRelatedPageFinder`). 아래 약속이 깨지면 링크가 틀리거나 사라지므로, 행사 수집·관리 구현 때 지켜 주세요.

| # | 약속 | 이유 |
|---|---|---|
| 1 | 사용자에게 보여 줄 행사는 `status = 'ACTIVE'` 이고 `region_code` 가 공개 지역(`siot.event.public-regions`). 관리자 삭제(EVE-09)·취소는 `ACTIVE` 가 아닌 값(예: `DELETED`)으로 남긴다 | 삭제 행사가 다시 수집되지 않도록(EVE-01 4번) 행을 남길 것이므로, 챗봇은 `ACTIVE` 만 본다. 공개 지역 밖의 행사는 상세가 404 |
| 2 | 재수집해도 `event_id` 는 바뀌지 않는다 (출처+원본 ID 로 **갱신**, 지우고 다시 넣지 않음) | 링크(`code`)와 대화 기록의 링크가 같은 행사를 가리켜야 함 |
| 3 | 모든 행사에 `ko` 번역 행이 있다. 다른 언어는 번역되면 추가 | 번역 전이거나 번역 실패해도 한국어 제목으로 링크를 보여 줌 |
| 4 | `starts_on`·`ends_on` 중 하나는 채운다 (하루 행사는 둘 다 같은 날) | 날짜가 모두 없는 행사는 끝났는지 알 수 없어 챗봇 링크에서 뺀다 |
| 5 | `is_multicultural` 은 다문화 행사 여부 (EVE-05 추천과 같은 기준) | 같은 점수면 다문화 행사를 먼저 보여 줌 |
| 6 | 행사 상세 화면 경로는 `/events/{eventId}`, 상세 API 는 `GET /api/events/{eventId}` | 프론트엔드가 `type=EVENT` 링크로 이동할 곳 |

정해지지 않은 것 (B 와 결정 후 반영):

- **거주 지역 우선**(EVE-05 1번): 온보딩에 지역이 없어서 지금은 지역을 보지 않습니다. 시흥시 행사를 먼저 보여 주려면 B 가 쓰는 `region_code` 값(예: 시흥시 코드)을 알려 주세요.
- **카테고리**(EVE-10 4번): 행사 API 분류 값이 정해지면 "공연 있어요?" 같은 질문을 분류로도 찾을 수 있습니다.
- 행사를 RAG 문서처럼 임베딩해 의미로 찾는 방식은 수집량·번역 비용을 보고 나중에 판단합니다.

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
3. **검색 (CB-14)**: 질문을 `gemini-embedding-001`(768차원, RETRIEVAL_QUERY)로 임베딩해 `document_chunk` 에서 코사인 거리로 가까운 조각 `top-k`(기본 5)개를 찾습니다. 사용 중인 문서(`document.is_active`)의 현재 버전(`document_version.is_current`) 중 반영이 끝난(`processing_status = 'COMPLETED'`) 버전의 조각만 대상입니다. 근사 인덱스(HNSW) 없이 이 조각 전체에서 정확히 가까운 순으로 찾습니다.
4. **근거 판단 (CB-12)**: 관련도(1 − 코사인 거리)가 `min-relevance`(기본 0.6) 이상인 조각만 근거로 씁니다. 하나도 없으면 Gemini 를 호출하지 않고 `NO_EVIDENCE`.
5. **답변 (CB-13)**: 근거 조각과 최근 3턴(질문 + 근거로 답한 답변)을 Gemini(`gemini-3.1-flash-lite`)에 넘겨 답변합니다. 앞 대화는 무엇을 가리키는지 이해하는 데만 쓰고, 사실은 근거 조각에서만 가져오게 합니다. 근거에 답이 없으면 모델이 `NO_ANSWER` 를 돌려주고, 이것도 `NO_EVIDENCE` 로 처리합니다. 답변 언어는 질문 언어, 판별이 안 되면 대화 언어입니다.
6. **저장**: 답변과 근거(최대 3개, `chat_message_source`)를 저장합니다.

Gemini 호출 중에는 DB 트랜잭션을 잡지 않습니다.

## 설정

| 환경변수 | 설명 | 기본값 |
|---|---|---|
| `GEMINI_PROJECT_ID` | Vertex AI 프로젝트 ID. 있으면 우선 사용 (인증은 ADC: VM 서비스 계정) | - |
| `GEMINI_LOCATION` | Vertex AI 리전 (gemini-3.x 는 global 만 제공) | `global` |
| `GEMINI_API_KEY` | Google AI Studio API 키 (`GEMINI_PROJECT_ID` 가 없을 때). 둘 다 비우면 모든 질문이 `ERROR` 안내로 응답 (앱은 정상 기동) | - |
| `GEMINI_CHAT_MODEL` | 답변 모델 | `gemini-3.1-flash-lite` |
| `CHATBOT_MIN_RELEVANCE` | 근거 최소 관련도 (0~1) | 0.6 |
| `CHATBOT_TOP_K` | 검색 조각 수 | 5 |
| `siot.chatbot.history-turns` | 이어지는 질문에 쓰는 최근 턴 수 (0이면 끔) | 3 |

`min-relevance` 는 실제 문서가 적재되면 평가 세트(CB-01)로 다시 맞춥니다.

## 문서 적재(역할 B)와의 약속

| 항목 | 약속 |
|---|---|
| 임베딩 | `gemini-embedding-001`, `outputDimensionality=768`, 문서는 `RETRIEVAL_DOCUMENT` (질문은 챗봇이 `RETRIEVAL_QUERY`). 저장 전 L2 정규화 권장. `embedding_model` 컬럼 = `gemini-embedding-001` (챗봇이 이 값으로 걸러 검색). 모델을 바꾸면 전체 재임베딩 |
| 상태 | `processing_status`: `PENDING` → `PROCESSING` → `COMPLETED` / `FAILED`. `COMPLETED` 뒤에만 `is_current = true`, 이전 버전은 같은 트랜잭션에서 false. 실패하면 이전 버전 유지 (ADM-04). 챗봇은 `is_current AND COMPLETED AND document.is_active` 만 검색. 현재 버전에서 내려온 버전 조각은 글만 남기고 `embedding` 은 비운다 |
| 필수 값 | `document.name` (화면 표시 이름), `document.source_url` (공개 원문 링크), `document_chunk.page_start/page_end` (PDF 기준 1부터), `chunk_no` (버전별 1부터) |
| 조각 | 목표 600자 / 최대 1,000자 / 100자 겹침. 제목·문단 경계에서 자르고, 조각 첫 줄에 절 제목, 표는 한 조각. 쪽을 넘으면 page_start~page_end 로 기록 |
| 언어 | 자료는 한국어 원문 그대로. 질문은 번역 없이 검색 (다국어 임베딩). 평가 세트(CB-01)에서 외국어 정답률 80% 미만이면 챗봇이 검색 전 한국어 변환을 추가 |

## 아직 없는 것 (다음 단계)

- 생활수칙의 관련 페이지 (CB-10): 문서 연결 데이터(`guide_document_reference`)가 없어 제목 일치로만 나옵니다. 연결 데이터를 넣으면 더 정확해집니다. 행사는 위 [관련 페이지](#관련-페이지-cb-10) 3번처럼 이미 나옵니다.

관리자 부정 평가·미응답 질문 목록(ADM-08)은 [관리자 API](api-admin.md) 에 있습니다. 질문 횟수는 제한하지 않습니다 (팀 결정).
