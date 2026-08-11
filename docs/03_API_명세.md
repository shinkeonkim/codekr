# 03. API 명세

베이스 URL: `http://localhost:18080` (로컬). 모든 REST 경로는 `/api/v1` 접두사를 가진다.

## 0. 공통 규약

### 인증

보호된 엔드포인트는 `Authorization: Bearer <accessToken>` 을 요구한다.

### 에러 응답

```json
{
  "code": "PROBLEM_NOT_FOUND",
  "message": "문제를 찾을 수 없습니다.",
  "fieldErrors": [ { "field": "email", "message": "이메일 형식이 아닙니다." } ]
}
```

| HTTP | 상황 |
|---|---|
| 400 | 검증 실패 (`VALIDATION_ERROR`) |
| 401 | 미인증/토큰 만료 (`UNAUTHORIZED`) |
| 403 | 권한 부족 (`FORBIDDEN`) |
| 404 | 리소스 없음 (`*_NOT_FOUND`) |
| 409 | 중복 (`EMAIL_ALREADY_EXISTS`, `SLUG_ALREADY_EXISTS`) |
| 500 | 서버 오류 (`INTERNAL_ERROR`) |

### 페이지 응답

```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
```

---

## 1. 인증 `/api/v1/auth`

### POST `/signup`

```json
// 요청
{ "email": "user@codekr.dev", "password": "password123", "nickname": "코더" }
// 201
{ "accessToken": "...", "refreshToken": "...", "user": { "id": 1, "email": "...", "nickname": "코더", "role": "USER" } }
```

검증: 이메일 형식, 비밀번호 8~64자, 닉네임 2~20자.

### POST `/login`

```json
{ "email": "user@codekr.dev", "password": "password123" }
```

→ 200, signup 과 동일한 응답. 실패 시 401 `INVALID_CREDENTIALS`.

### POST `/refresh`

```json
{ "refreshToken": "..." }
```

→ 200 `{ "accessToken": "...", "refreshToken": "..." }`

### GET `/me` 🔒

→ 200 `{ "id": 1, "email": "...", "nickname": "코더", "role": "USER" }`

---

## 2. 문제 `/api/v1/problems`

### GET `/`

| 쿼리 | 기본값 | 설명 |
|---|---|---|
| `q` | | 제목 부분 일치 |
| `category` | | `ALGORITHM` 등 |
| `tier` | | `BRONZE` \| `SILVER` \| `GOLD` \| `PLATINUM` \| `DIAMOND` \| `RUBY` |
| `sort` | `latest` | `latest` \| `title` \| `difficulty` |
| `page`, `size` | 0, 20 | size 최대 100 |

```json
{
  "content": [
    { "id": 1, "slug": "two-sum", "title": "두 수의 합", "category": "ALGORITHM",
      "difficulty": "BRONZE_5", "difficultyLevel": 1, "tier": "BRONZE", "difficultyLabel": "브론즈 5",
      "timeLimitMs": 2000, "memoryLimitMb": 256 }
  ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1
}
```

### GET `/{slug}`

```json
{
  "id": 1, "slug": "two-sum", "title": "두 수의 합",
  "category": "ALGORITHM",
  "difficulty": "BRONZE_5", "difficultyLevel": 1, "tier": "BRONZE", "difficultyLabel": "브론즈 5",
  "description": "…마크다운…",
  "inputDescription": "…", "outputDescription": "…",
  "timeLimitMs": 2000, "memoryLimitMb": 256,
  "examples": [ { "seq": 1, "input": "1 2\n", "output": "3\n" } ],
  "runtimes": [
    { "id": "python:3.12", "label": "Python 3.12", "monacoLanguage": "python",
      "template": "import sys\n…" }
  ]
}
```

- `examples` 에는 `visibility=PUBLIC` 테스트케이스만 담긴다.
- `runtimes[].template` 은 **문제가 지정한 초기 코드**이며, 지정하지 않았으면 런타임 기본 템플릿이 들어온다.

### POST `/{slug}/run` 🔒

임의 입력으로 1회 실행한다. 채점하지 않는다.

```json
// 요청
{ "runtimeId": "python:3.12", "sourceCode": "print(sum(map(int,input().split())))", "stdin": "1 2\n" }
// 200
{ "status": "OK", "stdout": "3\n", "stderr": "", "runtimeMs": 24, "memoryKb": 8192, "truncated": false }
```

