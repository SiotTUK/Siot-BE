# 인증·회원 API

공통 규칙(응답 형식, CSRF)은 [conventions.md](conventions.md)를 따릅니다.
**아이디 = 이메일**입니다 (11/13 결정). 가입하려면 먼저 이메일로 받은 **가입 인증 코드**를 확인해야 합니다.
인증된 이메일로만 비밀번호 재설정 메일과 이메일 알림(NTF-05)을 보냅니다.
로그인하지 않으면 아래 `/api/auth/**` 만 사용할 수 있습니다.
POST 요청은 모두 `X-XSRF-TOKEN` 헤더가 필요합니다. 앱 시작 시 `GET /api/auth/csrf` 를 한 번 호출합니다.

| 메서드 | 경로 | 로그인 | 요구사항 | 설명 |
|---|---|---|---|---|
| GET | `/api/auth/csrf` | - | - | CSRF 토큰 쿠키 발급 (204) |
| GET | `/api/auth/email/availability?email=` | - | M-02 | 이메일(아이디) 사용 가능 여부 |
| POST | `/api/auth/signup/email-verification` | - | M-01 | 가입 인증 코드 발송 (202) |
| POST | `/api/auth/signup/email-verification/confirm` | - | M-01 | 가입 인증 코드 확인 (204) |
| POST | `/api/auth/signup` | - | M-01~03 | 회원가입 (인증 코드 확인한 이메일만). 성공하면 바로 로그인 상태 (201) |
| POST | `/api/auth/login` | - | M-08 | 로그인 |
| POST | `/api/auth/logout` | - | M-09 | 로그아웃 (204) |
| GET | `/api/members/me` | 필요 | M-10 | 내 정보. 401이면 로그인 만료 → 로그인 화면으로 |
| PATCH | `/api/members/me` | 필요 | - | 내 정보 수정 (화면 언어) |
| PUT | `/api/members/me/onboarding` | 필요 | F02~F06 | 온보딩 답 저장 (처음 온보딩·내 정보 수정 공통) |
| POST | `/api/members/me/withdrawal` | 필요 | M-06, M-07 | 회원 탈퇴 (204) |
| POST | `/api/members/me/email/verification` | 필요 | NTF-09 | (기존 미인증 회원) 인증 코드 발송 (202) |
| POST | `/api/members/me/email/verification/confirm` | 필요 | NTF-09 | (기존 미인증 회원) 인증 코드 확인 → 인증 완료 |
| POST | `/api/auth/password-reset` | - | M-11 | 비밀번호 재설정 링크 요청 (202) |
| GET | `/api/auth/password-reset/validation?token=` | - | M-11 | 재설정 링크 유효성 확인 (204) |
| POST | `/api/auth/password-reset/confirm` | - | M-11 | 새 비밀번호 설정 (204) |

## 회원가입

1. 가입 인증 코드 요청

```http
POST /api/auth/signup/email-verification
{ "email": "user@example.com", "language": "en" }
```

**202** `{ "data": { "email": "user@example.com", "expiresInSeconds": 600, "resendAvailableInSeconds": 60 } }`

- `language` 는 메일 문구 언어(ko/en/zh/vi, 생략 시 ko)입니다. 가입 화면 언어를 보냅니다.
- 형식이 틀리면 400 `EMAIL_FORMAT`, 이미 가입한 이메일이면 409 `DUPLICATE_EMAIL` (메일 발송 안 함).
- 코드는 **10분** 유효, 다시 요청하면 이전 코드는 무효. 요청 제한(이메일 기준): **1분에 1회**, **하루 10회** (429 `EMAIL_VERIFICATION_TOO_FREQUENT` / `EMAIL_VERIFICATION_DAILY_LIMIT`).

2. 코드 확인

```http
POST /api/auth/signup/email-verification/confirm
{ "email": "user@example.com", "code": "123456" }
```

- 성공: 204. 이후 **30분 안에** 가입을 마쳐야 합니다.
- 틀림: 400 `EMAIL_VERIFICATION_CODE_INVALID`. **5회** 틀리거나 만료되면 400 `EMAIL_VERIFICATION_EXPIRED` → 코드를 다시 요청합니다.

3. 가입

```http
POST /api/auth/signup
Content-Type: application/json

{ "email": "user@example.com", "password": "abcd1234", "passwordConfirm": "abcd1234", "language": "en" }
```

