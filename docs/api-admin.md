# 관리자 API

`/api/admin/**` 는 관리자(`role = ADMIN`)만 사용할 수 있습니다. 로그인 안 함 401, 일반 회원 403 (ADM-01).

| 메서드 | 경로 | 요구사항 | 설명 |
|---|---|---|---|
| GET | `/api/admin/members?email=&role=&page=&size=` | ADM-01 | 회원 검색 (이메일 부분 일치, 권한, 가입 최신순) |
| PUT | `/api/admin/members/{memberId}/role` | ADM-01 | 권한 변경 `{ "role": "ADMIN" \| "USER" }` |
| GET | `/api/admin/chat/reviews?type=&reviewed=&page=&size=` | ADM-08 | 부정 평가 답변·근거 없음 질문 목록 |
| PATCH | `/api/admin/chat/reviews/{messageId}` | ADM-08 | 처리 여부·메모 `{ "reviewed": true, "adminMemo": "..." }` |

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
