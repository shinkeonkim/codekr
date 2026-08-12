# syntax=docker/dockerfile:1

FROM oven/bun:1.3-alpine AS deps
WORKDIR /app
COPY apps/web/package.json apps/web/bun.lock* ./
RUN bun install --frozen-lockfile || bun install

FROM oven/bun:1.3-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY apps/web ./
# 브라우저 번들에 들어갈 값이라 빌드 시점에만 주입할 수 있다.
#
# **배포 이미지는 비워 둔다.** 값을 박으면 환경마다 이미지를 따로 빌드해야 하고,
# 같은 이미지를 공개 주소(코드.kr)와 내부 주소로 함께 서비스할 수 없다.
# 비워 두면 브라우저가 자기가 열린 출처를 쓴다 (shared/config).
#
# 로컬 개발만 web(:13000)과 api(:18080)의 출처가 달라 값을 넣는다 (compose.yml).
ARG NEXT_PUBLIC_API_BASE_URL=""
ARG NEXT_PUBLIC_WS_BASE_URL=""
# `/api` 를 넘길 곳 (#314). **빌드 시점에 박힌다** — Next 는 rewrite 를 빌드 때
# routes-manifest 에 굳히므로 실행 시 환경 변수로는 바꿀 수 없다.
#
# 운영에서는 이 값이 쓰이지 않는다. Ingress 가 `/api` 를 api 로 먼저 보내므로 web
# 파드에는 그 요청이 오지 않는다.
ARG API_INTERNAL_BASE_URL="http://api:8080"
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_WS_BASE_URL=$NEXT_PUBLIC_WS_BASE_URL
ENV API_INTERNAL_BASE_URL=$API_INTERNAL_BASE_URL
ENV NEXT_TELEMETRY_DISABLED=1
RUN bun run build

FROM oven/bun:1.3-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -g 10001 app && adduser -u 10001 -G app -S app

COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

USER 10001
EXPOSE 3000
CMD ["bun", "server.js"]