### POST `/{slug}/submissions` 🔒

```json
// 요청
{ "runtimeId": "python:3.12", "sourceCode": "...", "visibility": "PRIVATE" }
// 202
{ "submissionId": 1024, "status": "PENDING" }
```

`visibility` 는 소스 코드 공개 범위이며 생략하면 `PRIVATE` 이다 (아래 3.1).
이후 진행은 WebSocket 으로 받는다.

---

## 3. 제출 `/api/v1/submissions`

### 3.1 소스 코드 공개 범위 (#33)

**메타데이터와 소스 코드를 분리해서 다룬다.** 문제·판정·실행 시간 같은 메타데이터는
로그인한 회원에게 보인다(전체 제출 목록의 전제). 소스 코드만 공개 범위로 가린다.

| 값 | 소스 코드를 볼 수 있는 사람 |
|---|---|
| `PUBLIC` | 모든 회원 |
| `PRIVATE` | 작성자, 관리자 |
| `ACCEPTED_ONLY` | 최종 판정이 `ACCEPTED` 로 확정된 뒤의 모든 회원 |

작성자와 관리자는 공개 범위와 무관하게 항상 볼 수 있다.

**설계 결정**

- **기본값은 `PRIVATE`.** 공개는 사용자가 명시적으로 선택해야 하는 행위다. 한 번 공개된 코드는
  되돌려도 이미 읽힌 뒤일 수 있다.
- **변경은 작성자만, 시점 제한 없음.** 관리자도 남의 공개 범위를 바꿀 수 없다 — 그것은
  작성자의 결정이다. 채점 전에 골라 둔 값을 나중에 바꾸고 싶은 경우가 자연스럽다.
- 응답에서 볼 권한이 없으면 `sourceCode` 를 **빈 문자열이 아니라 아예 내리지 않고**,
  `sourceVisible: false` 로 알린다.

### PATCH `/{id}/visibility` 🔒 (작성자만)

```json
{ "visibility": "PUBLIC" }
```

→ 204. 남의 제출이면 403.

### GET `/{id}` 🔒 (본인 또는 ADMIN)

```json
{
  "id": 1024, "problemSlug": "two-sum", "problemTitle": "두 수의 합",
  "runtimeId": "python:3.12", "status": "COMPLETED", "verdict": "ACCEPTED",
  "passedCount": 10, "totalCount": 10, "maxRuntimeMs": 31, "maxMemoryKb": 9012,
  "compileError": null,
  "visibility": "PRIVATE", "sourceVisible": true, "sourceCode": "...", "nickname": "코더",
  "results": [ { "seq": 1, "verdict": "ACCEPTED", "runtimeMs": 24, "memoryKb": 8192, "stderrExcerpt": null } ],
  "createdAt": "2026-08-10T12:00:00Z"
}
```

### GET `/` 🔒

내 제출 목록. 쿼리: `problemSlug`, `page`, `size`.

### GET `/explore` 🔒 (#34)

전체 회원의 제출 목록. **소스 코드는 담기지 않으며**, 상세에서 볼 수 있는지를
`sourceVisible` 로 미리 알려준다.

| 쿼리 | 설명 |
|---|---|
| `problemSlug` | 문제 |
| `nickname` | 회원 닉네임 부분 일치 |
| `runtimeId` | 실행 환경 |
| `verdict` | 판정 |
| `from`, `to` | 제출일 범위 (`YYYY-MM-DD`, **종료일 당일 포함**) |
| `sort` | `LATEST`(기본) \| `OLDEST` \| `RUNTIME` \| `MEMORY` |
| `page`, `size` | size 최대 50 |

- 날짜 경계는 **Asia/Seoul** 기준이다.
- 정렬은 항상 `id` 를 마지막 키로 둔다 — 같은 값이 여럿일 때 페이지 사이에서 순서가 흔들리면
  중복·누락이 생긴다.
