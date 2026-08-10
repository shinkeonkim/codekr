# syntax=docker/dockerfile:1
# Kotlin 은 공식 컴파일러 이미지가 없어 직접 만든다.
# JDK 위에 kotlinc 배포본만 얹는 얇은 이미지다.

FROM eclipse-temurin:21-jdk-alpine

ARG KOTLIN_VERSION=2.2.0

RUN apk add --no-cache bash curl unzip \
    && curl -fsSL -o /tmp/kotlin.zip \
       "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" \
    && unzip -q /tmp/kotlin.zip -d /opt \
    && rm /tmp/kotlin.zip \
    && chmod -R a+rx /opt/kotlinc

ENV PATH="/opt/kotlinc/bin:${PATH}"
# kotlinc 는 홈 디렉터리에 캐시를 만들려 한다. 읽기 전용 rootfs 에서 실패하지 않도록
# 쓰기 가능한 작업 디렉터리로 돌려 둔다.
ENV HOME=/work
