# ===== Stage 1: Build =====
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Gradle 의존성 레이어 캐싱 (소스 변경 시 재다운로드 방지)
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew
RUN ./gradlew dependencies --no-daemon -q

# 소스 빌드 (테스트 제외)
COPY src/ src/
RUN ./gradlew clean build -x test --no-daemon -q

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# 컨테이너 헬스체크: 30초마다 /actuator/health 호출
# — Docker Compose depends_on condition: service_healthy 연동
# — 3회 연속 실패 시 unhealthy 상태로 전환
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# JVM 옵션 설명:
#   UseContainerSupport      — Cgroup 메모리 제한 자동 인식
#   MaxRAMPercentage=75.0    — 컨테이너 메모리의 75%를 힙 상한으로 사용 (-Xmx 대체)
#   InitialRAMPercentage=50.0 — 초기 힙 50% (OOM 여유 확보)
#   UseG1GC                  — 대용량 힙에서 안정적인 STW 시간
#   Xlog:gc*                 — GC 로그 (5개 × 10MB 롤링)
#   java.security.egd        — /dev/random 블로킹 방지 (컨테이너 엔트로피 부족 해결)
RUN mkdir -p /app/logs
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  "-XX:+UseG1GC", \
  "-Xlog:gc*:file=/app/logs/gc.log:time,uptime:filecount=5,filesize=10m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
