# 인증·회원 API

공통 규칙(응답 형식, CSRF)은 [conventions.md](conventions.md)를 따릅니다.
POST 요청은 모두 `X-XSRF-TOKEN` 헤더가 필요합니다. 앱 시작 시 `GET /api/auth/csrf` 를 한 번 호출합니다.

| 메서드 | 경로 | 로그인 | 요구사항 | 설명 |
|---|---|---|---|---|
| GET | `/api/auth/csrf` | - | - | CSRF 토큰 쿠키 발급 (204) |
| GET | `/api/auth/login-id/availability?loginId=` | - | M-02 | ID 사용 가능 여부 |
| POST | `/api/auth/signup` | - | M-01~03 | 회원가입. 성공하면 바로 로그인 상태 (201) |
| POST | `/api/auth/login` | - | M-08 | 로그인 |
| POST | `/api/auth/logout` | - | M-09 | 로그아웃 (204) |
| GET | `/api/members/me` | 필요 | M-10 | 내 정보. 401이면 로그인 만료 → 로그인 화면으로 |
| POST | `/api/members/me/withdrawal` | 필요 | M-06, M-07 | 회원 탈퇴 (204) |
| POST | `/api/members/me/email/verification` | 필요 | NTF-09 | 알림 이메일 인증 코드 발송 (202) |
| POST | `/api/members/me/email/verification/confirm` | 필요 | NTF-09 | 인증 코드 확인 → 이메일 등록 |
| DELETE | `/api/members/me/email` | 필요 | NTF-09 | 알림 이메일 등록 해제 |
| POST | `/api/auth/password-reset` | - | M-11 | 비밀번호 재설정 링크 요청 (202) |
| GET | `/api/auth/password-reset/validation?token=` | - | M-11 | 재설정 링크 유효성 확인 (204) |
| POST | `/api/auth/password-reset/confirm` | - | M-11 | 새 비밀번호 설정 (204) |

## 회원가입

```http
POST /api/auth/signup
Content-Type: application/json

{ "loginId": "user01", "password": "abcd1234", "passwordConfirm": "abcd1234" }
```

**201**

```json
{ "data": { "id": 1, "loginId": "user01", "role": "USER", "language": "ko",
            "userType": null, "emailRegistered": false, "emailVerified": false } }
```

응답에 `SIOT_SESSION` 쿠키(HttpOnly, SameSite=Lax, 운영은 Secure)가 설정됩니다.

## ID 중복 확인

```http
GET /api/auth/login-id/availability?loginId=user01
```

```json
{ "data": { "loginId": "user01", "available": false } }
```

형식이 틀리면 400 `LOGIN_ID_FORMAT`. 가입 요청 때 서버에서 다시 검사하므로(M-02) 동시 가입도 막힙니다.

## 로그인 / 로그아웃

```http
POST /api/auth/login
Content-Type: application/json

{ "loginId": "user01", "password": "abcd1234" }
```

- 성공: 200, 회원가입과 같은 `data`
- 실패: ID가 없든 비밀번호가 틀리든 401 `LOGIN_FAILED` 로 같습니다.
- 5회 연속 실패: 이후 5분 동안 429 `LOGIN_LOCKED` (올바른 비밀번호도 거부). 로그인에 성공하면 실패 횟수가 초기화됩니다.
- 세션은 마지막 요청 후 14일 동안 유지됩니다 (M-10).

`POST /api/auth/logout` → 204, 서버 세션 삭제. 기기에 저장한 회원 정보 삭제(M-09 2번)는 프론트엔드에서 처리합니다.

## 회원 탈퇴

```http
POST /api/members/me/withdrawal
Content-Type: application/json

{ "password": "abcd1234" }
```

