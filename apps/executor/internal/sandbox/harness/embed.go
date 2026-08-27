// Package harness 는 특정 런타임이 필요로 하는 실행 스크립트를 담는다.
//
// 이미지에 함께 넣지 않고 실행기 바이너리에 넣는 이유는, 스크립트와 코드의 버전이
// 갈라지지 않게 하기 위함이다 — 이미지를 다시 만들지 않아도 스크립트가 따라온다.
package harness

import (
	"embed"
	"fmt"
)

//go:embed *.sh
var files embed.FS

// SQL 은 런타임 정의가 harness 로 고르는 이름이다 (PostgreSQL).
const SQL = "sql"

// MariaDB 는 MariaDB 문제의 하네스다 (#454).
//
// **DB 마다 스크립트가 하나씩 필요하다.** 초기화 방식·클라이언트·권한 모델·출력 형식이
// 전부 다르기 때문이다. 바뀌지 않는 것은 주고받는 파일 이름과 출력 형식뿐이고,
// 그래서 채점기는 어느 DB 였는지 모른 채 결과를 견줄 수 있다.
const MariaDB = "mariadb"

// Redis 는 Redis 문제의 하네스다 (#455).
//
// **채점 모델이 SQL 과 다르다** — 제출이 명령의 연속이라 남는 것은 상태다. 그래도
// 출력 형식은 같게 낸다: 채점기가 무엇이 돌았는지 몰라도 되게 하기 위함이다.
const Redis = "redis"

// Mongo 는 MongoDB 문제의 하네스다 (#527).
//
// **채점 모델은 Redis 와 같고 언어가 다르다.** `mongosh` 는 자바스크립트를 먹고
// redis-cli 는 명령 줄을 먹으므로 한 파일에 담을 수 없다 — 그래서 하네스가 따로다.
const Mongo = "mongo"

// Interactive 는 인터랙티브 문제의 하네스다 (#474).
//
// **도는 중에 주고받는다** — 스페셜 저지(#452)가 끝난 뒤 판정하는 것과 다르다.
// 파이프 둘을 파서 채점 코드와 제출을 서로 물린다.
const Interactive = "interactive"

/*
Regex 는 정규식 문제의 하네스다 (#653).

**하는 일이 다른 하네스와 다르다.** SQL·Redis 는 제출을 *실행*하지만 여기서는
제출을 **자료로 읽어** 엔진에 넘긴다 — 패턴은 코드가 아니고, 실행했다가는 제출이
곧 임의 코드 실행이 된다.

새 이미지를 만들지 않는다 — 이미 등록된 파이썬 이미지를 쓴다 (#588 이 겪은
"레지스트리에 없는 이미지" 를 늘리지 않는다).
*/
const Regex = "regex"

/*
Git 은 Git 문제의 하네스다 (#654).

**Redis 와 채점 모델이 같지만 하네스가 하는 일이 더 많다** — 커밋 해시가 재현되도록
신원과 시각을 고정하고, 네트워크 명령이 **타임아웃이 아니라 즉시** 거부되게 만든다.

새 이미지를 만들지 않는다 — C/C++ 이 쓰는 `gcc:13` 에 git 이 들어 있다.
*/
const Git = "git"

/*
Mutation 은 테스트 작성 문제의 하네스다 (#652).

**구현마다 컨테이너를 띄우지 않는다.** 여기서 반복을 돌아 실행이 한 번이다 —
그러지 않으면 실행기 부하가 구현 수만큼 늘고, 이 유형이 다른 것보다 비싼 이유가
그것이었다.
*/
const Mutation = "mutation"

// scripts 는 하네스 이름 → 작업 디렉터리에 쓸 파일 이름이다.
var scripts = map[string]string{
	SQL:         "run-sql.sh",
	MariaDB:     "run-mariadb.sh",
	Redis:       "run-redis.sh",
	Mongo:       "run-mongo.sh",
	Interactive: "run-interactive.sh",
	Regex:       "run-regex.sh",
	Git:         "run-git.sh",
	Mutation:    "run-mutation.sh",
}

// Files 는 그 하네스가 작업 디렉터리에 풀어야 할 파일들이다.
// 이름이 비어 있으면 아무것도 돌려주지 않는다 — 대부분의 런타임은 하네스가 필요 없다.
func Files(name string) (map[string]string, error) {
	if name == "" {
		return nil, nil
	}
	filename, known := scripts[name]
	if !known {
		return nil, fmt.Errorf("알 수 없는 하네스 %q", name)
	}
	body, err := files.ReadFile(filename)
	if err != nil {
		return nil, fmt.Errorf("하네스 %q 를 읽지 못했습니다: %w", name, err)
	}
	return map[string]string{filename: string(body)}, nil
}
