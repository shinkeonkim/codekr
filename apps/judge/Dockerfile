# syntax=docker/dockerfile:1
# 빌드 컨텍스트는 저장소 루트다 (libs/gocontract 를 함께 담기 위함).

FROM golang:1.26-alpine AS builder
WORKDIR /src

COPY libs/gocontract ./libs/gocontract
COPY apps/judge/go.mod apps/judge/go.sum ./apps/judge/
WORKDIR /src/apps/judge
RUN go mod download

COPY apps/judge ./
RUN CGO_ENABLED=0 go build -trimpath -o /out/judge ./cmd/judge

FROM alpine:3.21
RUN apk add --no-cache ca-certificates \
    && addgroup -g 10001 app \
    && adduser -u 10001 -G app -S app

COPY --from=builder /out/judge /usr/local/bin/judge

# 채점기는 사용자 코드를 직접 실행하지 않고 DB 자격증명도 갖지 않는다 (ADR-0004).
USER 10001
EXPOSE 8082
ENTRYPOINT ["/usr/local/bin/judge"]
