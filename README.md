# 코드.kr (codekr)

<img src="docs/images/readme-welcome.webp" alt="" width="520">

알고리즘 · 자료구조 · SQL · 네트워크 · 프로그래밍 언어 · 운영체제 · 시스템 설계 등
다양한 코딩 테스트 문제를 제공하는 오픈소스 문제 풀이 플랫폼.

- 도메인: <https://xn--hy1by51c.kr/> (코드.kr)
- 라이선스: MIT

## 무엇을 하는 프로젝트인가

문제를 읽고, 브라우저에서 코드를 작성하고, **실행 과정을 실시간으로 보면서** 채점받는다.
채점은 격리된 샌드박스 컨테이너에서 이루어지며, 테스트케이스 단위 진행 상황이
WebSocket으로 즉시 스트리밍된다.

## 구성

| 앱 | 스택 | 역할 |
|---|---|---|
| `apps/web` | Next.js 15 · TypeScript · Bun | 사용자 · 어드민 화면 |
| `apps/api` | Kotlin · Spring Boot 3 · JPA · Querydsl | 인증, 문제, 제출, 실시간 중계 |
| `apps/judge` | Go | 채점 큐 소비 → 테스트케이스별 실행 큐 발행 → 결과 집계 |
| `apps/executor` | Go | 실행 큐 소비 → OCI 샌드박스에서 코드 실행 |
| PostgreSQL | 16 | 영속 저장소 |
| Redis | 7 | 채점/실행 큐(Streams) · 실시간 이벤트(Pub/Sub) · 캐시 |

자세한 내용은 [`docs/`](docs/)를 참고한다.

- [프로젝트 기획서](docs/00_프로젝트_기획서.md)
- [아키텍처](docs/01_아키텍처.md)
- [도메인 모델](docs/02_도메인_모델.md)
- [API 명세](docs/03_API_명세.md)
- [로드맵 & 이슈 목록](docs/04_로드맵_및_이슈목록.md)
- [로컬 개발 가이드](docs/05_로컬_개발_가이드.md)
- [실행 제약 계약](docs/06_실행_제약_계약.md)
- [샌드박스 위협 모델](docs/07_샌드박스_위협모델.md)
- [활동 집계와 스트릭 정책](docs/08_활동_스트릭_정책.md)
- [배포 가이드 (k8s / GitOps)](docs/09_배포_가이드.md)
- [작업 방식 — 이슈와 Stacked PR](docs/10_작업_방식.md)
- [웹 프론트엔드 구조 (FSD)](docs/11_웹_구조.md)
- [샌드박스 보안 해설](docs/12_샌드박스_보안_해설.md) — 배경 지식 없이 읽는 해설서 (07 의 안내서)
- [배포 중 채점 해설](docs/13_배포_중_채점_해설.md) — 배포해도 채점을 잃지 않는 법 (#415 의 안내서)
- [의사결정 기록(ADR)](docs/adr/)
- [향후 기획서](docs/plans/) — 랭킹·문제 유형 확장·대회

## 빠르게 띄우기

```bash
make up        # 전체 스택 기동 (postgres, redis, api, judge, executor, web)
make seed      # 시드 문제 + 데모 계정 주입
open http://localhost:13000
```

자세한 절차와 포트 표는 [로컬 개발 가이드](docs/05_로컬_개발_가이드.md)에 있다.
