# 로컬 개발 환경

## 요구 사항

- Docker Desktop (Compose v2)
- JDK 21 (IDE에서 직접 실행하는 경우만. Docker만으로도 빌드/실행 가능)

## 1. 환경변수

```bash
cp .env.example .env
```

`.env` 는 `.gitignore` 대상입니다. 비밀번호는 각자 임의 값으로 바꿔 사용합니다.

| 변수 | 설명 | 기본값 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` / `prod` | local |
| `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL 접속 정보 | - |
| `DB_URL` | JDBC URL (IDE 실행 시) | `jdbc:postgresql://localhost:5432/siot` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis 접속 정보 | localhost / 6379 / - |
| `SESSION_TIMEOUT` | 세션 만료 시간 (마지막 요청 기준, M-10) | 14d |
| `BACKEND_PORT` | 호스트에 노출할 포트 | 8080 |
| `SCHEDULING_ENABLED` | 예약 작업 사용 여부 | true |
| `EVENT_COLLECT_CRON` | 행사 수집 주기 (EVE-01, Asia/Seoul) | `0 0 3 * * *` |
| `EVENT_PUBLIC_REGIONS` | 사용자에게 보여 주는 행사 지역 (`SIHEUNG`, `GYEONGGI`, 쉼표로 여러 개). `SIHEUNG,GYEONGGI`면 지금과 같은 동작(경기도 행사도 보임). 수집·관리자 API 는 모든 지역 ([api-events.md](api-events.md)) | `SIHEUNG` |
| `NOTIFICATION_DISPATCH_INTERVAL` | 예약 알림 발송 확인 주기 (NTF-05) | 1m |
| `DOCUMENT_STORAGE_DIR` | 업로드한 문서(PDF) 저장 폴더 (ADM-02). compose 는 `document-data` 볼륨 | `./data/documents` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP 서버 | 로컬 localhost:1025, compose mailpit:1025 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 계정 (운영: Gmail 앱 비밀번호) | - |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | SMTP 인증·TLS | 로컬 false, 운영 true |
| `MAIL_FROM` | 발신자 | `웰컴시흥 <no-reply@siot.local>` |
| `FRONTEND_BASE_URL` | 메일 링크에 쓰는 프론트엔드 주소 | `http://localhost:5173` |

## 2-A. 전체를 Docker로 실행

```bash
docker compose up -d --build
docker compose ps          # 3개 서비스 모두 healthy 확인
curl localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

## 2-B. DB/Redis만 Docker, 백엔드는 IDE에서 실행

```bash
docker compose up -d postgres redis
# 메일 기능을 확인하려면 개발용 메일 서버도 띄운다
docker compose --profile dev up -d mailpit
./gradlew bootRun          # local 프로필, 프로젝트 루트의 .env 를 자동으로 읽음
```

`application-local.yml` 이 `optional:file:.env[.properties]` 를 import 하므로 IntelliJ에서도 별도 환경변수 설정 없이 실행됩니다.

## 메일 확인 (Mailpit)

로컬과 compose 의 메일은 실제로 발송되지 않고 개발용 메일 서버 Mailpit 에 쌓입니다.

```bash
docker compose --profile dev up -d   # 전체 + mailpit
```

받은 메일 확인: http://localhost:8025 (가입·이메일 인증 코드, 비밀번호 재설정 링크)

## API 문서 (Swagger UI)

백엔드를 띄운 뒤 http://localhost:8080/swagger-ui.html (OpenAPI JSON: `/v3/api-docs`)

1. `GET /api/auth/csrf` 실행 (XSRF-TOKEN 쿠키 발급)
2. `POST /api/auth/login` 실행 → 세션 쿠키(SIOT_SESSION)가 브라우저에 저장됨
3. 이후 다른 API 를 "Try it out" 으로 호출. POST·PUT 등의 `X-XSRF-TOKEN` 헤더는 Swagger UI 가 자동으로 넣습니다.

관리자 API(`/api/admin/**`)는 관리자 계정으로 로그인해야 합니다 (`SIOT_ADMIN_EMAILS`).
IDE 실행(`local` 프로필)은 기본으로 켜져 있습니다. `prod` 프로필(운영, `docker compose` 전체 실행)은 기본으로 꺼져 있어서 `.env` 에 `SWAGGER_ENABLED=true` 를 넣어야 합니다. 운영에서 켜면 문서는 누구나 볼 수 있습니다 (API 호출 권한은 그대로).

## 테스트

```bash
./gradlew test
```

Testcontainers가 `pgvector/pgvector:pg16`, `redis:7-alpine` 컨테이너를 자동으로 띄우므로 Docker가 실행 중이어야 합니다.
검증 항목: Flyway 적용, Entity-스키마 validate, pgvector 확장, Redis 연결, Actuator health, 미인증 401 응답.

## 정리

```bash
docker compose down        # 컨테이너 종료 (데이터 유지)
docker compose down -v     # 볼륨까지 삭제 (DB 초기화)
```
