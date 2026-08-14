package sandbox

import (
	"context"
	"strings"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

const (
	redisSeed   = "ZADD scores 10 kim\nZADD scores 30 lee\nZADD scores 20 park\n"
	redisAnswer = "ZINCRBY scores 5 kim\n"
	redisVerify = "ZRANGE scores 0 -1 WITHSCORES\n"
)

/*
Redis 문제의 격리 (#455).

**우리는 같은 제품을 큐로도 쓴다** (ADR-0002). 그래서 여기서 확인할 것이 하나 더 있다 —
문제용 인스턴스가 밖으로 나갈 길이 아예 없는가. 하네스가 `--port 0` 으로 TCP 를 열지
않으므로 소켓 파일 말고는 붙을 곳이 없다.

**위험한 명령의 목록도 SQL 과 다르다.** `CONFIG`·`MODULE`·`EVAL`(Lua)·`FLUSHALL`·`KEYS`
가 그 자리에 온다.
*/
func TestLiveRedisSubmissionCannotEscapeItsInstance(t *testing.T) {
	box := newLiveSandbox(t)

	cases := []struct {
		name     string
		commands string
	}{
		{"설정 변경", "CONFIG SET appendonly yes"},
		{"모듈 적재", "MODULE LIST"},
		{"Lua 실행", "EVAL \"return 1\" 0"},
		{"전체 삭제", "FLUSHALL"},
		{"키 전체 훑기", "KEYS *"},
		{"서버 종료", "SHUTDOWN NOSAVE"},
		{"자기 권한 넓히기", "ACL SETUSER solver +@all"},
		{"다른 연결 보기", "CLIENT LIST"},
	}

	for _, testcase := range cases {
		t.Run(testcase.name, func(t *testing.T) {
			outcome := runRedis(t, box, testcase.commands)

			if !strings.Contains(outcome.Stderr, "NOPERM") && !strings.Contains(outcome.Stderr, "ERR ") {
				t.Fatalf("막혀야 할 명령이 막히지 않았습니다.\nstdout=%q\nstderr=%q",
					outcome.Stdout, outcome.Stderr)
			}
			if outcome.ExitCode == 0 {
				t.Fatalf("막힌 명령은 0 이 아닌 코드로 끝나야 합니다: %+v", outcome)
			}
		})
	}
}

// 기대 상태와 제출 상태는 **다른 인스턴스**다 — 번호 DB 로 나누면 ACL 이 그것을 가르지 못한다.
func TestLiveRedisCannotTouchExpectedState(t *testing.T) {
	box := newLiveSandbox(t)

	// 제출이 정답과 같은 일을 한 뒤, 기대 쪽을 흔들어 보려 한다.
	outcome := runRedis(t, box, "ZINCRBY scores 5 kim\nZADD scores 999 lee\n")

	expected, _, found := contract.SplitSQLResults(outcome.Stdout)
	if !found {
		t.Fatalf("기대 상태가 나오지 않았습니다: %q (stderr=%q)", outcome.Stdout, outcome.Stderr)
	}
	if strings.Contains(expected, "999") {
		t.Fatalf("제출이 기대 상태를 건드렸습니다: %q", expected)
	}
}

// 채점기는 무엇이 돌았는지 모른다 — 그러려면 출력이 SQL 판과 같은 모양이어야 한다.
func TestLiveRedisProducesTheSameShapeAsSQL(t *testing.T) {
	box := newLiveSandbox(t)

	outcome := runRedis(t, box, "ZINCRBY scores 5 kim\n")

	expected, actual, found := contract.SplitSQLResults(outcome.Stdout)
	if !found {
		t.Fatalf("기대/실제 상태가 나뉘어 나오지 않았습니다: %q (stderr=%q)", outcome.Stdout, outcome.Stderr)
	}
	// 정렬 집합의 순서는 자료의 일부다 — 순서까지 같아야 한다.
	if contract.NormalizeSQLRows(expected, false) != contract.NormalizeSQLRows(actual, false) {
		t.Fatalf("같은 명령인데 상태가 다릅니다.\n기대=%q\n실제=%q", expected, actual)
	}
	if !strings.Contains(expected, "15") {
		t.Fatalf("시드와 정답 명령이 반영되지 않았습니다: %q", expected)
	}
}

func runRedis(t *testing.T, box Sandbox, commands string) Outcome {
	t.Helper()
	outcome, err := box.Run(context.Background(), Spec{
		Image:      "redis:7-alpine",
		SourceFile: "commands.redis",
		SourceCode: commands,
		Harness:    "redis",
		User:       "999:999",
		Run:        []string{"sh", "run-redis.sh"},
		ExtraFiles: map[string]string{
			"seed.redis":   redisSeed,
			"answer.redis": redisAnswer,
			"verify.redis": redisVerify,
		},
		TimeLimitMs:    20000,
		MemoryLimitMb:  512,
		MaxOutputBytes: 65536,
	})
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	return outcome
}
