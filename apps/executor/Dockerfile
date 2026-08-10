# syntax=docker/dockerfile:1
# 빌드 컨텍스트는 저장소 루트다 (libs/gocontract 와 infra/runtimes 를 함께 담기 위함).

FROM golang:1.26-alpine AS builder
WORKDIR /src

COPY libs/gocontract ./libs/gocontract
COPY apps/executor/go.mod apps/executor/go.sum ./apps/executor/
WORKDIR /src/apps/executor
RUN go mod download

COPY apps/executor ./
RUN CGO_ENABLED=0 go build -trimpath -o /out/executor ./cmd/executor

FROM alpine:3.21
RUN apk add --no-cache ca-certificates

COPY --from=builder /out/executor /usr/local/bin/executor
COPY infra/runtimes/runtimes.yaml /etc/codekr/runtimes.yaml

# 이 컨테이너는 호스트 컨테이너 런타임 소켓으로 형제 컨테이너를 만든다. 소켓 접근 자체가
# 이미 root 권한과 동등하므로 여기서 사용자를 낮춰도 실질적인 이득이 없다.
# 실제 격리는 이 프로세스가 아니라 "생성되는 샌드박스 컨테이너"에 걸린다 (ADR-0003).
#
# `executor --self-test` 로 배포된 노드에서 방어를 검증할 수 있다 (docs/07 §5).
EXPOSE 8081
ENTRYPOINT ["/usr/local/bin/executor"]
