# 배포 가이드 (GCP)

> 배포 대상: **GCP Compute Engine VM 1대 + Docker Compose** (아키텍처의 "단일 서버").
> `main` 에 push 하면 GitHub Actions 가 테스트 → 이미지 push(Artifact Registry) → VM 배포까지 자동으로 한다.

| 항목 | 값 |
|---|---|
| GCP 프로젝트 | `siot-514601` (번호 `511164544275`) |
| VM | `instance-20261113-012436` · `asia-northeast3-c` · e2-small(2GB) + 스왑 2GB · Ubuntu 22.04 |
| 외부 IP | `34.22.97.3` (고정 IP `siot-backend-ip`) |
| 서비스 주소 | **https://34-22-97-3.sslip.io** (프론트엔드 + `/api`, Swagger: `/swagger-ui.html`). sslip.io 는 IP 를 이름으로 바꿔 주는 무료 DNS 라 도메인 없이 HTTPS 인증서를 받는다 |
| 프론트엔드 | VM `/opt/siot/frontend` (Siot-FE `npm run build` 결과 `dist/`) |
| 배포 폴더 | VM `/opt/siot` (`docker-compose.yml`, `.env` — root 소유, 권한 600) |
| 이미지 | `asia-northeast3-docker.pkg.dev/siot-514601/siot/siot-backend` |

## 1. 구성

| 항목 | 사용 | 비고 |
|---|---|---|
| 서버 | Compute Engine VM | Docker, Docker Compose 설치 |
| DB / Redis | VM 안의 컨테이너 (`docker-compose.yml`) | 데이터는 Docker 볼륨에 저장 |
| 이미지 저장소 | Artifact Registry `asia-northeast3-docker.pkg.dev/siot-514601/siot` | CI가 push, VM이 pull (VM 서비스 계정에 읽기 권한) |
| 메일 | Gmail SMTP (`smtp.gmail.com:587`) | 팀 공용 Gmail 계정 + 앱 비밀번호 |
| 파일 저장소 | Cloud Storage (GCS) | PDF·안내문 원본. 기존 아키텍처의 Amazon S3를 대체 |
| AI | Gemini (Vertex AI, VM 서비스 계정으로 인증) | |

## 2. CI/CD 파이프라인 (`.github/workflows/backend-ci.yml`)

| 트리거 | 동작 |
|---|---|
| `main` 브랜치 대상 PR | Gradle test + bootJar 빌드 (이미지 push 없음) |
| `main` 브랜치 push | test → 이미지 push (`latest`, `sha-<커밋>`) → **VM 배포** |
| `backend-v1.2.3` 태그 push | test → 이미지 push (`1.2.3`, `sha-<커밋>`) (배포 안 함) |
| 수동 실행 (workflow_dispatch, main) | `main` push 와 동일 |

### GCP 인증: Workload Identity Federation

GitHub Actions 가 발급하는 OIDC 토큰으로 GCP 서비스 계정 `github-deployer` 를 사용한다.
**서비스 계정 키 파일과 GitHub Secret 이 없다.** 워크플로의 `env` 값(프로젝트 번호, Provider 이름, 서비스 계정 이메일)은 식별자일 뿐 비밀 값이 아니다.

| GCP 리소스 | 설정 |
|---|---|
| Workload Identity Pool / Provider | `github` / `siot-be` — `SiotTUK/Siot-BE` 저장소의 `main` 브랜치와 `backend-v*` 태그만 허용 |
| 서비스 계정 `github-deployer` | Artifact Registry `siot` 저장소 쓰기, VM 1대에 관리자 SSH(OS Login), VM 기본 서비스 계정 사용, Compute 읽기 |

배포 job 은 `gcloud compute ssh`(OS Login)로 VM 에 접속해 `.env` 의 `BACKEND_IMAGE` 를 `sha-<커밋>` 으로 바꾸고 `docker compose up -d` 한 뒤 readiness 가 200 이 될 때까지(최대 3분) 기다린다. 실패하면 backend 로그를 출력하고 job 이 실패한다.

## 3. Gmail SMTP 준비

1. 팀 공용 Gmail 계정을 만듭니다. (개인 계정 사용 금지)
2. Google 계정 → 보안 → **2단계 인증** 을 켭니다.
3. Google 계정 → 보안 → **앱 비밀번호** 에서 16자리 비밀번호를 만듭니다. 이 값이 `MAIL_PASSWORD` 입니다.
4. `MAIL_FROM` 은 같은 계정 주소여야 합니다. (Gmail은 다른 주소로 발신하지 못함)

