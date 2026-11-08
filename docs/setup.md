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
| `NOTIFICATION_DISPATCH_INTERVAL` | 예약 알림 발송 확인 주기 (NTF-05) | 1m |

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
./gradlew bootRun          # local 프로필, 프로젝트 루트의 .env 를 자동으로 읽음
```

`application-local.yml` 이 `optional:file:.env[.properties]` 를 import 하므로 IntelliJ에서도 별도 환경변수 설정 없이 실행됩니다.

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
