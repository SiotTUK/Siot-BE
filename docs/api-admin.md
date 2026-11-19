# 관리자 API

`/api/admin/**` 는 관리자(`role = ADMIN`)만 사용할 수 있습니다. 로그인 안 함 401, 일반 회원 403 (ADM-01).

| 메서드 | 경로 | 요구사항 | 설명 |
|---|---|---|---|
| GET | `/api/admin/members?email=&role=&page=&size=` | ADM-01 | 회원 검색 (이메일 부분 일치, 권한, 가입 최신순) |
| PUT | `/api/admin/members/{memberId}/role` | ADM-01 | 권한 변경 `{ "role": "ADMIN" \| "USER" }` |
| GET | `/api/admin/chat/reviews?type=&reviewed=&page=&size=` | ADM-08 | 부정 평가 답변·근거 없음 질문 목록 |
| PATCH | `/api/admin/chat/reviews/{messageId}` | ADM-08 | 처리 여부·메모 `{ "reviewed": true, "adminMemo": "..." }` |
| GET | `/api/admin/guides/{code}/translations/{language}` | CHK-06 | 항목 상세 문구 조회 (한 언어) |
| PUT | `/api/admin/guides/{code}/translations/{language}` | CHK-06, NTF-07 | 항목 상세 문구 저장. `noticeChanged` 로 실제 안내 내용 변경 여부 표시 |
| GET | `/api/admin/documents?page=&size=` | ADM-03 | 문서 목록 (최근 등록 순, 현재 버전·최근 업로드 버전 상태) |
| POST | `/api/admin/documents` | ADM-02 | 새 문서 등록 (multipart: `file`, `name`, `sourceUrl`, `publishedOn`) |
| PATCH | `/api/admin/documents/{documentId}` | ADM-03 | 문서 정보 수정 `{ "name", "sourceUrl", "publishedOn", "active" }` (보낸 값만) |
| POST | `/api/admin/documents/{documentId}/versions` | ADM-02 | 새 버전 업로드 (multipart: `file`, `changeSummary`) |
| GET | `/api/admin/documents/{documentId}/versions` | ADM-04, NTF-06 | 버전 목록과 반영 상태 |
| POST | `/api/admin/document-versions/{versionId}/retry` | ADM-04 | 실패한 버전 다시 반영 |

## 관리자 지정 (ADM-01)

- **첫 관리자**: 환경변수 `SIOT_ADMIN_EMAILS` (쉼표 구분, 대소문자 무관). 앱이 시작할 때 이미 가입한 회원은 관리자로 올리고, 아직 가입 전이면 가입하는 순간 관리자가 됩니다.
- **권한 변경**: 다른 관리자가 `PUT /api/admin/members/{id}/role` 로 바꿉니다. 바뀐 회원의 **기존 세션은 모두 끊기고** 다시 로그인해야 새 권한이 적용됩니다.
- 자기 자신의 관리자 권한은 내릴 수 없습니다 (400 `ADMIN_SELF_DEMOTION`). 없는 회원은 404 `MEMBER_NOT_FOUND`.
- `SIOT_ADMIN_EMAILS` 에 있는 회원은 권한을 내려도 앱이 다시 시작될 때 관리자로 돌아갑니다. 관리자를 영구히 빼려면 환경변수에서도 지웁니다.

## 부정 평가·미응답 질문 (ADM-08)

`type`: `ALL`(기본) / `NEGATIVE`(도움이 안 됐어요) / `NO_EVIDENCE`(자료에서 답을 찾지 못함)
`reviewed`: `false` = 미처리만, `true` = 처리 완료만, 생략 = 전체. 최신순.

```json
{ "data": { "content": [ {
  "messageId": 32, "conversationId": 12, "language": "en",
  "reason": "NEGATIVE",
  "question": "How do I pay car tax?",
  "answer": "Pay it in June.", "answerStatus": "ANSWERED",
  "helpful": false, "feedbackComment": "This is wrong",
  "sources": [ { "order": 1, "documentName": "...", "pageStart": 30, "pageEnd": 30, "sourceUrl": "...", "relevanceScore": 0.71 } ],
  "reviewed": false, "adminMemo": null, "reviewedBy": null, "reviewedAt": null,
  "createdAt": "2026-11-14T16:10:04+09:00" } ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false } }
```

