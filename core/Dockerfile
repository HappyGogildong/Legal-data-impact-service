# LIA core — 멀티스테이지 이미지 (Boot 4.0 · Java 21)
# build: gradle wrapper 로 bootJar (테스트는 CI 소유 → -x test)
# runtime: JRE 21 slim + non-root + actuator 헬스체크

# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
# 1) 의존성 캐시 레이어: 빌드 스크립트·wrapper 먼저 복사 → 소스 변경 시 재다운로드 회피
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true
# 2) 소스 복사 후 bootJar (테스트 제외 — CI 가 ./gradlew test 수행)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:21-jre AS runtime
# 헬스체크용 curl (temurin jre = Ubuntu 계열)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN useradd -r -u 1001 lia
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER lia
EXPOSE 8080
# start-period: Spring 기동 + Flyway 마이그레이션 여유
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
