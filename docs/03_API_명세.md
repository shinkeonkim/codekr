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
| `difficulty` | | `EASY` \| `MEDIUM` \| `HARD` |
| `sort` | `latest` | `latest` \| `title` \| `difficulty` |
| `page`, `size` | 0, 20 | size 최대 100 |

```json
{
  "content": [
    { "id": 1, "slug": "two-sum", "title": "두 수의 합", "category": "ALGORITHM",
      "difficulty": "EASY", "timeLimitMs": 2000, "memoryLimitMb": 256 }
  ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1
}
```

### GET `/{slug}`

```json
{
  "id": 1, "slug": "two-sum", "title": "두 수의 합",
  "category": "ALGORITHM", "difficulty": "EASY",
  "description": "…마크다운…",
  "inputDescription": "…", "outputDescription": "…",
  "timeLimitMs": 2000, "memoryLimitMb": 256,
  "examples": [ { "seq": 1, "input": "1 2\n", "output": "3\n" } ],
  "runtimes": [ { "id": "python:3.12", "label": "Python 3.12" } ]
}
```

`examples` 에는 `visibility=PUBLIC` 테스트케이스만 담긴다.

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
{ "runtimeId": "python:3.12", "sourceCode": "..." }
// 202
{ "submissionId": 1024, "status": "PENDING" }
```

이후 진행은 WebSocket 으로 받는다.

---

## 3. 제출 `/api/v1/submissions`

### GET `/{id}` 🔒 (본인 또는 ADMIN)

```json
{
  "id": 1024, "problemSlug": "two-sum", "problemTitle": "두 수의 합",
  "runtimeId": "python:3.12", "status": "COMPLETED", "verdict": "ACCEPTED",
  "passedCount": 10, "totalCount": 10, "maxRuntimeMs": 31, "maxMemoryKb": 9012,
  "compileError": null, "sourceCode": "...",
  "results": [ { "seq": 1, "verdict": "ACCEPTED", "runtimeMs": 24, "memoryKb": 8192, "stderrExcerpt": null } ],
  "createdAt": "2026-08-10T12:00:00Z"
}
```

### GET `/` 🔒

내 제출 목록. 쿼리: `problemSlug`, `page`, `size`.

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
  "category": "ALGORITHM", "difficulty": "EASY",
  "description": "…", "inputDescription": "…", "outputDescription": "…",
  "timeLimitMs": 2000, "memoryLimitMb": 256, "published": true,
  "testcases": [
    { "seq": 1, "input": "1 2\n", "expectedOutput": "3\n", "visibility": "PUBLIC" },
    { "seq": 2, "input": "10 20\n", "expectedOutput": "30\n", "visibility": "HIDDEN" }
  ]
}
```

→ 201 `{ "id": 1, "slug": "two-sum" }`. slug 중복 시 409.

### GET `/problems/{id}`

히든 포함 전체 테스트케이스를 반환한다 (어드민 편집 화면용).

### PUT `/problems/{id}`

POST 와 동일한 바디. 테스트케이스는 **전체 치환**한다 (부분 수정 API 를 따로 두지 않는다 — YAGNI).

### DELETE `/problems/{id}`

→ 204. 제출 이력이 있는 문제는 409 `PROBLEM_HAS_SUBMISSIONS`.

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

## 6. 런타임 목록 `/api/v1/runtimes`

```json
[ { "id": "python:3.12", "label": "Python 3.12", "monacoLanguage": "python",
    "template": "import sys\n\ndef solve():\n    pass\n" } ]
```

---

## 7. 상태 점검

| 서비스 | 경로 |
|---|---|
| api | `GET /actuator/health`, `/actuator/prometheus` |
| judge | `GET :18082/healthz`, `/metrics` |
| executor | `GET :18081/healthz`, `/metrics` |
