# 행사 API

요청·응답 필드는 Swagger(`7-2. 행사`, `행사 알림`)를 봅니다. 여기에는 API 마다 공통인 규칙을 적습니다.

## 사용자에게 보여 주는 지역

**사용자 조회는 시흥 행사만 보여 줍니다. 설정으로 바꿀 수 있습니다.** API 로 모을 수 없는 시흥 행사는 관리자가 직접 등록합니다.

| 설정 | 환경 변수 | 기본값 |
|---|---|---|
| `siot.event.public-regions` | `EVENT_PUBLIC_REGIONS` | `SIHEUNG` |

- 값은 `SIHEUNG`, `GYEONGGI` 이고 쉼표로 여러 개를 적습니다. `SIHEUNG,GYEONGGI` 면 시흥·경기도 행사를 모두 보여 줍니다.
- 모르는 값이나 빈 값이면 서버가 시작하지 않습니다. 바꾼 값은 서버를 다시 시작해야 적용됩니다.
- **수집과 번역은 설정과 상관없이 모든 지역**을 처리합니다. 설정을 바꾸면 이미 모은 행사가 바로 보입니다.
- **관리자 API(`/api/admin/events/**`)는 모든 지역**을 보여 줍니다. 지역이 잘못 분류된 행사는 `PATCH` 로 `region` 을 고칩니다. 고친 지역은 수집이 덮어쓰지 않습니다.
- 지역(`region_code`)이 없는 행사는 사용자에게 보이지 않습니다. 수집과 관리자 등록은 항상 지역을 넣습니다.

### 공개하지 않는 지역의 행사

| API | 동작 |
|---|---|
| `GET /api/events` (진행 예정·진행 중, `past=true`) | 결과에서 빠짐 (`totalElements` 에도 없음) |
| `GET /api/events?region=GYEONGGI` (공개하지 않는 지역) | **200 빈 결과**. `SEOUL` 처럼 모르는 값은 400 `INVALID_INPUT` |
| `GET /api/events/calendar`, `GET /api/events/recommendations` | 결과에서 빠짐 |
| `GET /api/events/{eventId}` | 404 `EVENT_NOT_FOUND` |
| `POST /api/events/{eventId}/subscriptions` | 404 `EVENT_NOT_FOUND` |
| `GET /api/events/{eventId}/subscriptions` | 200 `{ "subscribed": false, "leadDays": null }` |
| `GET /api/members/me/event-subscriptions` | 결과에서 빠짐. 신청 기록은 남아서 설정을 넓히면 다시 보임 |
| `DELETE /api/events/{eventId}/subscriptions` | 해제 가능 |
| 행사 콘텐츠 오류 신고 | 404 `EVENT_NOT_FOUND` |
| 챗봇 관련 페이지 ([api-chat.md](api-chat.md)) | 링크를 만들지 않음. 예전 대화에 저장된 링크는 남고, 열면 404 |

`GET /api/events/collection-status` 는 행사를 돌려주지 않으므로 달라지지 않습니다.

**알림(NTF-02)은 아직 이 설정을 보지 않습니다.** 공개하지 않는 지역의 행사를 이미 신청한 회원에게는 시작·신청 마감·일정 변경 알림이 갈 수 있고, 알림을 누르면 상세가 404 입니다.
