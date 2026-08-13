package judging

import (
	"context"
	"io"
	"log/slog"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

func sqlJob(query string, ignoreRowOrder bool) contract.JudgeJob {
	return contract.JudgeJob{
		SubmissionID:  1,
		Kind:          contract.KindJudgeSQL,
		RuntimeID:     defaultSQLRuntimeID,
		SourceCode:    query,
		TimeLimitMs:   10000,
		MemoryLimitMb: 512,
		SQL: &contract.JudgeSQLSpec{
			Schema:         "CREATE TABLE t(x int);",
			Answer:         "SELECT x FROM t;",
			IgnoreRowOrder: ignoreRowOrder,
		},
	}
}

func harnessOutput(expected, actual string) string {
	return contract.SQLExpectedMarker + "\n" + expected + "\n" + contract.SQLActualMarker + "\n" + actual + "\n"
}

func TestSqlJudgeAcceptsMatchingResult(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("1\n2", "1\n2")},
	}}

	newTestService(executor, sink).Judge(context.Background(), sqlJob("SELECT x FROM t;", false))

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("정답이어야 합니다: %s", last.Verdict)
	}
}

func TestSqlJudgeRejectsDifferentResult(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("1\n2", "1\n3")},
	}}

	newTestService(executor, sink).Judge(context.Background(), sqlJob("SELECT 3;", false))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("오답이어야 합니다: %s", last.Verdict)
	}
}

// 문제가 정렬을 요구하지 않는데 순서를 비교하면 맞는 답이 틀린 것으로 나온다.
func TestSqlJudgeIgnoresRowOrderWhenAsked(t *testing.T) {
	stdout := harnessOutput("1\n2", "2\n1")

	sink := &recordingSink{}
	newTestService(&stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: stdout}}}, sink).
		Judge(context.Background(), sqlJob("SELECT x FROM t;", true))
	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("순서를 무시하면 정답이어야 합니다: %s", last.Verdict)
	}

	// 정렬이 문제의 일부면 순서가 다른 답은 오답이다.
	strict := &recordingSink{}
	newTestService(&stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: stdout}}}, strict).
		Judge(context.Background(), sqlJob("SELECT x FROM t;", false))
	if last := strict.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("순서를 보면 오답이어야 합니다: %s", last.Verdict)
	}
}

// 쿼리가 문법 오류이거나 권한에 막히면 psql 이 0 이 아닌 코드로 끝난다.
func TestSqlJudgeReportsQueryErrorAsCompileError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{
		Status:   contract.StatusOK,
		ExitCode: 3,
		Stdout:   harnessOutput("1\n2", ""),
		Stderr:   `ERROR:  cannot execute DROP TABLE in a read-only transaction`,
	}}}

	newTestService(executor, sink).Judge(context.Background(), sqlJob("DROP TABLE t;", true))

	last := sink.last()
	if last.Verdict != contract.VerdictCompileError {
		t.Fatalf("쿼리 오류는 컴파일 오류로 대응합니다: %s", last.Verdict)
	}
	// 왜 막혔는지가 사용자에게 가야 한다.
	if last.CompileError == "" {
		t.Fatal("사유가 비어 있으면 사용자는 무엇이 잘못됐는지 알 수 없습니다")
	}
}

// 정답 쿼리나 스키마가 잘못된 것은 **사용자 잘못이 아니다.** 오답으로 처리하면
// 출제자의 실수가 사용자 기록에 남는다.
func TestSqlJudgeTreatsBrokenSpecAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "psql: 스키마가 깨졌습니다"},
	}}

	newTestService(executor, sink).Judge(context.Background(), sqlJob("SELECT 1;", true))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

func TestSqlJudgeRefusesJobWithoutSpec(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK}}}

	job := sqlJob("SELECT 1;", true)
	job.SQL = nil
	newTestService(executor, sink).Judge(context.Background(), job)

	if executor.calls != 0 {
		t.Fatalf("스펙 없이 실행하지 않아야 합니다: calls=%d", executor.calls)
	}
	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

