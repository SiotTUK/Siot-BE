# 백엔드 개발 규칙

팀원이 같은 방식으로 API·마이그레이션을 만들기 위한 규칙입니다. 바꿀 필요가 있으면 PR로 이 문서와 코드를 함께 수정합니다.

## 1. 패키지 구조

```
com.siot.backend.{기능}
├── controller   @RestController, 요청/응답 DTO 변환만
├── service      트랜잭션·비즈니스 로직
├── repository   Spring Data JPA
├── domain       Entity, enum
└── dto          요청/응답 record
```

- 다른 기능의 Entity를 직접 참조하지 않고 ID(Long)로 참조합니다. (예: `Notification.eventId`)
- 공통 코드는 `global` 패키지에 둡니다.

## 2. API 경로와 접근 권한

`global/config/SecurityConfig` 에 정의되어 있습니다. **목록에 없는 경로는 모두 로그인이 필요합니다.**

| 경로 | 허용 메서드 | 접근 | 용도 |
|---|---|---|---|
| `/api/auth/**` | 전체 | 누구나 | 가입 인증 코드, 가입, 이메일 중복 확인, 로그인·로그아웃, 비밀번호 재설정, CSRF 토큰 |
| `/api/members/me/**` | 전체 | 로그인 | 내 정보·온보딩 답, 이메일 인증, 체크리스트 진행, 생활 변화, 알림 설정 등 **본인 데이터** |
| `/api/admin/**` | 전체 | ADMIN | 관리 기능 |
| 그 외 `/api/**` | 전체 | 로그인 | 홈·체크리스트·생활 달력·행사·챗봇·신고·번역 등 (**비회원 이용 불가**, 11/13 결정) |
| `/actuator/health`, `/actuator/info` | GET | 누구나 | 헬스 체크 |
| 그 외 `/actuator/**` | GET | ADMIN | 운영 지표 |

- **본인 데이터(NFR-03)** 는 `/api/members/me/**` 아래에 두고, 서비스에서 로그인 사용자 ID로만 조회합니다. 요청 파라미터로 받은 userId를 믿지 않습니다.
- 경로는 소문자 복수형 명사와 kebab-case를 사용합니다. (예: `/api/events/{eventId}/subscriptions`)

## 3. CSRF

세션 쿠키 방식이라 CSRF 보호가 켜져 있습니다. **GET 이외의 요청(POST/PUT/PATCH/DELETE)은 토큰이 없으면 403 `CSRF_TOKEN_INVALID` 입니다.**

1. `GET /api/auth/csrf` → 응답에 `XSRF-TOKEN` 쿠키가 설정됩니다. (204)
2. 이후 요청에 쿠키를 그대로 보내고, 같은 값을 `X-XSRF-TOKEN` 헤더에 넣습니다.

**프론트엔드 (axios)**: `withCredentials: true` 이면 axios가 `XSRF-TOKEN` 쿠키를 읽어 헤더를 자동으로 붙입니다. 앱 시작 시 `/api/auth/csrf` 를 한 번 호출합니다.

**토큰 교체**: 가입·로그인·로그아웃 응답에서 `XSRF-TOKEN` 쿠키가 새 값으로 바뀝니다 (로그인 전 토큰을 로그인 후에 쓰지 못하게). 헤더 값은 요청할 때마다 쿠키에서 다시 읽어야 합니다. 앱 시작 때 읽은 값을 변수에 저장해 두고 계속 쓰면 로그인 뒤 요청이 `CSRF_TOKEN_INVALID`(403)가 됩니다. axios 기본 동작은 매번 쿠키를 읽으므로 그대로 쓰면 됩니다.

**curl**

```bash
curl -c jar.txt -b jar.txt http://localhost:8080/api/auth/csrf
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' jar.txt)
curl -c jar.txt -b jar.txt -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" \
     -d '{"email":"tester01@example.com","password":"..."}' http://localhost:8080/api/auth/login
```

**Postman**: Collection의 Pre-request Script에 다음을 넣습니다. (쿠키는 Postman이 자동 관리)

```javascript
const token = pm.cookies.get('XSRF-TOKEN');
if (token) pm.request.headers.upsert({ key: 'X-XSRF-TOKEN', value: token });
```

토큰이 없으면 먼저 `GET {{baseUrl}}/api/auth/csrf` 를 한 번 호출합니다.

## 4. 응답 형식

