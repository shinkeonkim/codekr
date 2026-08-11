package judging

import (
	"context"
	"log/slog"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// SQL 실행 환경. 문제마다 새 PostgreSQL 을 샌드박스 안에서 띄운다 (#60).
const sqlRuntimeID = "sql:postgres16"

// SqlJudge 는 SQL 문제를 채점한다 (#60).
//
// **정답 쿼리와 제출 쿼리를 같은 데이터베이스에서 돌려 결과를 비교한다.** 정답을
// 결과 집합으로 저장하지 않는 이유는, 시드 데이터가 바뀌면 기대 결과도 바뀌기 때문이다.
type SqlJudge struct {
	executor ExecutorClient
	log      *slog.Logger
}

// NewSqlJudge 는 SQL 채점기를 만든다.
func NewSqlJudge(executor ExecutorClient, log *slog.Logger) *SqlJudge {
	return &SqlJudge{executor: executor, log: log}
}

// Judge 는 제출 쿼리를 한 번 실행하고 정답 결과와 비교한다.
//
// 채점 단위가 하나뿐이라 테스트케이스를 돌지 않는다 — 진행률의 분모도 1 이다.
func (j *SqlJudge) Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome {
	emit(contract.Event{Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: 1})

	if job.SQL == nil {
		// 스펙 없이 SQL 문제가 큐에 들어왔다. 짐작해 채점하지 않는다.
		j.log.Error("SQL 문제인데 스펙이 없습니다", "submissionId", job.SubmissionID)
		return Outcome{Summary: Summary{Verdict: contract.VerdictSystemError, TotalCount: 1}}
	}

	result, err := j.executor.Run(ctx, contract.ExecJob{
		RuntimeID:     sqlRuntimeID,
		SourceCode:    job.SourceCode,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
		ExtraFiles: map[string]string{
			"schema.sql": job.SQL.Schema,
			"answer.sql": job.SQL.Answer,
		},
	})
	if err != nil {
		j.log.Error("실행 요청 실패", "submissionId", job.SubmissionID, "error", err)
		result = contract.ExecResult{Status: contract.StatusSystemError, Stderr: err.Error()}
	}

	verdict, detail := j.verdictOf(result, job.SQL.IgnoreRowOrder)
	emit(contract.Event{
		Type:          contract.EventTestcase,
		SubmissionID:  job.SubmissionID,
		Seq:           1,
		Verdict:       verdict,
		RuntimeMs:     result.RuntimeMs,
		MemoryKb:      result.MemoryKb,
		StderrExcerpt: excerpt(detail),
	})

	summary := Summary{
		Verdict:      verdict,
		TotalCount:   1,
		MaxRuntimeMs: result.RuntimeMs,
		MaxMemoryKb:  result.MemoryKb,
	}
	if verdict == contract.VerdictAccepted {
		summary.PassedCount = 1
	}
	// 쿼리 자체가 잘못된 것은 컴파일 오류로 대응한다 — 새 판정 값을 늘리기 전에
	// 기존 값으로 표현되는지 먼저 본다 (기획서 §3).
	if verdict == contract.VerdictCompileError {
		return Outcome{Summary: summary, CompileError: excerpt(detail)}
	}
	return Outcome{Summary: summary}
}

func (j *SqlJudge) verdictOf(
	result contract.ExecResult,
	ignoreRowOrder bool,
) (verdict contract.Verdict, detail string) {
	// 실행 자체가 실패한 경우는 stdin/stdout 과 같게 읽는다 — 시간·메모리·인프라 실패의
	// 뜻은 유형과 무관하다.
	if result.Status != contract.StatusOK && result.Status != contract.StatusRuntimeError {
		return VerdictOf(result, ""), result.Stderr
	}

	expected, actual, found := contract.SplitSQLResults(result.Stdout)
	if !found {
		// 하네스가 정답 결과까지 내지 못했다 — 스키마나 정답 쿼리가 잘못됐다.
		// 사용자 잘못이 아니므로 오답으로 처리하지 않는다.
		return contract.VerdictSystemError, result.Stderr
	}
	if result.ExitCode != 0 {
		// 제출 쿼리가 문법 오류이거나 권한에 막혔다. 어느 쪽이든 사용자에게 보일 것은 stderr 다.
		return contract.VerdictCompileError, result.Stderr
	}
	if contract.NormalizeSQLRows(expected, ignoreRowOrder) != contract.NormalizeSQLRows(actual, ignoreRowOrder) {
		return contract.VerdictWrongAnswer, ""
	}
	return contract.VerdictAccepted, ""
}
