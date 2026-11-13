# 배포 가이드 (GCP)

> 배포 대상: **GCP Compute Engine VM 1대 + Docker Compose** (아키텍처의 "단일 서버").
> VM이 아직 만들어지지 않아 **실제 배포는 수행하지 않았습니다.** 아래는 준비된 구조와 절차입니다.

## 1. 구성

| 항목 | 사용 | 비고 |
|---|---|---|
| 서버 | Compute Engine VM | Docker, Docker Compose 설치 |
| DB / Redis | VM 안의 컨테이너 (`docker-compose.yml`) | 데이터는 Docker 볼륨에 저장 |
| 이미지 저장소 | GHCR `ghcr.io/siottuk/siot-backend` | CI가 push, VM이 pull |
| 메일 | Gmail SMTP (`smtp.gmail.com:587`) | 팀 공용 Gmail 계정 + 앱 비밀번호 |
| 파일 저장소 | Cloud Storage (GCS) | PDF·안내문 원본. 기존 아키텍처의 Amazon S3를 대체 |
| AI | Gemini API | |

## 2. CI 파이프라인 (`.github/workflows/backend-ci.yml`)

| 트리거 | 동작 |
|---|---|
| `main` 브랜치 대상 PR | Gradle test + bootJar 빌드 (이미지 push 없음) |
| `main` 브랜치 push | test → 이미지 빌드 → GHCR push (`latest`, `sha-<커밋>`) |
| `backend-v1.2.3` 태그 push | test → 이미지 push (`1.2.3`, `sha-<커밋>`) |
| 수동 실행 (workflow_dispatch) | 위와 동일 |

GHCR push는 워크플로의 `GITHUB_TOKEN`(`packages: write`)을 사용하므로 별도 Secret이 필요 없습니다.
조직 설정에서 다음을 확인합니다.

- Settings → Actions → General → Workflow permissions 에서 패키지 쓰기 허용
- 첫 push 이후 GHCR 패키지는 private 로 생성됨 → VM에서 pull 하려면 `read:packages` 권한의 PAT 필요

## 3. Gmail SMTP 준비

1. 팀 공용 Gmail 계정을 만듭니다. (개인 계정 사용 금지)
2. Google 계정 → 보안 → **2단계 인증** 을 켭니다.
3. Google 계정 → 보안 → **앱 비밀번호** 에서 16자리 비밀번호를 만듭니다. 이 값이 `MAIL_PASSWORD` 입니다.
4. `MAIL_FROM` 은 같은 계정 주소여야 합니다. (Gmail은 다른 주소로 발신하지 못함)

- GCP VM은 **25번 포트로 나가는 연결이 막혀 있어** 587(STARTTLS)을 사용합니다.
- Gmail 무료 계정은 **하루 약 500통**까지 보낼 수 있습니다. 이메일 인증 요청은 회원당 1분 1회·하루 10회, 비밀번호 재설정은 ID당 1분 1회·하루 5회로 제한되어 있습니다.
- 발송 실패는 백엔드 로그에 `Mail send failed` 로 남습니다. (health 체크에는 포함하지 않음)

## 4. 운영 환경변수 (`.env`)

VM의 배포 폴더에만 두고(권한 600) 저장소에 커밋하지 않습니다.

```env
SPRING_PROFILES_ACTIVE=prod
DB_NAME=siot
DB_USERNAME=<운영 DB 계정>
DB_PASSWORD=<강력한 비밀번호>
REDIS_PASSWORD=<강력한 비밀번호>
SESSION_TIMEOUT=14d
BACKEND_PORT=8080
BACKEND_IMAGE=ghcr.io/siottuk/siot-backend:<배포할 태그>

# 메일 (Gmail SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<팀 Gmail 주소>
MAIL_PASSWORD=<앱 비밀번호 16자리>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
MAIL_FROM=웰컴시흥 <팀 Gmail 주소>

# 메일 링크(비밀번호 재설정)에 들어갈 서비스 주소
FRONTEND_BASE_URL=https://<서비스 도메인>
```

- `prod` 프로필: 세션 쿠키 `Secure`, health 상세 정보 비공개, 프록시 헤더(`X-Forwarded-*`) 처리.
- HTTPS 종료는 앞단 Nginx(프론트엔드 측)에서 처리합니다.
- **`MAIL_HOST` 를 넣지 않으면 개발용 `mailpit` 으로 보내려다 실패합니다.** 운영에서는 반드시 Gmail 값을 넣습니다. (`mailpit` 은 `dev` 프로필에서만 실행)
- Gemini API 키, Cloud Storage 버킷 등은 해당 기능 구현 시 추가합니다.

## 5. Cloud Storage (구현 시 참고)

- 버킷은 VM과 같은 리전에 만들고, 공개 접근을 막습니다.
- VM에 **전용 서비스 계정**을 연결하고 그 계정에 버킷 권한(`Storage Object Admin` 등 필요한 최소 권한)을 줍니다.
  이렇게 하면 서비스 계정 키 파일을 서버에 두지 않아도 됩니다. (애플리케이션 기본 사용자 인증 정보 사용)
- 로컬 개발은 `gcloud auth application-default login` 또는 별도 개발 버킷을 사용합니다.

## 6. VM 준비 (최초 1회)

```bash
# Docker 설치 후
echo <PAT(read:packages)> | docker login ghcr.io -u <github-id> --password-stdin
mkdir -p ~/siot && cd ~/siot
# docker-compose.yml, .env 배치 (.env 권한 600)
```

- 방화벽: 외부에는 Nginx(80/443)만 엽니다. 8080·5432·6379는 열지 않습니다. (compose 에서 DB/Redis는 127.0.0.1에만 바인딩)
- VM 디스크 백업(스냅샷 일정)을 설정해 DB 볼륨을 보호합니다.

## 7. 배포 / 롤백

```bash
cd ~/siot
docker compose pull backend
docker compose up -d --no-build
docker compose ps
curl -f localhost:8080/actuator/health/readiness
```

롤백: `.env` 의 `BACKEND_IMAGE` 를 이전 `sha-` 태그로 바꾸고 `docker compose up -d --no-build`.

## 8. 자동 배포 (VM 생성 후 추가 예정)

CI의 `image` job 뒤에 SSH 배포 job 을 추가합니다. 필요한 Secret 예시:
`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`. VM에서 실행할 명령은 7번과 같습니다.
