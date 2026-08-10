# ADR-0002. 채점/실행 큐로 Redis Streams 선택

- 상태: 채택
- 날짜: 2026-08-10

## 배경

요구사항이 "코드 실행기와 코드 채점기 사이에는 queue 를 통해 통신한다",
"큐는 실시간 모니터링이 가능해야 한다", "가용성을 위해 여러 인스턴스로 구성될 수 있어야 한다"
로 못박혀 있다. 후보는 Redis Streams, RabbitMQ, Kafka, NATS JetStream 이었다.

## 결정

**Redis Streams + Consumer Group** 을 사용한다.

- `codekr:judge` — api → judge
- `codekr:exec` — judge → executor
- `codekr:exec:res:{jobId}` — executor → judge (작업별 응답 스트림, TTL 300초)
- `codekr:events` — judge → api (Pub/Sub, 실시간 진행 이벤트)

## 근거

- **Redis 는 어차피 필요하다.** 캐시와 세션 용도로 스택에 이미 들어 있다.
  브로커를 추가로 운영하면 홈랩 클러스터에 컴포넌트가 하나 더 늘어난다.
- Consumer Group 이 요구사항 두 개를 한 번에 만족한다 — 인스턴스를 늘리면
  파티션 재설정 없이 수평 확장되고(`XREADGROUP`), `XPENDING`/`XINFO GROUPS` 로
  적체·소비자 상태를 **즉시 조회**할 수 있어 모니터링 API 를 얇게 짤 수 있다.
- 채점 작업량은 초당 수천 건 규모가 아니다. Kafka 의 처리량이 필요 없고,
  RabbitMQ 의 라우팅 유연성도 필요 없다(토폴로지가 고정된 두 홉짜리 파이프라인).

## 대안과 기각 사유

| 대안 | 기각 사유 |
|---|---|
| RabbitMQ | 별도 브로커 운영 비용. 모니터링은 좋지만 Redis 로도 충분 |
| Kafka | 이 규모에 과잉. 운영 부담(ZK/KRaft, 파티션 관리)이 크다 |
| NATS JetStream | 좋은 후보지만 스택에 없는 컴포넌트를 새로 들이는 비용이 이득보다 크다 |
| HTTP 직접 호출 | 요구사항 위반. 실행기 장애 시 작업 유실 |

## 결과 / 감수하는 것

- Redis 는 단일 장애점이 된다. 운영에서는 복제/센티널 또는 관리형 Redis 로 보완한다.
- Streams 는 메시지를 자동으로 지우지 않는다 — `MAXLEN ~` 로 상한을 두고,
  응답 스트림은 소비 후 즉시 `DEL` 한다.
- Pub/Sub 는 전달 보장이 없다. 그래서 **진행 이벤트에만** 쓰고, 최종 판정은
  api 가 이벤트로 받되 유실 시 `GET /submissions/{id}` 폴링으로 복구 가능하게 설계한다.