- GCP VM은 **25번 포트로 나가는 연결이 막혀 있어** 587(STARTTLS)을 사용합니다.
- Gmail 무료 계정은 **하루 약 500통**까지 보낼 수 있습니다. 가입·이메일 인증 코드 요청은 이메일(회원)당 1분 1회·하루 10회, 비밀번호 재설정은 이메일당 1분 1회·하루 5회로 제한되어 있습니다.
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
BACKEND_PORT=127.0.0.1:8080   # 외부에는 Caddy(80/443)만 연다

# 웹 서버 (Caddy): 프론트엔드 + /api 프록시 + HTTPS 자동 발급
COMPOSE_PROFILES=web
SITE_ADDRESS=34-22-97-3.sslip.io
SWAGGER_ENABLED=false         # 지금은 임시로 true

# 관리자로 지정할 이메일 (쉼표 구분)
SIOT_ADMIN_EMAILS=<관리자 이메일>

# Gemini (챗봇·번역·문서 적재). Vertex AI 를 VM 서비스 계정(ADC)으로 호출한다. 서비스 계정에 Vertex AI 사용 권한 필요
# 비우면 GEMINI_API_KEY(AI Studio) 를 쓰고, 둘 다 비면 챗봇은 안내 문구로만 응답
GEMINI_PROJECT_ID=siot-514601
GEMINI_CHAT_MODEL=gemini-3.1-flash-lite
BACKEND_IMAGE=asia-northeast3-docker.pkg.dev/siot-514601/siot/siot-backend:<배포할 태그>   # CI 가 배포 때마다 바꿈

# 메일 (Gmail SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<팀 Gmail 주소>
MAIL_PASSWORD=<앱 비밀번호 16자리>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
MAIL_FROM=웰컴시흥 <팀 Gmail 주소>

