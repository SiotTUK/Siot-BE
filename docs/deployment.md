# 배포 가이드

> 현재 운영 서버 정보가 정해지지 않아 **실제 배포는 수행하지 않았습니다.**
> 아래는 CI 이미지 빌드 구조와 서버 배포 절차입니다.

## 1. CI 파이프라인 (`.github/workflows/backend-ci.yml`)

| 트리거 | 동작 |
|---|---|
| `main` 브랜치 대상 PR | Gradle test + bootJar 빌드 (이미지 push 없음) |
| `main` 브랜치 push | test → 이미지 빌드 → GHCR push (`latest`, `sha-<커밋>`) |
| `backend-v1.2.3` 태그 push | test → 이미지 push (`1.2.3`, `sha-<커밋>`) |
| 수동 실행 (workflow_dispatch) | 위와 동일 |

이미지: `ghcr.io/siottuk/siot-backend`

GHCR push는 워크플로의 `GITHUB_TOKEN`(`packages: write`)을 사용하므로 별도 Secret이 필요 없습니다.
단, 조직 설정에서 다음을 확인해야 합니다.

- Settings → Actions → General → Workflow permissions 에서 패키지 쓰기 허용
- 첫 push 이후 GHCR 패키지가 private 로 생성됨 → 서버에서 pull 하려면 패키지 접근 권한 또는 PAT 필요

## 2. 운영 환경변수

운영에서는 `.env` 를 서버에만 두고(권한 600) 저장소에 커밋하지 않습니다.

```env
SPRING_PROFILES_ACTIVE=prod
DB_NAME=siot
DB_USERNAME=<운영 DB 계정>
DB_PASSWORD=<강력한 비밀번호>
REDIS_PASSWORD=<강력한 비밀번호>
SESSION_TIMEOUT=14d
BACKEND_PORT=8080
BACKEND_IMAGE=ghcr.io/siottuk/siot-backend:<배포할 태그>
```

`prod` 프로필 특징: 세션 쿠키 `Secure`, health 상세 정보 비공개, 프록시 헤더(`X-Forwarded-*`) 처리.
HTTPS 종료는 앞단 Nginx(프론트엔드 측)에서 처리하는 것을 전제로 합니다.

외부 API 키(Gemini, S3 등)는 해당 기능 구현 시 환경변수로 추가합니다.

## 3. 서버 배포 절차 (수동)

```bash
# 최초 1회
echo <PAT(read:packages)> | docker login ghcr.io -u <github-id> --password-stdin
# 서버에 docker-compose.yml, .env 배치

# 배포
docker compose pull backend
docker compose up -d --no-build
docker compose ps
curl -f localhost:8080/actuator/health/readiness
```

롤백: `.env` 의 `BACKEND_IMAGE` 를 이전 `sha-` 태그로 바꾸고 `docker compose up -d --no-build`.

## 4. 향후 자동 배포 (서버 확정 후)

`image` job 뒤에 SSH 배포 job 을 추가합니다. 필요한 Secret 예시:
`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`. 서버에서 실행할 명령은 3번 절차와 동일합니다.