- 정답 코드 검증 제출(#39)은 어떤 조건으로도 조회되지 않는다.

---

## 4. 실시간 — WebSocket `/ws/submissions`

접속 후 구독 메시지를 보낸다.

```json
// client → server
{ "type": "SUBSCRIBE", "submissionId": 1024, "token": "<accessToken>" }
```

서버는 해당 제출의 소유자인지 검증한 뒤 이벤트를 흘려보낸다.

```json
{ "type": "JUDGING",   "submissionId": 1024, "totalCount": 10 }
{ "type": "TESTCASE",  "submissionId": 1024, "seq": 1, "verdict": "ACCEPTED", "runtimeMs": 24, "memoryKb": 8192 }
{ "type": "COMPLETED", "submissionId": 1024, "verdict": "ACCEPTED", "passedCount": 10, "totalCount": 10 }
```

`COMPLETED` 수신 후 서버가 연결을 유지하되, 클라이언트는 구독을 해제해도 된다.
WebSocket 을 사용할 수 없는 환경을 위해 `GET /submissions/{id}` 폴링이 항상 대안으로 존재한다.

---

## 5. 어드민 `/api/v1/admin` 🔒 ROLE_ADMIN

### GET `/problems`

미공개 문제를 포함한 전체 목록. 쿼리는 공개 목록과 동일.

### POST `/problems`

```json
{
  "slug": "two-sum", "title": "두 수의 합",
  "category": "ALGORITHM", "difficulty": "BRONZE_5",
  "description": "…", "inputDescription": "…", "outputDescription": "…",
  "timeLimitMs": 2000, "memoryLimitMb": 256, "published": true,
  "testcases": [
    { "seq": 1, "input": "1 2\n", "expectedOutput": "3\n", "visibility": "PUBLIC" },
    { "seq": 2, "input": "10 20\n", "expectedOutput": "30\n", "visibility": "HIDDEN" }
  ],
  "templates": [
    { "runtimeId": "python:3.12", "sourceCode": "import sys\n\ndef main():\n    pass\n" }
  ],
  "solution": { "runtimeId": "python:3.12", "sourceCode": "print(3)" }
}
```

`difficulty` 는 `BRONZE_5` ~ `RUBY_1` 의 30단계 중 하나다 (docs/02 3장).
`templates` 는 언어별 초기 코드이며, 등록하지 않은 언어는 런타임 기본 템플릿을 쓴다.
`solution` 은 선택 사항이다 — 넣으면 전체 테스트케이스를 검증할 수 있고,
**일반 사용자와 공개 API 는 그 존재조차 조회할 수 없다.**

→ 201 `{ "id": 1, "slug": "two-sum" }`. slug 중복 시 409.

### GET `/problems/{id}`

히든 테스트케이스, 언어별 초기 코드, **정답 코드와 마지막 검증 결과**를 반환한다
(어드민 편집 화면용).

```json
{
  "solution": { "runtimeId": "python:3.12", "sourceCode": "print(3)" },
  "verification": {
    "submissionId": 91, "status": "COMPLETED", "verdict": "ACCEPTED",
    "passedCount": 5, "totalCount": 5, "compileError": null,
    "stale": false,
    "results": [ { "seq": 1, "verdict": "ACCEPTED", "runtimeMs": 12, "memoryKb": 5000 } ]
  }
}
```

`stale` 이 true 면 검증 이후 테스트케이스나 실행 제한이 바뀐 것이다 — 결과를 믿을 수 없다.

### POST `/problems/{id}/verify`

등록된 정답 코드로 **전체 테스트케이스**(공개·히든 모두)를 검증한다.
사용자 제출과 같은 채점 큐를 쓰므로 진행 상황은 문제 상세의 `verification` 으로 확인한다.

→ 202. 정답 코드가 없으면 400 `SOLUTION_REQUIRED`.

### PUT `/problems/{id}`

POST 와 동일한 바디. 테스트케이스와 초기 코드는 **전체 치환**한다
(부분 수정 API 를 따로 두지 않는다 — YAGNI).

### DELETE `/problems/{id}`

→ 204. **소프트 삭제**다 — 행은 남고 `deleted_at` 만 채워진다 (ADR-0007).
따라서 그 문제로 남긴 제출 이력은 그대로 조회되며, 삭제한 slug 는 다시 쓸 수 있다.

### POST `/retention/cleanup` (#46)

보관 기간이 지난 소프트 삭제 행을 실제로 지운다. 자동 실행(매일 04:00 KST)을 기다리지 않고
결과를 확인해야 할 때 쓴다.

```json
{
  "executedAt": "2026-08-10T19:00:00Z",
  "deletedProblems": 2, "deletedTestcases": 41, "deletedTemplates": 7,
  "truncated": false
}
```

`truncated` 가 true 면 한 번에 지울 상한에 걸린 것이다 — 다음 실행에서 이어서 지운다.

### GET `/queues`

```json
{
  "streams": [
    { "name": "codekr:judge", "length": 3, "group": "judge-workers",
      "pending": 1, "consumers": 2, "lastDeliveredId": "1723..." },
    { "name": "codekr:exec", "length": 12, "group": "exec-workers",
      "pending": 4, "consumers": 4, "lastDeliveredId": "1723..." }
  ],
  "workers": [
    { "service": "judge", "healthy": true }, { "service": "executor", "healthy": true }
  ]
}
```

---

## 5.1 활동 `/api/v1/users/me/activity` 🔒 (#36)

일별 활동량과 현재·최장 스트릭. 규칙과 근거는 [08 문서](08_활동_스트릭_정책.md)에 있다.

| 쿼리 | 기본값 | 설명 |
|---|---|---|
| `from`, `to` | 최근 365일 | `YYYY-MM-DD`. 최대 3년 |

```json
{
  "from": "2025-08-11", "to": "2026-08-10",
  "days": [ { "date": "2026-08-10", "count": 3 } ],
  "totalCount": 3, "activeDayCount": 1,
  "currentStreak": 1, "longestStreak": 4,
  "timeZone": "Asia/Seoul"
}
```

## 6. 런타임 목록 `/api/v1/runtimes`

```json
[ { "id": "python:3.12", "label": "Python 3.12", "monacoLanguage": "python",
    "template": "import sys\n\ndef solve():\n    pass\n" } ]
```

여기의 `template` 은 **런타임 기본값**이다. 문제별 초기 코드는 문제 상세 응답에서 내려간다.

---

## 7. 상태 점검

| 서비스 | 경로 |
|---|---|
| api | `GET /actuator/health`, `/actuator/prometheus` |
| judge | `GET :18082/healthz`, `/metrics` |
| executor | `GET :18081/healthz`, `/metrics` |


## 부록 — 엔드포인트 접근 수준

전체 목록은 코드에 있다: `apps/api/src/integrationTest/.../security/ApiEndpointInventory.kt`

| 수준 | 뜻 |
|---|---|
| `PUBLIC` | 토큰 없이 누구나 |
| `AUTHENTICATED` | 로그인한 사용자. **자원 소유권 검사는 서비스가 따로 한다** |
| `ADMIN` | 어드민 역할. **어느 역할인지는 엔드포인트마다 다르다** (#103) |

**엔드포인트를 추가하면 그 목록에도 적어야 한다.** 적지 않으면
`EndpointAuthorizationIntegrationTest` 가 실패한다 (#71). 권한을 정하지 않고 API 를
늘릴 수 없게 만든 장치다.

어드민 API 의 보호는 `SecurityConfig` 의 **경로·역할 규칙 한 곳**에 있다.
그래서 어드민 엔드포인트가 그 경로 밖에 생기지 않는지도 같은 테스트가 확인한다.

### 내 설정 (#104)

`GET`/`PATCH /api/v1/users/me/settings`

- **`PATCH` 는 보낸 항목만 바꾼다.** 전체를 보내게 하면 새 항목이 생겼을 때 옛 화면이
  그것을 지운다
- 제출 요청의 `visibility` 가 없으면 **서버가** 사용자 기본값을 채운다. 화면이 기본값을
  알고 보내는 방식이면 화면마다 어긋난다
- 기본값 변경은 **앞으로의 제출에만** 적용된다. 이미 낸 제출의 공개 범위는 그대로다
- `rankingOptOut` 을 켜면 랭킹 목록에서 빠진다. **점수는 계속 쌓인다** — 껐다 켤 때
  기록이 사라지면 끄기가 되돌릴 수 없는 선택이 된다 (#58)

### 랭킹 (#57, #85, #58)

`GET /api/v1/rankings?metric=&period=&page=&size=` — 공개
`GET /api/v1/rankings/metrics` — 고를 수 있는 지표·기간. 공개

- **지표와 기간 목록을 서버가 알려준다.** 축이 늘어날 때 화면을 같이 고쳐야 하는 구조를
  만들지 않는다
- 실력 점수는 **가장 어려운 100문제만** 합산한다. 전부 더하면 쉬운 문제를 무한히 풀어
  순위를 올릴 수 있고, 그때 이 숫자는 실력이 아니라 누적 시간을 나타낸다
- 동점 처리 규칙이 순서를 끝까지 정하므로 **공동 순위가 생기지 않는다**
- 프로필의 `skillTier` 는 **문제 난이도 티어와 다른 개념이다.** 화면은 "실력 티어"로
  표기해 `solvedByTier`(문제 난이도별 해결 수)와 구분한다

### 대회 (#61)

`GET /api/v1/contests` — 목록. 공개. **준비 중(DRAFT)인 대회는 나오지 않는다**
`GET /api/v1/contests/{slug}` — 상세. 공개하되 **로그인 여부에 따라 내용이 다르다**
`POST /api/v1/contests/{slug}/registrations` — 참가 등록. 두 번 눌러도 오류가 아니다

- **진행 단계(SCHEDULED/RUNNING/ENDED)는 저장하지 않는다.** 시작·종료 시각과 지금을 견줘
  조회 시점에 판정한다. 스케줄러가 상태를 옮기는 방식이면 스케줄러가 1분 늦는 순간
  대회가 1분 늦게 시작한다 — **지연이 곧 사고가 된다**
- 저장하는 `status` 는 **사람이 정하는 것**뿐이다 (DRAFT/PUBLISHED/CANCELED/ARCHIVED)
- 문제는 **시작 전에는 참가자에게도 보이지 않는다.** 진행 중에는 참가자만, 종료 후에는 누구나
- 준비 중인 대회는 존재 여부조차 알리지 않는다 (404)

`POST /api/v1/contests/{contestSlug}/problems/{problemSlug}/submissions` — 대회 제출 (#62)

- **평소 제출과 경로를 나눈다.** 같은 경로에 대회 여부를 실어 보내면, 대회가 아닌 척
  제출해 평소 큐로 보내는 길이 생긴다
- 대회 제출은 `codekr:judge:contest` 로 가고 **전용 워커만** 그것을 읽는다
- 마감 판정은 **요청이 도착한 순간**의 시각으로 한다. 뒤의 검증에 걸리는 시간을 넣지 않는다
- 같은 참가자·같은 문제는 **20초에 한 번**. 문제마다 따로 센다

어드민: `/api/v1/admin/contests/**` — **`CONTEST_MANAGER` 이상.** 자원 범위(어느 대회의
관리자인가)는 아직 없다. 전역 역할만 본다.

- 문제 배정은 **전체 치환**이다. 부분 수정은 순번이 꼬인다
- 배정된 문제가 없으면 공개할 수 없다 — 참가자가 등록한 뒤 시작 시각에 아무것도 못 본다
- **최종 순위 공개(`/unfreeze`)는 자동이 아니다.** 종료 직후 발견된 문제를 바로잡을 틈이 필요하다
- 문제 제외는 배정을 **지우지 않는다.** 그 문제로 낸 제출이 남아 있다

> 널 허용 `AuthPrincipal?` 파라미터는 **비로그인도 허용**한다. 공개 화면인데 로그인 여부에
> 따라 내용이 달라지는 경우에 쓴다 — 대회 상세가 그렇다.

### 역할 (#103)

```
SUPERUSER ─▶ ADMIN ─▶ PROBLEM_SETTER
                   ─▶ CONTEST_MANAGER
                   ─▶ BOARD_MANAGER
```

한 사람이 여러 역할을 가질 수 있다. 위계는 위에서 아래로만 흐른다 —
**문제 출제자는 인프라를 만질 수 없다.**

| 경로 | 필요한 역할 |
|---|---|
| `/api/v1/admin/problems/**` | `PROBLEM_SETTER` |
| `/api/v1/admin/queues/**`, `/executors/**`, `/retention/**` | `ADMIN` |
| **그 밖의 `/api/v1/admin/**`** | `SUPERUSER` |

마지막 줄이 **안전한 기본값**이다. 규칙을 적지 않고 어드민 API 를 늘리면 아무도 못 쓰게
되지, 모두가 쓸 수 있게 되지 않는다.

> 인가를 메서드 보안(`@PreAuthorize`)이 아니라 경로 규칙에 둔 이유: 메서드 보안은
> **본문 바인딩 뒤에** 돌아서, 권한 없는 요청이 400(검증 실패)을 먼저 받는다.
> 막혔다는 사실조차 알 수 없고, 남의 영역의 요청 형식을 탐색할 수 있게 된다.

**자원 범위(어느 대회의 관리자인가)는 아직 없다.** 대회 도메인이 생길 때 함께 만든다 (#61).