- 질문·답변·의견은 개인정보를 가린 값만 보입니다 (CB-05). 회원 이메일 등 신원 정보는 넣지 않습니다.
- 처리 기록: `PATCH` → 처리 여부, 메모(1000자 이하), 처리한 관리자 ID, 처리 시각이 저장됩니다. 검토 대상이 아닌 답변이면 404 `CHAT_REVIEW_NOT_FOUND`.

## 항목 문구 저장 (CHK-06, NTF-07)

체크리스트 항목·생활 변화 할 일·생활수칙의 상세 문구를 언어(`ko`/`en`/`zh`/`vi`)별로 저장합니다. `code` 는 항목 ID(CL-01, LC-01-1 …)입니다.

```http
PUT /api/admin/guides/CL-01/translations/ko
{ "title": "외국인등록", "summary": null, "content": "...", "badge": "입국 후 90일 이내",
  "place": "관할 출입국·외국인관서", "docs": ["여권", "사진 1장"], "fee": "30,000원",
  "law": "출입국관리법 제31조", "penalty": "과태료", "sourceName": "하이코리아",
  "noticeChanged": true }
```

**200** `{ "data": { "code": "CL-01", "language": "ko", "title": "외국인등록", ..., "noticeChangedAt": "2026-11-14T22:30:00+09:00", "updatedAt": "2026-11-14T22:30:00+09:00" } }`

- 보낸 값으로 **전부 바꿉니다** (빠진 필드·빈 문자열은 지움). 화면은 조회한 값을 고친 뒤 통째로 보냅니다.
- `noticeChanged` (필수):
  - `true` = 기한·장소·준비물·수수료 등 **실제 안내 내용이 바뀜** → `guide.notice_changed_at` 을 지금으로 기록합니다. 상세 화면의 '변경됨' 표시는 이 값을 씁니다.
  - `false` = 오타 수정 같은 단순 수정 → `notice_changed_at` 은 그대로입니다.
  - 어느 쪽이든 `updated_at`(CHK-06 수정일)과 수정한 관리자(`updated_by`)는 바뀝니다.
- 아직 없는 언어면 새로 만듭니다. 조회(GET)는 없는 언어면 404 `GUIDE_TRANSLATION_NOT_FOUND`.
- 없거나 삭제한 항목 404 `GUIDE_NOT_FOUND`, 지원하지 않는 언어·제목 없음·`noticeChanged` 없음 400 `INVALID_INPUT`.
- 변경 알림 발송(NTF-07 1·4번: 대상 회원에게 알림)은 아직 없습니다. 지금은 변경 시각 기록까지만 합니다.

## 문서 등록·교체·반영 (ADM-02~04, NTF-06)

챗봇이 근거로 검색하는 PDF 를 관리합니다. 업로드하면 파일을 저장하고 바로 응답하며(버전 `PENDING`), 반영은 뒤에서 한 건씩 진행됩니다.

```
PENDING(대기) → PROCESSING(처리 중) → COMPLETED(완료) / FAILED(실패)
단계: EXTRACT(텍스트 추출) → SPLIT(분할) → EMBEDDING(임베딩) → INDEX(검색 반영)
```

