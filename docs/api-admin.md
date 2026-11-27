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
| POST | `/api/admin/documents` | ADM-02 | 새 문서 등록 (multipart: `file`, `name`, `sourceUrl`, `publishedOn`, `excludedPages`) |
| PATCH | `/api/admin/documents/{documentId}` | ADM-03 | 문서 정보 수정 `{ "name", "sourceUrl", "publishedOn", "active" }` (보낸 값만) |
| POST | `/api/admin/documents/{documentId}/versions` | ADM-02 | 새 버전 업로드 (multipart: `file`, `changeSummary`, `excludedPages`) |
| GET | `/api/admin/documents/{documentId}/versions` | ADM-04, NTF-06 | 버전 목록과 반영 상태 |
| POST | `/api/admin/document-versions/{versionId}/retry` | ADM-04 | 실패한 버전 다시 반영 |
| POST | `/api/admin/documents/{documentId}/reprocess` | ADM-04 | 현재 버전 파일로 재처리 (바뀐 조각 규칙·뺄 쪽 적용) `{ "excludedPages" }` (선택) |

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
- **옛 버전 조각은 글만 남기고 임베딩은 비웁니다.** 새 버전이 현재 버전이 되는 같은 트랜잭션에서 이전 버전 조각의 `embedding` 을 NULL 로 만듭니다 (글·쪽·조각 번호는 그대로). 그래서 예전 버전을 DB 에서 직접 `is_current` 로 되돌리면 그 문서는 검색에서 사라집니다. 옛 내용으로 돌아가려면 그 파일을 새 버전으로 올립니다.
- 화면 안내 문구는 문서를 바꿔도 자동으로 바뀌지 않습니다. 관리자가 검토 후 [항목 문구 저장](#항목-문구-저장-chk-06-ntf-07)으로 고칩니다 (ADM-02, NTF-07).
- 반영 결과(NTF-06)는 버전 목록에서 확인합니다. `uploadedAt`(파일 업로드 완료)과 `processedAt`(반영이 끝난 시각, 성공·실패 모두)을 구분합니다.

### 새 문서 등록 `POST /api/admin/documents` (multipart/form-data) → 201

| 필드 | 필수 | 설명 |
|---|---|---|
| `file` | O | PDF (30MB 이하) |
| `name` | O | 문서명 (255자 이하). 챗봇 출처에 그대로 보입니다 |
| `sourceUrl` | O | 공개 원문 링크 (`http(s)://`) |
| `publishedOn` | | 발행일 `2026-03-01` |
| `excludedPages` | | [적재에서 뺄 쪽](#적재에서-뺄-쪽-excludedpages) `51-52, 100` |

```json
{ "data": { "documentId": 3, "name": "시흥시 외국인주민 생활안내", "sourceUrl": "https://...", "publishedOn": "2026-03-01",
  "active": true, "currentVersion": null,
  "latestVersion": { "versionId": 12, "versionNo": 1, "status": "PENDING", "processedAt": null, "excludedPages": null },
  "createdAt": "2026-11-20T10:00:00+09:00", "updatedAt": "2026-11-20T10:00:00+09:00" } }
```

### 버전 목록 `GET /api/admin/documents/{documentId}/versions` (최신 먼저)

```json
{ "data": [ {
  "versionId": 13, "documentId": 3, "versionNo": 2, "originalFilename": "guide-2026.pdf",
  "changeType": "REPLACE", "changeSummary": "2026년 기준 반영", "excludedPages": "51-52,100",
  "status": "FAILED", "current": false,
  "failedStage": "EMBEDDING", "failureReason": "Gemini 임베딩 실패: ...",
  "chunkCount": 0, "uploadedBy": 1,
  "uploadedAt": "2026-11-20T10:05:00+09:00", "processedAt": "2026-11-20T10:07:12+09:00" } ] }
```

- `changeType`: `NEW`(새 문서 등록) / `REPLACE`(기존 문서 교체) / `REPROCESS`(같은 파일 재처리).
- 텍스트가 없는 PDF(스캔 이미지)는 `EXTRACT` 단계에서 실패합니다. 글자를 선택할 수 있는 PDF 로 다시 올립니다.

### 적재에서 뺄 쪽 `excludedPages`

빈칸 서식·신청서처럼 답변 근거로 쓸 수 없는 쪽(옛 기준 문구가 남은 서식 포함)을 관리자가 지정합니다. **자동 감지는 하지 않습니다** (본문 쪽을 잘못 뺄 수 있어서).

- 형식: `51-52, 100` — **PDF 뷰어 기준 1부터**(책에 인쇄된 쪽 번호가 아닙니다). 쉼표로 나열하고 범위는 `-`. 1,000자 이하. 저장·응답은 `51-52,100` 처럼 정리한 값입니다.
- 그 쪽은 텍스트 추출에서 건너뜁니다. 남은 조각의 `page_start`/`page_end` 는 원본 PDF 번호 그대로입니다. 한 조각이 뺀 쪽의 앞뒤를 함께 담지 않습니다 (출처 쪽 범위에 뺀 쪽이 들지 않게). 원본 파일은 그대로 보관합니다.
- 머리말·꼬리말 판단과 태그 PDF 판단은 전체 쪽으로 합니다. **장 제목이 있는 쪽을 빼면** 뒤 쪽 조각 제목에서 그 장 이름이 빠지고, 제목 단계가 줄어 짧은 절이 덜 묶일 수 있습니다.
- 값은 **버전마다** 저장합니다 (`document_version.excluded_pages`). 파일이 바뀌면 쪽 번호가 달라지기 때문입니다.

| 상황 | `excludedPages` |
|---|---|
| 새 문서 등록 | 선택. 생략·빈 값 = 빼지 않음 |
| 새 버전 업로드 | 현재 버전에 뺀 쪽이 있으면 **필수** (400 `DOCUMENT_EXCLUDED_PAGES_REQUIRED`). **새 파일에서 번호를 다시 확인해** 보냅니다. 빼지 않으려면 빈 값. 현재 버전에 뺀 쪽이 없으면 선택 |
| 재처리 | JSON 본문 선택. 본문이나 값을 생략하면 현재 버전 값 유지, `""` 면 빼지 않음. 값이 바뀌면 `changeSummary` 가 `적재에서 뺄 쪽 변경: 51-52` (해제는 `…: 없음`) |
| 실패한 버전 다시 반영 | 그 버전에 저장한 값 그대로 |

- 뺄 쪽만 바꾸려면 재처리를 씁니다. 같은 파일을 새 버전으로 올리면 `DOCUMENT_FILE_UNCHANGED` 입니다.
- `PATCH`(문서 정보 수정)로는 바꾸지 않습니다. 다시 적재해야 검색에 반영되기 때문입니다.

### 재처리 `POST /api/admin/documents/{documentId}/reprocess` → 202

조각 나누기 규칙이나 [뺄 쪽](#적재에서-뺄-쪽-excludedpages)이 바뀌었을 때 기존 문서를 다시 적재합니다. 같은 파일을 새 버전으로 올리면 `DOCUMENT_FILE_UNCHANGED` 로 막히므로 이 API 를 씁니다.

- 현재 버전(`current: true`)의 저장된 파일로 새 버전을 만듭니다. 파일은 다시 저장하지 않고 같은 저장 키·checksum 을 씁니다.
- `versionNo` 는 +1, `changeType: "REPROCESS"`, `changeSummary: "조각 규칙 변경으로 재처리"`(뺄 쪽을 바꾸면 위 표의 값), `uploadedBy` 는 요청한 관리자입니다.
- 처리 흐름은 새 버전 업로드와 같습니다. 성공하면 새 버전이 현재 버전이 되고, 실패하면 이전 버전을 계속 씁니다. 실패한 재처리 버전은 `retry` 로 다시 반영할 수 있습니다.
- 문서 내용이 바뀐 것이 아니므로 **사용자 알림 대상이 아닙니다.** 사용자에게 문서 변경 알림을 보내게 되면 `REPROCESS` 는 빼야 합니다.
- 사용 중지(`active: false`) 문서도 재처리할 수 있으며, 검색에서는 계속 빠집니다.
- 실패한 새 버전(REPLACE)이 남아 있는 상태에서 재처리가 성공하면, 그 실패 버전은 현재 버전보다 오래된 버전이 되어 `retry` 할 수 없습니다.
- 응답은 새 버전 (`status: "PENDING"`, `current: false`). 진행 상태는 버전 목록에서 확인합니다.

### 오류

| 상황 | 응답 |
|---|---|
| PDF 가 아님 | 400 `DOCUMENT_FILE_INVALID` |
| 30MB 초과 | 413 `DOCUMENT_FILE_TOO_LARGE` |
| 같은 문서의 다른 버전이 대기·처리 중 (업로드·재처리) | 409 `DOCUMENT_VERSION_IN_PROGRESS` |
| 현재 버전과 같은 파일 | 409 `DOCUMENT_FILE_UNCHANGED` |
| `excludedPages` 형식 오류, PDF 쪽 수 초과, 모든 쪽을 뺌, 1,000자 초과 (등록·업로드·재처리) | 400 `DOCUMENT_EXCLUDED_PAGES_INVALID` |
| 새 버전: 현재 버전에 뺀 쪽이 있는데 `excludedPages` 를 안 보냄 | 400 `DOCUMENT_EXCLUDED_PAGES_REQUIRED` |
| 재처리: 현재 버전이 없음 (첫 버전이 아직 성공하지 못함) | 409 `DOCUMENT_CURRENT_VERSION_MISSING` |
| 재실행: 실패가 아닌 버전, 또는 현재 버전보다 오래된 버전 | 409 `DOCUMENT_VERSION_NOT_RETRYABLE` |
| 없는 문서 / 버전 | 404 `DOCUMENT_NOT_FOUND` / `DOCUMENT_VERSION_NOT_FOUND` |

### 조각 나누기 (챗봇과 합의)

- `gemini-embedding-001`, 768차원, `RETRIEVAL_DOCUMENT`, 문서명을 title 로, L2 정규화.
- **태그 PDF**(InDesign 등에서 접근성 태그를 넣은 PDF)는 구조 트리 순서로 읽습니다. 머리말·꼬리말·쪽 번호(Artifact)는 자동으로 빠지고, 표는 행마다 머리 행 이름을 붙여 `구분: 방문 납부 / 납부 방법: … / 비고: …` 한 줄로 만들어 표 하나를 한 조각으로 저장합니다 (길면 행 경계에서 나눔, 합쳐진 칸은 아래 행에도 채움). 첫 행에 전화·주소·URL 이 있으면 머리 행 없이 `값 / 값`, 첫 행 칸이 설명(· 로 시작, 쉼표, 30자 초과)이면 첫 열을 항목 이름으로 `위치: 값 / 값` 형태로 씁니다. 구조 트리 글자가 화면 글자의 80% 미만이면 아래 방식으로 읽습니다.
- **태그 없는 PDF**는 화면 위치 순서로 읽고, 쪽마다 반복되는 머리말·꼬리말 줄과 쪽 번호 줄은 뺍니다. 책자처럼 홀수·짝수 쪽의 머리말이 다르면 따로 셉니다.
- 목표 600자 / 최대 1,000자 / 같은 절 안에서 약 100자 겹침. 절 제목이 바뀌면 새 조각, 조각 첫 줄은 절 제목(최대 세 단계·80자, `1-4 지방세 납부 > ■ 재산세`. 05 묶음 문서 안은 아래처럼 네 단계·100자), 제목 전 내용은 문서명.
- 제목은 짧고(40자 이하) 문장부호로 끝나지 않으며, 앞 내용이 끝난 뒤(쪽 첫 줄, 문장·목록 줄 뒤)에 오고 다음 줄이 본문인 줄입니다. `■`·`1-4` 가 붙은 줄은 짧기만 하면 제목입니다. `1. 여권 / 2. 사진` 처럼 같은 번호가 이어지면 목록입니다. 상위 제목(`1-4`)이 바뀌면 그 아래 제목(`■`)도 초기화됩니다.
- 제목 단계는 번호 모양과 글자 크기·굵기로 가릅니다 (태그 PDF 도 글자 크기를 씀). `3.1.3.` 같은 점 번호는 숫자 개수로, `PART 1.`·`제2장`, 번호 없는 체류자격 제목(`영 주(F-5)`)은 늘 새 장입니다. 표지 제목(앞 3쪽 안의 가장 큰 첫 제목, 또는 절이 20개 이상인 문서에서 내용 있는 절의 절반 넘게 경로 맨 앞에 붙는 번호 없는 제목)은 제목 경로에 넣지 않습니다. `[별표 2] <개정 …>` 은 개정 표기를 떼고 아래 제목 줄과 이어 `[별표 2] 지역보험료 산정 기준(제6조제1항 관련)` 으로 씁니다.
- 태그 없는 PDF 에서 앞 줄보다 1.1배 이상 큰 `Ⅲ`·`5.` 번호 줄은 앞 내용이 문장으로 끝나지 않았어도 제목입니다 (`□ 쿼터 : 250명` 다음의 `Ⅲ 지자체별 요건 및 특례 - 특정활동`).
- **05 체류 매뉴얼 구조에 맞춘 규칙** (개정판을 올리면 아래 두 가지가 그대로 잡히는지 확인):
  - **묶음 문서**: 마지막 체류자격 장 뒤, 쪽 맨 위의 큰 글자(본문 1.5배 이상)이고 `매뉴얼`·`지침`·`알림`·`시범사업` 으로 끝나는 줄은 표제입니다 (2026-11-16 판: 526·590·659·673·713·775쪽 6개). 다음 표제 전까지 경로 맨 위에 남습니다.
    - 제목은 `표제 > 절 > 지역 > 마지막 제목`(위 세 단계 + 마지막, 100자)입니다. 넘으면 아래 단계부터 빼고, 뺀 제목은 본문 첫 줄에 남깁니다.
    - 표제 안에서는 본문과 글자 크기가 같아도 로마 숫자는 절, `󰊱` 상자 번호는 제목, `붙임 3`·`별첨 2`·`참고 1` 은 표제 바로 아래 제목입니다. 표제 바로 아래 번호 없는 줄(부제)은 본문입니다.
    - 문서·절·지역이 다른 조각은 짧아도 합치지 않습니다.
  - **시간제취업 칸**: 유학(D-2) 표의 두 줄 칸 이름 `시간제취업` / `활동 허가` 를 `유 학(D-2) > 시간제취업 활동 허가` 제목으로 세우고, 다음 칸 이름에서 끝냅니다.
- 강조(큰 글자) 기준: 문서 본문보다 1.15배 이상이고 그 쪽 본문보다도 1.05배 이상 커야 합니다. 별표 쪽처럼 쪽 전체 글자가 큰 쪽의 표 칸은 제목이 되지 않습니다. 쪽 끝까지 찬 줄이라도 쪽 본문보다 1.25배 이상 크면 상자 제목입니다.
- 바로 위 부모 제목이 같은 이웃 형제 절은 합쳐서 600자(한쪽 본문이 200자 미만이면 1,000자)까지 한 조각으로 묶습니다. 첫 줄은 부모 경로, 하위 제목은 본문 안에 줄로 남깁니다. 앞 조각에서 이어지는 꼬리를 묶을 때는 `① 정부간 협약 감면 (이어서)` 로 표시합니다. 부모가 최상위 제목(장)뿐이면 묶지 않고, 묶은 조각은 2쪽을 넘지 않습니다. 표 조각은 묶지 않습니다.
- 조각이 `※ 외국인근로자에게 적용되는` 처럼 다음 줄로 이어지는 짧은 머리 문장으로 끝나면 그 문장은 다음 조각으로 넘깁니다. 태그 PDF 에서 한 화면 줄이 구조 요소 여러 개로 나뉘어 있으면 한 줄로 잇습니다.
- 태그 없는 PDF 의 표는 문단 기준입니다 (숫자 칸이 반복되는 줄은 한 조각에 묶음). 예외로 05 체류 매뉴얼의 체류관리 표(칸 이름 7개: 체류자격외 활동 ~ 외국인등록)는 행이 짧고 촘촘하면(칸 이름 사이 3줄 이하) `재입국허가: …` 행으로 바꾸고 자격 제목 바로 아래 `체류관리` 절에 둡니다.
- 무료 등급 한도: 100개씩 묶어 호출하고 묶음 사이 2초, 429·503 이면 60초 기다렸다 2번까지 다시 보냅니다. 계속 실패하면 `FAILED [EMBEDDING]`.

## 달력 공통 일정·세금 일정 (CAL-01~03, NTF-01)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/schedules?year=&kind=` | 그해 시작하는 공통 일정 (꺼 둔 것 포함, 시작일 순). `autoGenerated=true` 는 자동 생성된 일정 |
| POST | `/api/admin/schedules` | 공통 일정 등록 (201). 연납 신청 기간·기한 연장처럼 정기분 밖의 일정 |
| PUT | `/api/admin/schedules/{scheduleId}` | 공통 일정 수정 (모든 값을 덮어씀) |
| POST | `/api/admin/schedules/taxes/generate?year=` | 그해 정기분 세금 일정 중 없는 회차를 만든다 |

```json
{ "kind": "TAX_CAR", "guideCode": "tax-car", "startsOn": "2027-06-16", "endsOn": "2027-06-30",
  "targetDescription": null, "sourceUrl": "https://www.wetax.go.kr", "active": true }
```

- `kind` 는 영문 대문자·숫자·밑줄. `TAX` 가 들어가면 사용자 세금 상세(`/api/schedules/taxes`)와 세금 알림(D-7·D-1) 대상입니다.
- `guideCode` 를 주면 제목·상세 문구·문의처를 그 항목(`tax-car` 등)에서 가져옵니다. 없는 항목은 404 `GUIDE_NOT_FOUND`, `endsOn` 이 `startsOn` 보다 빠르면 400 `INVALID_INPUT`.
- 지우는 API 는 없습니다. `active: false` 로 끕니다 (알림이 일정을 가리키고 있고, 지우면 자동 생성이 다시 만들기 때문).

### 정기분 세금 일정 자동 생성

시흥시 지방세 정기분 납부 기간은 매년 같아서 서버가 만들어 둡니다 (생활수칙 `tax-car`·`tax-property`·`tax-resident` 와 같은 출처).

| kind | 회차 | 기간 |
|---|---|---|
| `TAX_CAR` | 자동차세 1기분 / 2기분 | 6월·12월 16일 ~ 말일 |
| `TAX_PROPERTY` | 재산세 | 7월·9월 16일 ~ 말일 |
| `TAX_RESIDENT` | 주민세 개인분 | 8월 16일 ~ 말일 |

- **마감일**: 말일이 토요일·일요일·공휴일·대체공휴일·노동절이면 그다음 날 (지방세기본법 제24조 기한의 특례). 다음 날도 쉬는 날이면 계속 미룹니다. 예: 2027-07-31(토) → 08-02(월), 2028-09-30(토) → 추석 연휴·대체공휴일 → 10-06(금).
- **공휴일 자료**: 한국천문연구원 특일 정보(공공데이터포털)에서 받습니다. 환경변수 `HOLIDAY_API_KEY` (비우면 `EVENT_API_KEY`) 로 **특일 정보 활용신청이 된 키**가 필요합니다. 키가 없거나 그해 자료가 아직 없으면 주말·양력 고정 공휴일만 반영하고, 생성 결과 `officialHolidays: false` 와 서버 경고 로그로 알립니다. 이때는 설날·추석·대체·임시공휴일에 걸린 마감일을 확인해 고쳐 주세요.
- **언제 만드나**: 서버 시작 때와 매일 04:30 (`TAX_SCHEDULE_CRON`). 올해는 항상, 내년은 12월부터. 그 전에 필요하면 `POST /api/admin/schedules/taxes/generate?year=` 로 만듭니다.
- **이미 있는 회차는 건드리지 않습니다.** 그달에 같은 세금 일정이 있으면(꺼 둔 것, 관리자가 고친 것, `guideCode` 로 연결된 다른 kind 포함) 만들지도 고치지도 않습니다. 임시공휴일 지정이나 기한 연장이 발표되면 `PUT` 으로 마감일을 고칩니다.