**201**

```json
{ "data": { "id": 1, "email": "user@example.com", "role": "USER", "language": "en",
            "userType": null, "entryDate": null, "purpose": null, "residences": [], "stayExpiry": null,
            "emailVerified": true, "onboarded": false } }
```

응답에 `SIOT_SESSION` 쿠키(HttpOnly, SameSite=Lax, 운영은 Secure)가 설정됩니다.

- 코드를 확인하지 않은 이메일이면 400 `EMAIL_NOT_VERIFIED`. 확인 한 번으로 한 번만 가입할 수 있습니다.
- 이메일은 앞뒤 공백을 지우고 **소문자로 저장**합니다. 로그인·중복 확인도 대소문자를 구분하지 않습니다.
- `onboarded` 가 false(사용자 유형 미입력)이면 온보딩(사용자 유형) 화면으로 이동합니다.
- `language` 는 화면 언어(ko/en/zh/vi, 생략 시 ko)입니다. 가입 화면 언어를 보냅니다. 비밀번호 재설정·알림 메일도 이 언어로 갑니다. 지원하지 않는 값이면 400 `INVALID_INPUT`.

## 이메일 중복 확인

```http
GET /api/auth/email/availability?email=user@example.com
```

```json
{ "data": { "email": "user@example.com", "available": false } }
```

형식이 틀리면 400 `EMAIL_FORMAT`. 가입 요청 때 서버에서 다시 검사하므로(M-02) 동시 가입도 막힙니다.

## 로그인 / 로그아웃

```http
POST /api/auth/login
Content-Type: application/json

{ "email": "user@example.com", "password": "abcd1234" }
```

- 성공: 200, 회원가입과 같은 `data`
- 실패: 가입하지 않은 이메일이든 비밀번호가 틀리든 401 `LOGIN_FAILED` 로 같습니다. `details` 에 연속 실패 횟수가 있어 화면에 "비밀번호가 틀렸어요 (2/5)" 처럼 표시할 수 있습니다.

```json
{ "code": "LOGIN_FAILED", "message": "이메일 또는 비밀번호가 맞지 않습니다.",
  "details": { "failedAttempts": 2, "maxAttempts": 5, "remainingAttempts": 3 } }
```

  가입하지 않은 이메일도 같은 방식으로 세므로 횟수로 가입 여부가 드러나지 않습니다.
- 5회 연속 실패: 5번째 응답은 `LOGIN_FAILED` (`remainingAttempts: 0`) 이고, 이후 5분 동안 429 `LOGIN_LOCKED` (올바른 비밀번호도 거부). 로그인에 성공하면 실패 횟수가 초기화됩니다.
- 세션은 마지막 요청 후 14일 동안 유지됩니다 (M-10).

`POST /api/auth/logout` → 204, 서버 세션 삭제. 기기에 저장한 회원 정보 삭제(M-09 2번)는 프론트엔드에서 처리합니다.

## 내 정보 · 온보딩 답

`GET /api/members/me` 는 온보딩 답 5개를 모두 돌려줍니다 (내 정보 화면에서 보여 주고 고칠 때 사용).

```json
{ "data": { "id": 1, "email": "user@example.com", "role": "USER", "language": "en",
            "userType": "NEW", "entryDate": "2026-11-01", "purpose": "STUDY",
            "residences": ["KIDS", "KOREAN"], "stayExpiry": "2027-11-01",
            "emailVerified": true, "onboarded": true } }
```

### 온보딩 답 저장 (F02~F06)

```http
PUT /api/members/me/onboarding
{ "userType": "NEW", "entryDate": "2026-11-01", "purpose": "STUDY",
  "residences": ["KIDS", "KOREAN"], "stayExpiry": "2027-11-01" }
```

- **200**, 내 정보(`onboarded: true`)를 돌려줍니다.
- `userType`(NEW / LIVING)만 필수입니다. `purpose`: STUDY / WORK / FAMILY / OTHER (건너뛰면 null → 체크리스트는 OTHER로 계산). `residences`: KIDS / CAR / HOUSE / KOREAN / F4 (복수, 안 고르면 빈 배열 또는 생략). 날짜는 `yyyy-MM-dd`.
- **항상 전체를 덮어씁니다.** 내 정보 화면에서 한 항목만 고쳐도 5개를 모두 보내야 하며, 보내지 않은 선택 항목은 지워집니다.
- 답을 바꿔도 체크리스트 완료·해당 없음 기록은 지우지 않습니다.
- 필수 값이 없거나 목록에 없는 값이면 400 `INVALID_INPUT`.