- 본문(비밀번호)이 필요해서 DELETE 대신 POST를 사용합니다.
- 비밀번호가 틀리면 400 `PASSWORD_INCORRECT` (401이 아니므로 로그인 만료로 처리하지 않습니다).
- 성공하면 204. 즉시 물리 삭제되며 복구할 수 없습니다.
  - 삭제: 회원 정보, 생활 정보, 알림 이메일, 체크리스트 완료 기록, 알림·알림 설정·행사 알림 신청, 대화 기록
  - 유지: 오류 신고 (작성자 정보만 삭제)
  - 다른 기기를 포함한 **모든 세션**이 끊기고, 응답에서 세션 쿠키가 삭제됩니다.
- 삭제 안내 화면, 화면 안 추가 확인 단계, 기기 저장값 삭제(화면 언어는 유지), "탈퇴되었습니다" 표시(M-06, M-07)는 프론트엔드에서 처리합니다.

## 알림 이메일 등록 (NTF-09)

선택 등록이며, 인증을 마친 이메일만 알림 메일과 비밀번호 재설정에 사용합니다.

1. 인증 코드 요청

```http
POST /api/members/me/email/verification
{ "email": "user@example.com" }
```

**202** `{ "data": { "email": "user@example.com", "expiresInSeconds": 600, "resendAvailableInSeconds": 60 } }`

- 입력한 주소로 6자리 코드가 사용자 언어(ko/en/zh/vi)로 발송됩니다. 이메일은 소문자로 저장합니다.
- 코드는 **10분** 유효합니다. 다시 요청하면 이전 코드는 무효가 됩니다.
- 요청 제한: **1분에 1회**(`EMAIL_VERIFICATION_TOO_FREQUENT`), **하루 10회**(`EMAIL_VERIFICATION_DAILY_LIMIT`). Gmail 발송량 보호 목적입니다.

2. 코드 확인

```http
POST /api/members/me/email/verification/confirm
{ "code": "123456" }
```

- 성공: 200, `data.emailRegistered`·`data.emailVerified` 가 true 인 내 정보
- 틀림: 400 `EMAIL_VERIFICATION_CODE_INVALID`. **5회** 틀리거나 10분이 지나면 400 `EMAIL_VERIFICATION_EXPIRED` → 코드를 다시 요청합니다.

3. 등록 해제: `DELETE /api/members/me/email` → 200, 내 정보

## 비밀번호 재설정 (M-11)

1. 로그인 화면의 "비밀번호 찾기"에서 ID 입력

```http
POST /api/auth/password-reset
{ "loginId": "user01" }
```

- **항상 202** 입니다. 계정이 없거나 인증된 이메일이 없어도 같은 응답이므로, 화면에는 "등록된 이메일이 있으면 메일을 보냈습니다"를 표시합니다.
- 요청 제한(ID 기준): **1분에 1회**(`PASSWORD_RESET_TOO_FREQUENT`), **하루 5회**(`PASSWORD_RESET_DAILY_LIMIT`), 모두 429.
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
| `LOGIN_ID_FORMAT` | 400 | ID가 영문 소문자·숫자 4~20자가 아님 |
| `DUPLICATE_LOGIN_ID` | 409 | 이미 사용 중인 ID |
| `PASSWORD_LENGTH` | 400 | 8~64자가 아님 |
| `PASSWORD_WHITESPACE` | 400 | 공백 포함 |
| `PASSWORD_INVALID_CHARACTER` | 400 | 영문·숫자·특수문자(ASCII) 외 문자 포함 |
| `PASSWORD_LETTER_AND_DIGIT_REQUIRED` | 400 | 영문 또는 숫자가 없음 |
| `PASSWORD_SAME_AS_LOGIN_ID` | 400 | ID와 같음 |
| `PASSWORD_CONFIRM_MISMATCH` | 400 | 비밀번호 확인 불일치 |
| `LOGIN_FAILED` | 401 | ID 또는 비밀번호 불일치 |
| `LOGIN_LOCKED` | 429 | 5회 연속 실패로 5분 잠금 |
| `PASSWORD_INCORRECT` | 400 | 탈퇴 시 비밀번호 재확인 실패 |
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
