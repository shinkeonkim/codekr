# syntax=docker/dockerfile:1

FROM oven/bun:1.3-alpine AS deps
WORKDIR /app
COPY apps/web/package.json apps/web/bun.lock* ./
RUN bun install --frozen-lockfile || bun install

FROM oven/bun:1.3-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY apps/web ./
# 브라우저 번들에 들어갈 값이라 빌드 시점에 주입해야 한다.
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_WS_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_WS_BASE_URL=$NEXT_PUBLIC_WS_BASE_URL
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