### 화면 언어 수정

```http
PATCH /api/members/me
{ "language": "zh" }
```

- **200**, 내 정보를 돌려줍니다. 보내지 않은(null) 항목은 바꾸지 않습니다.
- 저장한 언어로 비밀번호 재설정·인증 코드·알림 메일이 갑니다. 사용자가 화면 언어를 바꾸면 이 API로 저장합니다.
- ko / en / zh / vi 외에는 400 `INVALID_INPUT`.

## 회원 탈퇴

```http
POST /api/members/me/withdrawal
Content-Type: application/json

{ "password": "abcd1234" }
```

- 본문(비밀번호)이 필요해서 DELETE 대신 POST를 사용합니다.
- 비밀번호가 틀리면 400 `PASSWORD_INCORRECT` (401이 아니므로 로그인 만료로 처리하지 않습니다).
- 성공하면 204. 즉시 물리 삭제되며 복구할 수 없습니다.
  - 삭제: 회원 정보(이메일·인증 여부), 온보딩 답·거주 정보, 체크리스트 완료·해당 없음 기록, 생활 변화, 알림·알림 설정·행사 알림 신청, 대화 기록
  - 유지: 오류 신고 (작성자 정보만 삭제)
  - 다른 기기를 포함한 **모든 세션**이 끊기고, 응답에서 세션 쿠키가 삭제됩니다.
- 삭제 안내 화면, 화면 안 추가 확인 단계, 기기 저장값 삭제(화면 언어는 유지), "탈퇴되었습니다" 표시(M-06, M-07)는 프론트엔드에서 처리합니다.

## 가입 후 이메일 인증 (NTF-09)

새 회원은 가입 때 인증을 마치므로(`emailVerified: true`) 사용하지 않습니다. 가입 인증 코드가 생기기 전에 가입했거나 이전 데이터에서 넘어온 **미인증 회원**(`emailVerified: false`)용입니다. 인증하지 않으면 비밀번호 재설정과 이메일 알림을 받지 못합니다.

1. 인증 코드 요청 (본문 없음)

```http
POST /api/members/me/email/verification
```

**202** `{ "data": { "email": "user@example.com", "expiresInSeconds": 600, "resendAvailableInSeconds": 60 } }`

- 가입 이메일로 6자리 코드가 사용자 언어(ko/en/zh/vi)로 발송됩니다.
- 코드는 **10분** 유효합니다. 다시 요청하면 이전 코드는 무효가 됩니다.
- 요청 제한: **1분에 1회**(`EMAIL_VERIFICATION_TOO_FREQUENT`), **하루 10회**(`EMAIL_VERIFICATION_DAILY_LIMIT`). Gmail 발송량 보호 목적입니다.
- 이미 인증했으면 409 `EMAIL_ALREADY_VERIFIED`.

2. 코드 확인

```http
POST /api/members/me/email/verification/confirm
{ "code": "123456" }
```

- 성공: 200, `data.emailVerified` 가 true 인 내 정보
- 틀림: 400 `EMAIL_VERIFICATION_CODE_INVALID`. **5회** 틀리거나 10분이 지나면 400 `EMAIL_VERIFICATION_EXPIRED` → 코드를 다시 요청합니다.

## 비밀번호 재설정 (M-11)

1. 로그인 화면의 "비밀번호 찾기"에서 이메일 입력

```http
POST /api/auth/password-reset
{ "email": "user@example.com" }
```

- 메일은 **회원이 저장한 언어**(가입 때 `language` 또는 `PATCH /api/members/me`)로 갑니다. 그래서 이 요청에는 언어 칸이 없습니다.
- **항상 202** 입니다. 가입하지 않았거나 인증하지 않은 이메일이어도 같은 응답이므로, 화면에는 "가입·인증된 이메일이면 메일을 보냈습니다"를 표시합니다.
- 요청 제한(이메일 기준): **1분에 1회**(`PASSWORD_RESET_TOO_FREQUENT`), **하루 5회**(`PASSWORD_RESET_DAILY_LIMIT`), 모두 429.
- 메일의 링크: `{FRONTEND_BASE_URL}/password-reset?token=...` → **프론트엔드는 이 경로에 재설정 화면을 만들어야 합니다.**

