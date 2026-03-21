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

ENTRYPOINT ["java", "-jar", "app.jar"]