# 메일 링크(비밀번호 재설정)에 들어갈 서비스 주소
FRONTEND_BASE_URL=https://34-22-97-3.sslip.io
```

- `prod` 프로필: 세션 쿠키 `Secure`, health 상세 정보 비공개, 프록시 헤더(`X-Forwarded-*`) 처리.
- HTTPS 종료는 같은 compose 의 `caddy` 서비스(web 프로필)가 한다. 인증서는 Let's Encrypt 에서 자동 발급·갱신되고 `caddy-data` 볼륨에 저장된다.
- 도메인을 바꿀 때: DNS A 레코드를 `34.22.97.3` 으로 두고 `.env` 의 `SITE_ADDRESS`·`FRONTEND_BASE_URL` 을 바꾼 뒤 `sudo docker compose up -d --no-build`.
- **`MAIL_HOST` 를 넣지 않으면 개발용 `mailpit` 으로 보내려다 실패합니다.** 운영에서는 반드시 Gmail 값을 넣습니다. (`mailpit` 은 `dev` 프로필에서만 실행)
- Cloud Storage 버킷 등은 해당 기능 구현 시 추가합니다.

## 5. Cloud Storage (구현 시 참고)

- 버킷은 VM과 같은 리전에 만들고, 공개 접근을 막습니다.
- VM에 **전용 서비스 계정**을 연결하고 그 계정에 버킷 권한(`Storage Object Admin` 등 필요한 최소 권한)을 줍니다.
  이렇게 하면 서비스 계정 키 파일을 서버에 두지 않아도 됩니다. (애플리케이션 기본 사용자 인증 정보 사용)
- 로컬 개발은 `gcloud auth application-default login` 또는 별도 개발 버킷을 사용합니다.

## 6. VM 준비 (완료됨, 다시 만들 때 참고)

1. Docker Engine + Compose plugin 설치 (Docker 공식 apt 저장소), 스왑 2GB (`/swapfile`)
2. `/opt/siot` 에 `docker-compose.yml`, `.env`(권한 600) 배치. DB·Redis 비밀번호는 VM 에서 `openssl rand -hex 24` 로 생성
3. `sudo gcloud auth configure-docker asia-northeast3-docker.pkg.dev` (root 의 docker 가 Artifact Registry 사용)
4. VM 메타데이터 `enable-oslogin=TRUE`

- 방화벽: 외부에는 Caddy(80/443)만 연다 (`default-allow-http`·`default-allow-https`, 태그 `http-server`·`https-server`). 8080·5432·6379는 열지 않고 백엔드는 `BACKEND_PORT=127.0.0.1:8080` 으로 VM 내부에만 바인딩한다.
- 2026-11-14~21 동안 임시로 열었던 `http://34.22.97.3:8080` (방화벽 규칙 `siot-backend-8080`)은 Caddy·HTTPS 를 붙이면서 닫았다. Swagger 는 `https://34-22-97-3.sslip.io/swagger-ui.html` (`SWAGGER_ENABLED=true` 인 동안).
- DB 백업: 아래 [8. 백업](#8-백업) 참고 (VM 디스크 스냅샷 일정, 설정됨).
- 접속: `gcloud compute ssh instance-20261113-012436 --zone asia-northeast3-c` (OS Login, 프로젝트 권한 필요)

## 7. 수동 배포 / 롤백

평소에는 CI 가 배포한다. VM 에서 직접 할 때:

```bash
cd /opt/siot
sudo docker compose pull backend
sudo docker compose up -d --no-build
sudo docker compose ps
curl -f localhost:8080/actuator/health/readiness
```

롤백: `.env` 의 `BACKEND_IMAGE` 를 이전 `sha-` 태그로 바꾸고 `sudo docker compose up -d --no-build`.
태그 목록: `gcloud artifacts docker tags list asia-northeast3-docker.pkg.dev/siot-514601/siot/siot-backend`

### 프론트엔드 배포 (수동)

프론트엔드는 아직 CI 가 없다. Siot-FE 에서 빌드해 VM 의 `/opt/siot/frontend` 를 바꾼다. Caddy 는 파일을 바로 읽으므로 재시작이 필요 없다.

```bash
# Siot-FE 저장소에서
npm ci && npm run build
tar -czf fe-dist.tgz -C dist .
gcloud compute scp fe-dist.tgz instance-20261113-012436:/tmp/ --zone asia-northeast3-c
gcloud compute ssh instance-20261113-012436 --zone asia-northeast3-c --command '
  sudo rm -rf /opt/siot/frontend.old && sudo cp -a /opt/siot/frontend /opt/siot/frontend.old &&
  sudo find /opt/siot/frontend -mindepth 1 -delete &&
  sudo tar -xzf /tmp/fe-dist.tgz -C /opt/siot/frontend && rm /tmp/fe-dist.tgz'
```

- 폴더를 `mv` 로 바꿔치기하면 안 된다. 컨테이너의 bind mount 는 원래 폴더를 계속 가리켜서 예전 파일이 나간다. 위처럼 **폴더 안의 내용만** 바꾼다.
- 롤백: `sudo find /opt/siot/frontend -mindepth 1 -delete && sudo cp -a /opt/siot/frontend.old/. /opt/siot/frontend/`.

### 이미지 정리 정책

Artifact Registry `siot` 저장소에 정리 정책이 걸려 있다. `siot-backend` 는 **최근 10개 버전만 남기고** 나머지는 지운다 (정책 `keep-recent-10` + `delete-older`, 하루 1번 정도 실행). 롤백은 최근 10개 배포 안에서만 가능하다.

- CI 는 `provenance: false` 로 빌드 1번에 이미지 버전 1개만 만든다. (켜면 index·attestation 버전이 함께 생겨 10개가 3~4개 빌드로 줄어든다)
- Dockerfile 은 Spring Boot 레이어 jar 로 풀어서 넣는다. 라이브러리 레이어(약 67MB)는 의존성이 바뀌지 않으면 빌드끼리 공유되어, 빌드마다 늘어나는 용량은 약 1MB 다.

```bash
gcloud artifacts repositories describe siot --location asia-northeast3 --project siot-514601   # Repository Size
```

## 8. 백업

DB·Redis 데이터(Docker 볼륨)는 VM 부팅 디스크 안에 있으므로 **디스크 스냅샷 일정**으로 백업한다.

| 항목 | 값 |
|---|---|
| 스냅샷 일정 | `default-schedule-1` (리전 `asia-northeast3`) → 디스크 `instance-20261113-012436` 에 연결 |
| 주기 | 매일 02:00 KST (17:00 UTC) 시작, 4시간 안에 생성 |
| 보관 | 14일. 디스크·VM 을 지워도 스냅샷은 남김 (`KEEP_AUTO_SNAPSHOTS`) |
| 저장 위치 | `asia` 멀티 리전 |
| 일관성 | 실행 중 촬영(`guestFlush: false`). 복원 시 PostgreSQL 이 비정상 종료 후 재시작처럼 WAL 로 복구 |

```bash
# 스냅샷 목록
gcloud compute snapshots list --filter="sourceDisk~instance-20261113-012436" --project siot-514601
```

### 복원

디스크 전체를 스냅샷 시점으로 되돌리는 방식이다. (테이블 하나만 되살리는 논리 백업(pg_dump)은 없다)

1. 스냅샷으로 새 디스크를 만든다.
   `gcloud compute disks create siot-restore --source-snapshot <스냅샷 이름> --zone asia-northeast3-c --type pd-balanced`
2. **VM 전체 복구**: VM 을 멈추고 부팅 디스크를 새 디스크로 바꾼 뒤 시작한다.
3. **일부 데이터만 복구**: 새 디스크로 임시 VM 을 만들어 `/opt/siot` 에서 `sudo docker compose up -d postgres` 후 `pg_dump` 로 필요한 데이터를 꺼내 운영 DB 에 넣는다. 끝나면 임시 VM·디스크를 지운다.
