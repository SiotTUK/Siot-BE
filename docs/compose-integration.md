# 전체 Docker Compose 통합 가이드 (프론트엔드 담당자용)

백엔드 저장소의 `docker-compose.yml` 은 `backend`, `postgres`, `redis` 만 포함합니다.
프론트엔드(Nginx) 컨테이너를 합칠 때 아래 내용을 참고하세요.

## Backend 서비스 명세

| 항목 | 값 |
|---|---|
| 이미지 | `ghcr.io/siottuk/siot-backend:<tag>` |
| 내부 포트 | `8080` |
| Compose 서비스명 | `backend` (같은 네트워크에서 `http://backend:8080`) |
| API prefix | `/api/**` |
| Health | `GET /actuator/health/liveness`, `GET /actuator/health/readiness` |
| 의존 서비스 | `postgres`(healthy), `redis`(healthy) |
| 필수 환경변수 | `SPRING_PROFILES_ACTIVE`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` |

## 통합 예시

```yaml
services:
  frontend:
    image: ghcr.io/siottuk/siot-frontend:latest   # 예시
    ports:
      - "80:80"
    depends_on:
      backend:
        condition: service_healthy

  backend:
    image: ghcr.io/siottuk/siot-backend:latest
    # 통합 환경에서는 외부 포트를 열지 않고 Nginx 를 통해서만 접근하는 것을 권장
    expose:
      - "8080"
    # environment / depends_on 은 백엔드 docker-compose.yml 과 동일
```

## Nginx 프록시 설정 예시

```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

## 세션 / CSRF 관련 (프론트엔드 연동 시 필요)

- 인증은 **세션 쿠키** 방식입니다. 쿠키 이름: `SIOT_SESSION` (HttpOnly, SameSite=Lax, 운영에서 Secure).
- 같은 도메인에서 `/api` 를 프록시하면 CORS 설정이 필요 없습니다. 요청 시 `credentials: 'include'`(fetch) 또는 `withCredentials: true`(axios)를 사용합니다.
- CSRF 보호가 켜져 있습니다. 서버가 내려주는 `XSRF-TOKEN` 쿠키 값을 상태 변경 요청(POST/PUT/PATCH/DELETE)의 `X-XSRF-TOKEN` 헤더로 보내야 합니다. (axios는 기본 동작으로 자동 처리)
