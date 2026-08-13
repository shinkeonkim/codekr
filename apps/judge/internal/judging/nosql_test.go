package judging

import (
	"context"
	"io"
	"log/slog"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

func noSQLJob(commands string, ignoreOrder bool) contract.JudgeJob {
	return contract.JudgeJob{
		SubmissionID:  7,
		Kind:          contract.KindJudgeNoSQL,
		RuntimeID:     defaultNoSQLRuntimeID,
		SourceCode:    commands,
		TimeLimitMs:   10000,
		MemoryLimitMb: 512,
		NoSQL: &contract.JudgeNoSQLSpec{
			Seed:        "ZADD scores 10 kim",
			Answer:      "ZINCRBY scores 5 kim",
			Verify:      "ZRANGE scores 0 -1 WITHSCORES",
			IgnoreOrder: ignoreOrder,
		},
	}
}

func TestNoSqlJudgeAcceptsMatchingState(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("kim\n15", "kim\n15")},
	}}

	newTestService(executor, sink).Judge(context.Background(), noSQLJob("ZINCRBY scores 5 kim", false))

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("정답이어야 합니다: %s", last.Verdict)
	}
}

// **남는 것은 결과가 아니라 상태다.** 명령이 돌았어도 상태가 다르면 오답이다.
func TestNoSqlJudgeRejectsDifferentState(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("kim\n15", "kim\n60")},
	}}

	newTestService(executor, sink).Judge(context.Background(), noSQLJob("ZINCRBY scores 50 kim", false))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("오답이어야 합니다: %s", last.Verdict)
	}
}

/*
순서는 **기본이 자료의 일부**다 (#455).

SQL 의 행 순서와 반대다 — 정렬 집합·리스트에서 순서가 다르면 다른 상태다.
*/
func TestNoSqlJudgeKeepsOrderByDefault(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("a\nb", "b\na")},
	}}

	newTestService(executor, sink).Judge(context.Background(), noSQLJob("LPUSH l a b", false))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("순서가 다르면 오답이어야 합니다: %s", last.Verdict)
	}
}

func TestNoSqlJudgeIgnoresOrderWhenAsked(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("a\nb", "b\na")},
	}}

	newTestService(executor, sink).Judge(context.Background(), noSQLJob("SADD s a b", true))

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("순서를 무시하기로 했으면 정답이어야 합니다: %s", last.Verdict)
	}
}

// 막힌 명령은 오답이 아니라 **무엇이 막혔는지 보여야 한다.**
func TestNoSqlJudgeReportsBlockedCommandAsCompileError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{
			Status:   contract.StatusRuntimeError,
			ExitCode: 1,
			Stdout:   harnessOutput("kim\n15", ""),
			Stderr:   "NOPERM User solver has no permissions to run the 'flushall' command",
		},
	}}

	newTestService(executor, sink).Judge(context.Background(), noSQLJob("FLUSHALL", false))

	if last := sink.last(); last.Verdict != contract.VerdictCompileError {
		t.Fatalf("막힌 명령은 컴파일 오류로 대응한다: %s", last.Verdict)
	}
}

func TestNoSqlJudgeSendsSpecFilesToExecutor(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewNoSqlJudge(captured, log).Judge(context.Background(), noSQLJob("GET k", false), func(contract.Event) {})

	for _, name := range []string{"answer.redis", "verify.redis", "seed.redis"} {
		if captured.job.ExtraFiles[name] == "" {
			t.Fatalf("%s 를 실어야 합니다: %+v", name, captured.job.ExtraFiles)
		}
	}
	if captured.job.RuntimeID != defaultNoSQLRuntimeID {
		t.Fatalf("NoSQL 런타임으로 보내야 합니다: %s", captured.job.RuntimeID)
	}
}

// 스펙 없이 들어온 작업을 짐작해 채점하지 않는다 — 그것은 출제자의 실수다.
func TestNoSqlJudgeRefusesJobWithoutSpec(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK}}}
	job := noSQLJob("GET k", false)
	job.NoSQL = nil

	newTestService(executor, sink).Judge(context.Background(), job)

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}