**성공**: `ApiResponse` 로 감쌉니다.

```json
{ "data": { "eventId": 1, "title": "..." } }
```

**목록(페이지)**: `ApiResponse.of(PageResponse.from(page, EventResponse::from))`

```json
{ "data": { "content": [ ... ], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3, "hasNext": true } }
```

**본문 없는 성공**(삭제 등): `204 No Content`

**실패**: `ErrorResponse`

```json
{ "code": "NOT_FOUND", "message": "요청한 리소스를 찾을 수 없습니다." }
```

- 비즈니스 오류는 `throw new BusinessException(ErrorCode.XXX)` 로 던지면 `GlobalExceptionHandler` 가 위 형식으로 응답합니다.
- 화면에 추가 정보가 필요한 오류만 `details` 객체를 넣습니다 (`new BusinessException(code, Map.of(...))`). 없으면 응답에서 빠집니다. 예: `LOGIN_FAILED` 의 실패 횟수.
- 새 오류가 필요하면 `ErrorCode` 에 추가합니다. `code` 값은 프론트엔드가 분기에 사용하므로 한 번 정하면 바꾸지 않습니다.

| 공통 code | HTTP |
|---|---|
| `INVALID_INPUT` | 400 |
| `UNAUTHORIZED` | 401 |
| `FORBIDDEN` | 403 |
| `CSRF_TOKEN_INVALID` | 403 |
| `NOT_FOUND` | 404 |
| `METHOD_NOT_ALLOWED` | 405 |
| `INTERNAL_ERROR` | 500 |

## 5. DB 마이그레이션 (Flyway)

- 스키마는 **Flyway 파일로만** 변경합니다. JPA는 `ddl-auto: validate` 라 Entity와 다르면 기동에 실패합니다.
- **파일 이름**: `V{yyyy}_{MM}_{dd}_{HHmm}__{snake_case_설명}.sql` (작성 시각, 한국 시간)
  - 예: `V2026_11_12_1430__add_event_category.sql`
  - 세 명이 동시에 만들어도 번호가 겹치지 않습니다. `MigrationNamingTest` 가 형식을 검사합니다.
  - `V1`~`V4` 는 초기 골격 파일입니다.
- **이미 main에 병합된 파일은 절대 수정하지 않습니다.** 고칠 내용은 새 파일로 추가합니다. (수정하면 checksum 불일치로 기동 실패)
- PR을 병합하기 직전, 내 파일의 시각이 main에 있는 최신 파일보다 이르면 **파일 이름을 현재 시각으로 바꿉니다.** 운영 DB에는 순서대로 적용되어야 하기 때문입니다.
- 로컬(`local` 프로필)은 `out-of-order: true` 라서 브랜치를 오가며 순서가 뒤바뀌어도 기동됩니다.
- 로컬 DB가 꼬였으면 `docker compose down -v` 후 다시 띄웁니다. (로컬 데이터 전부 삭제)

## 6. 테스트

- `./gradlew test` 는 Testcontainers로 PostgreSQL(pgvector)과 Redis를 띄우므로 Docker가 실행 중이어야 합니다.
- 통합 테스트는 `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` 를 사용합니다.
- PR은 CI(`Backend CI`)가 통과해야 병합합니다.

## 7. 메일 발송

- 메일은 `global/mail` 의 `EmailSender` 로 보냅니다. (운영: Gmail SMTP, 로컬: Mailpit)
- `send()` 는 **비동기**이며 실패해도 예외를 던지지 않고 로그만 남깁니다. 발송 결과를 저장해야 하면(예: 알림 이메일 NTF-05의 `notification.email_status`) 호출하는 쪽에서 별도로 처리합니다.
- 메일은 **인증된 가입 이메일**(`app_user.email_verified_at` 있음)에만 보냅니다. 예외: 인증 코드 메일.
- 메일 문구는 `resources/messages/mail*.properties` 에 4개 언어(ko 기본, en, zh, vi)로 추가하고 `MailTemplates` 에 메서드를 만듭니다. MessageFormat 문법이라 문구에 작은따옴표(`'`)를 쓰지 않습니다.
- Gmail 무료 계정은 하루 약 500통 제한이 있으므로, 사용자가 반복 요청할 수 있는 메일은 `RequestRateLimiter` 로 횟수를 제한합니다.
- 수신자 이메일 주소는 개인정보이므로 로그에 남기지 않습니다.