// 실행기에 넘기는 자료가 하네스가 기대하는 이름이어야 한다.
func TestSqlJudgeSendsSchemaAndAnswerToExecutor(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewSqlJudge(captured, log).Judge(context.Background(), sqlJob("SELECT 1;", true), func(contract.Event) {})

	if captured.job.ExtraFiles["schema.sql"] == "" || captured.job.ExtraFiles["answer.sql"] == "" {
		t.Fatalf("스키마와 정답 쿼리를 실어야 합니다: %+v", captured.job.ExtraFiles)
	}
	if captured.job.RuntimeID != defaultSQLRuntimeID {
		t.Fatalf("SQL 런타임으로 보내야 합니다: %s", captured.job.RuntimeID)
	}
}

type capturingExecutor struct {
	job    contract.ExecJob
	result contract.ExecResult
}

func (c *capturingExecutor) Run(_ context.Context, job contract.ExecJob) (contract.ExecResult, error) {
	c.job = job
	return c.result, nil
}

/*
상태를 묻는 문제 (#453).

**신호는 파일이다.** 검사 쿼리가 실리지 않으면 하네스는 데이터베이스를 하나만 만들고,
`UPDATE` 는 결과 집합이 비어 있으니 **아무 답이나 통과한다.**
*/
func TestSqlJudgeSendsVerifyAndWriteFlag(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	job := sqlJob("UPDATE t SET x = 1;", true)
	job.SQL.Verify = "SELECT x FROM t ORDER BY x;"
	job.SQL.AllowWrite = true
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewSqlJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if captured.job.ExtraFiles["verify.sql"] != "SELECT x FROM t ORDER BY x;" {
		t.Fatalf("검사 쿼리를 실어야 합니다: %+v", captured.job.ExtraFiles)
	}
	if _, ok := captured.job.ExtraFiles["allow-write"]; !ok {
		t.Fatalf("쓰기를 여는 신호가 없습니다: %+v", captured.job.ExtraFiles)
	}
}

// 지금 있는 SELECT 문제가 조용히 쓰기 가능해지면 안 된다.
func TestSqlJudgeKeepsReadOnlyByDefault(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewSqlJudge(captured, log).Judge(context.Background(), sqlJob("SELECT x FROM t;", true), func(contract.Event) {})

	for _, name := range []string{"verify.sql", "allow-write"} {
		if _, ok := captured.job.ExtraFiles[name]; ok {
			t.Fatalf("%s 가 실리면 안 됩니다: %+v", name, captured.job.ExtraFiles)
		}
	}
}

/*
어느 DB 로 풀지는 제출이 고른다 (#454).

전에는 채점기가 `sql:postgres16` 을 박아 보냈다. 그러면 MariaDB 로 낸 제출도 PostgreSQL
에서 돌아 **문법이 맞는데 틀린 답**이 된다.
*/
func TestSqlJudgeSendsSubmittedDatabase(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	job := sqlJob("SELECT 1;", true)
	job.RuntimeID = "sql:mariadb11"
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewSqlJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if captured.job.RuntimeID != "sql:mariadb11" {
		t.Fatalf("제출이 고른 DB 로 보내야 합니다: %s", captured.job.RuntimeID)
	}
}

// 이 값이 없던 시절의 작업은 PostgreSQL 이다 — 그때는 그것 하나뿐이었다.
func TestSqlJudgeFallsBackToPostgresForOldJobs(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("1", "1"),
	}}
	job := sqlJob("SELECT 1;", true)
	job.RuntimeID = ""
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewSqlJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if captured.job.RuntimeID != defaultSQLRuntimeID {
		t.Fatalf("옛 작업은 PostgreSQL 로 돌아야 합니다: %s", captured.job.RuntimeID)
	}
}
