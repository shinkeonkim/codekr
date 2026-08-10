# ADR-0005. Spring 스택 구성 요소의 선택 범위

- 상태: 채택
- 날짜: 2026-08-10

## 배경

요구사항이 Spring 관련 기술을 넓게 나열한다 — Security, Data JPA, Querydsl, Actuator,
Admin, DevTools, Test, Testcontainers, WebFlux, WebSocket. 이 중 일부는 함께 쓸 때
서로 충돌하거나, MVP 에서 비용만 늘린다. 무엇을 어떤 형태로 쓸지 명시한다.

## 결정

| 기술 | 채택 여부 | 형태 |
|---|---|---|
| Spring Security | ✅ | JWT 인증 필터, `ROLE_USER`/`ROLE_ADMIN`, BCrypt |
| Spring Data JPA | ✅ | 엔티티 영속화, 기본 CRUD |
| Querydsl | ✅ | 문제 목록의 동적 검색(키워드·카테고리·난이도·정렬) |
| Actuator | ✅ | `/actuator/health`, `/metrics`, `/prometheus` |
| Spring WebSocket | ✅ | `/ws/submissions` 실시간 채점 중계 |
| Spring Boot Test | ✅ | 단위 + `@SpringBootTest` 슬라이스 테스트 |
| Testcontainers | ✅ | PostgreSQL/Redis 통합 테스트. **CI 에서 별도 잡으로 분리** |
| DevTools | ✅ | `developmentOnly` 스코프. 프로덕션 이미지에 포함하지 않는다 |
| **WebFlux** | ⚠️ 부분 | **웹 스택으로는 쓰지 않는다.** `WebClient`(리액티브 HTTP 클라이언트)만 사용 |
| **Spring Boot Admin** | ⏸ 보류 | M2 로 미룬다 |

## 근거

### WebFlux 를 웹 스택으로 쓰지 않는 이유

WebFlux(Netty)와 Spring MVC(서블릿)를 한 애플리케이션에 함께 두면 Spring Boot 는
서블릿 스택을 선택하고 WebFlux 는 무력화된다. 게다가 JPA 는 블로킹 API 다 —
리액티브 파이프라인 안에서 블로킹 JPA 를 호출하면 이벤트 루프를 막아
논블로킹의 이점이 사라지고 오히려 더 나빠진다.

이 서비스의 부하 특성은 **동시 연결이 많은 I/O 프록시**가 아니라
**DB 를 읽고 쓰는 평범한 CRUD + 큐 발행**이다. 실제 장시간 대기는 채점이며,
그것은 이미 큐로 비동기화되어 API 스레드를 점유하지 않는다.

따라서 웹 계층은 Spring MVC(가상 스레드 활성화)로 두고, WebFlux 는
`WebClient` 형태로만 쓴다. 실시간 요구는 WebSocket 이 담당한다.
장래에 리액티브가 필요해지면 그때는 서비스를 분리하는 편이 옳다.

### Spring Boot Admin 을 미루는 이유

Admin 서버는 **여러 인스턴스를 모니터링하는 UI** 다. MVP 는 api 인스턴스 하나이고,
Actuator + Prometheus 로 필요한 관측이 이미 충족된다. 인스턴스가 늘어나는 M2 에서
홈랩의 기존 관측 스택과 함께 도입한다 (이슈 #16).

## 결과

- `apps/api` 는 서블릿 스택이다. 컨트롤러는 블로킹으로 작성한다.
- Java 21 가상 스레드(`spring.threads.virtual.enabled=true`)로 블로킹 I/O 의
  스레드 비용을 낮춘다.
- Testcontainers 테스트는 `./gradlew integrationTest` 로 분리되어 PR CI 에서 제외된다.
