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

// Redis 는 NoSQL(Redis) 문제의 하네스다 (#455).
//
// **채점 모델이 SQL 과 다르다** — 제출이 명령의 연속이라 남는 것은 상태다. 그래도
// 출력 형식은 같게 낸다: 채점기가 무엇이 돌았는지 몰라도 되게 하기 위함이다.
const Redis = "redis"

// Interactive 는 인터랙티브 문제의 하네스다 (#474).
//
// **도는 중에 주고받는다** — 스페셜 저지(#452)가 끝난 뒤 판정하는 것과 다르다.
// 파이프 둘을 파서 채점 코드와 제출을 서로 물린다.
const Interactive = "interactive"

// scripts 는 하네스 이름 → 작업 디렉터리에 쓸 파일 이름이다.
var scripts = map[string]string{
	SQL:         "run-sql.sh",
	MariaDB:     "run-mariadb.sh",
	Redis:       "run-redis.sh",
	Interactive: "run-interactive.sh",
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
