# syntax=docker/dockerfile:1
# 빌드 컨텍스트는 저장소 루트다 (모든 앱이 같은 컨텍스트를 쓰도록 통일).

FROM gradle:9-jdk21 AS builder
WORKDIR /workspace

# 의존성 캐시를 먼저 확보한다 (소스만 바뀌면 이 레이어를 재사용).
COPY apps/api/gradle gradle
COPY apps/api/gradlew apps/api/settings.gradle.kts apps/api/build.gradle.kts apps/api/gradle.properties ./
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY apps/api/src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 10001 app && adduser -u 10001 -G app -S app
COPY --from=builder /workspace/build/libs/app.jar app.jar
# 런타임 정의는 executor 와 같은 파일을 쓴다 (ADR-0003).
COPY infra/runtimes/runtimes.yaml /etc/codekr/runtimes.yaml

USER 10001
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