2. 재설정 화면 진입 시 링크 확인 (선택)

`GET /api/auth/password-reset/validation?token=...` → 유효 204 / 만료·사용됨 400 `PASSWORD_RESET_TOKEN_INVALID`

3. 새 비밀번호 설정

```http
POST /api/auth/password-reset/confirm
{ "token": "...", "password": "newpass5678", "passwordConfirm": "newpass5678" }
```

- 성공: 204. 링크는 **1회용, 30분 후 만료**. 새 링크를 요청하면 이전 링크는 무효가 됩니다.
- 비밀번호 규칙은 회원가입과 같습니다. 규칙 위반으로 실패하면 링크는 소모되지 않아 다시 입력할 수 있습니다.
- 성공하면 그 회원의 **모든 세션이 끊기고**, 로그인 잠금(5회 실패)도 풀립니다. 화면은 로그인 화면으로 이동합니다.

## 오류 코드

프론트엔드는 `code` 로 사용자 언어 문구를 표시합니다 (M-03 4번). `message` 는 한국어 기본 문구입니다.

| code | HTTP | 상황 |
|---|---|---|
| `EMAIL_FORMAT` | 400 | 이메일 형식이 아님 (최대 255자) |
| `DUPLICATE_EMAIL` | 409 | 이미 가입한 이메일 |
| `PASSWORD_LENGTH` | 400 | 8~64자가 아님 |
| `PASSWORD_WHITESPACE` | 400 | 공백 포함 |
| `PASSWORD_INVALID_CHARACTER` | 400 | 영문·숫자·특수문자(ASCII) 외 문자 포함 |
| `PASSWORD_LETTER_AND_DIGIT_REQUIRED` | 400 | 영문 또는 숫자가 없음 |
| `PASSWORD_SAME_AS_EMAIL` | 400 | 이메일과 같음 (대소문자 무시) |
| `PASSWORD_CONFIRM_MISMATCH` | 400 | 비밀번호 확인 불일치 |
| `LOGIN_FAILED` | 401 | 이메일 또는 비밀번호 불일치 |
| `LOGIN_LOCKED` | 429 | 5회 연속 실패로 5분 잠금 |
| `PASSWORD_INCORRECT` | 400 | 탈퇴 시 비밀번호 재확인 실패 |
| `EMAIL_NOT_VERIFIED` | 400 | 가입 인증 코드를 확인하지 않고 가입 요청 |
| `EMAIL_ALREADY_VERIFIED` | 409 | 이미 인증한 이메일 |
| `EMAIL_VERIFICATION_TOO_FREQUENT` | 429 | 인증 코드 1분 내 재요청 |
| `EMAIL_VERIFICATION_DAILY_LIMIT` | 429 | 인증 코드 하루 10회 초과 |
| `EMAIL_VERIFICATION_CODE_INVALID` | 400 | 인증 코드 불일치 |
| `EMAIL_VERIFICATION_EXPIRED` | 400 | 인증 코드 만료 / 5회 실패 / 요청 없음 |
| `PASSWORD_RESET_TOO_FREQUENT` | 429 | 재설정 1분 내 재요청 |
| `PASSWORD_RESET_DAILY_LIMIT` | 429 | 재설정 하루 5회 초과 |
| `PASSWORD_RESET_TOKEN_INVALID` | 400 | 재설정 링크 만료·사용됨·잘못됨 |
| `UNAUTHORIZED` | 401 | 로그인 필요 / 만료 |

비밀번호 규칙은 위 표 순서대로 검사해 **처음 어긴 규칙 하나**를 돌려줍니다. 입력 중 규칙별 체크 표시(M-03 3번)는 프론트엔드에서 같은 규칙으로 처리합니다.

## 백엔드에서 로그인 사용자 사용하기

```java
@GetMapping("/api/members/me/checklist-progress")
public ResponseEntity<...> myProgress(@AuthenticationPrincipal MemberPrincipal principal) {
    Long memberId = principal.getId();   // NFR-03: 본인 데이터는 이 ID로만 조회
    ...
}
```
