# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어: 빌드 스크립트가 바뀔 때만 다시 받는다.
# 받은 파일을 레이어(/root/.gradle)에 남긴다. --mount=type=cache 는 CI 의 레이어 캐시(type=gha)에 저장되지 않아,
# 이 레이어가 캐시로 건너뛰어지면 bootJar 가 새 러너에서 매번 Maven Central 에서 전부 다시 받다가 429 로 막혔다.
# downloadDependencies 는 라이브러리 파일(JAR)까지, buildEnvironment 는 bootJar 가 쓰는 플러그인 쪽 의존성(spring-boot-loader-tools 등)까지 받는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew
RUN ./gradlew downloadDependencies buildEnvironment --no-daemon -q > /dev/null

# 위 레이어에 받은 것만 쓴다 (네트워크를 쓰지 않으므로 저장소 요청 제한에 걸리지 않는다)
COPY src src
RUN ./gradlew bootJar --offline --no-daemon -q -x test

# 레이어로 풀기: 라이브러리(dependencies)는 빌드마다 같은 레이어가 되어 Artifact Registry 에 한 번만 저장되고,
# 빌드마다 새로 쌓이는 것은 application 레이어(약 1MB)뿐이다.
# 파일 시각을 고정해야 내용이 같을 때 레이어 digest 도 같아진다.
RUN cp build/libs/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted \
 && find extracted -exec touch -h -d @0 {} +

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
# 업로드한 문서(PDF) 저장 폴더 (ADM-02). 볼륨이 처음 붙을 때 이 소유자를 이어받아 spring 이 쓸 수 있다
RUN mkdir -p /app/data/documents && chown -R spring:spring /app/data

# 자주 바뀌지 않는 레이어부터 복사한다
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Seoul"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
