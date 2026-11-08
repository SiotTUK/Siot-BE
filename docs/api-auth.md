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