- **모든 단계가 성공해야** 새 버전이 현재 버전(`current: true`)이 되고 이전 버전은 검색에서 빠집니다. 실패하면 이전 버전을 계속 씁니다.
- 예전 버전과 조각은 지우지 않습니다 (답변 출처가 사라지지 않게, CB-17). 문서를 안 쓰려면 `PATCH` 로 `active: false`.
- 화면 안내 문구는 문서를 바꿔도 자동으로 바뀌지 않습니다. 관리자가 검토 후 [항목 문구 저장](#항목-문구-저장-chk-06-ntf-07)으로 고칩니다 (ADM-02, NTF-07).
- 반영 결과(NTF-06)는 버전 목록에서 확인합니다. `uploadedAt`(파일 업로드 완료)과 `processedAt`(반영이 끝난 시각, 성공·실패 모두)을 구분합니다.

### 새 문서 등록 `POST /api/admin/documents` (multipart/form-data) → 201

| 필드 | 필수 | 설명 |
|---|---|---|
| `file` | O | PDF (30MB 이하) |
| `name` | O | 문서명 (255자 이하). 챗봇 출처에 그대로 보입니다 |
| `sourceUrl` | O | 공개 원문 링크 (`http(s)://`) |
| `publishedOn` | | 발행일 `2026-03-01` |

```json
{ "data": { "documentId": 3, "name": "시흥시 외국인주민 생활안내", "sourceUrl": "https://...", "publishedOn": "2026-03-01",
  "active": true, "currentVersion": null,
  "latestVersion": { "versionId": 12, "versionNo": 1, "status": "PENDING", "processedAt": null },
  "createdAt": "2026-11-20T10:00:00+09:00", "updatedAt": "2026-11-20T10:00:00+09:00" } }
```

### 버전 목록 `GET /api/admin/documents/{documentId}/versions` (최신 먼저)

```json
{ "data": [ {
  "versionId": 13, "documentId": 3, "versionNo": 2, "originalFilename": "guide-2026.pdf",
  "changeType": "REPLACE", "changeSummary": "2026년 기준 반영",
  "status": "FAILED", "current": false,
  "failedStage": "EMBEDDING", "failureReason": "Gemini 임베딩 실패: ...",
  "chunkCount": 0, "uploadedBy": 1,
  "uploadedAt": "2026-11-20T10:05:00+09:00", "processedAt": "2026-11-20T10:07:12+09:00" } ] }
```

- `changeType`: `NEW`(새 문서 등록) / `REPLACE`(기존 문서 교체).
- 텍스트가 없는 PDF(스캔 이미지)는 `EXTRACT` 단계에서 실패합니다. 글자를 선택할 수 있는 PDF 로 다시 올립니다.

### 오류

| 상황 | 응답 |
|---|---|
| PDF 가 아님 | 400 `DOCUMENT_FILE_INVALID` |
| 30MB 초과 | 413 `DOCUMENT_FILE_TOO_LARGE` |
| 같은 문서의 다른 버전이 대기·처리 중 | 409 `DOCUMENT_VERSION_IN_PROGRESS` |
| 현재 버전과 같은 파일 | 409 `DOCUMENT_FILE_UNCHANGED` |
| 재실행: 실패가 아닌 버전, 또는 현재 버전보다 오래된 버전 | 409 `DOCUMENT_VERSION_NOT_RETRYABLE` |
| 없는 문서 / 버전 | 404 `DOCUMENT_NOT_FOUND` / `DOCUMENT_VERSION_NOT_FOUND` |

### 조각 나누기 (챗봇과 합의)

- `gemini-embedding-001`, 768차원, `RETRIEVAL_DOCUMENT`, 문서명을 title 로, L2 정규화.
- 쪽마다 반복되는 머리말·꼬리말 줄과 쪽 번호 줄은 뺍니다. 책자처럼 홀수·짝수 쪽의 머리말이 다르면 따로 셉니다.
- 목표 600자 / 최대 1,000자 / 같은 절 안에서 약 100자 겹침. 절 제목이 바뀌면 새 조각, 조각 첫 줄은 절 제목(`상위 > 하위`), 제목 전 내용은 문서명.
- 제목은 짧고(40자 이하) 문장부호로 끝나지 않으며, 앞 내용이 끝난 뒤(쪽 첫 줄, 문장·목록 줄 뒤)에 오고 다음 줄이 본문인 줄입니다. `■`·`1-4` 가 붙은 줄은 짧기만 하면 제목입니다. `1. 여권 / 2. 사진` 처럼 같은 번호가 이어지면 목록입니다. 상위 제목(`1-4`)이 바뀌면 그 아래 제목(`■`)도 초기화됩니다.
- 표는 1차로 문단 기준입니다 (숫자 칸이 반복되는 줄은 한 조각에 묶음).
- 무료 등급 한도: 100개씩 묶어 호출하고 묶음 사이 2초, 429·503 이면 60초 기다렸다 2번까지 다시 보냅니다. 계속 실패하면 `FAILED [EMBEDDING]`.
