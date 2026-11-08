# Siot Backend

Siot 서비스의 Spring Boot 백엔드입니다. (프론트엔드는 별도 브랜치에서 관리)

| 항목 | 내용 |
|---|---|
| Runtime | Java 21, Spring Boot 4.1 |
| Build | Gradle 9 (Wrapper 포함) |
| DB | PostgreSQL 16 + pgvector, Flyway |
| Session | Spring Session Data Redis |
| Monitoring | Spring Boot Actuator (`/actuator/health`) |
| CI/CD | GitHub Actions → GHCR (`ghcr.io/siottuk/siot-backend`) |

## 빠른 시작

```bash
cp .env.example .env          # 비밀번호 등 값 수정
docker compose up -d --build  # backend + postgres + redis
curl localhost:8080/actuator/health
```

자세한 내용:

- **[개발 규칙 (API 경로·권한, CSRF, 응답 형식, 마이그레이션) — 작업 전 필독](docs/conventions.md)**
- [로컬 개발 환경](docs/setup.md)
- [배포 가이드](docs/deployment.md)
- [전체 Docker Compose 통합 (프론트엔드 담당자용)](docs/compose-integration.md)

## 패키지 구조 (Package by Feature)

```
com.siot.backend
├── auth          인증 (세션 로그인, 이메일 인증, 비밀번호 재설정, refresh_token)
├── member        회원 (app_user)
├── checklist     체크리스트·생활수칙 (guide, guide_translation, checklist_progress)
├── schedule      생활 달력 (calendar_schedule)
├── event         행사 (event, event_translation, event_subscription)
├── reservation   예약 (ERD 미정 - 빈 패키지)
├── notification  알림 (notification, notification_preference)
├── rag           문서·임베딩·챗봇 (document*, guide_document_reference, chat_*)
├── report        오류 신고 (content_report)
└── global        config / exception / common (ApiResponse, PageResponse)
```

각 기능 패키지는 필요에 따라 `controller / service / repository / domain / dto` 하위 패키지를 둡니다.

## 데이터베이스 규칙

- 스키마 변경은 **Flyway 마이그레이션만** 사용합니다. 파일 이름은 `V{yyyy}_{MM}_{dd}_{HHmm}__{설명}.sql` ([개발 규칙](docs/conventions.md#5-db-마이그레이션-flyway)).
- JPA는 `ddl-auto: validate` 로 Entity와 스키마 불일치 시 기동에 실패합니다.
- 이미 적용된 마이그레이션 파일은 수정하지 말고 새 버전을 추가합니다.
