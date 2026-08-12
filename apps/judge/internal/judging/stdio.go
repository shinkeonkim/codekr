package judging

import (
	"context"
	"log/slog"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// StdioJudge 는 stdin/stdout 채점이다 (ADR-0006).
//
// 소스 코드를 언어 런타임에서 실행하고, 테스트케이스마다 stdout 을 기대 출력과 비교한다.
// 유형이 늘어나도 이 파일은 건드리지 않는다 — 새 유형은 새 Kind 구현을 추가한다.
type StdioJudge struct {
	executor ExecutorClient
	log      *slog.Logger
}

// NewStdioJudge 는 stdin/stdout 채점기를 만든다.
func NewStdioJudge(executor ExecutorClient, log *slog.Logger) *StdioJudge {
	return &StdioJudge{executor: executor, log: log}
}

// Judge 는 테스트케이스를 순서대로 실행하고 진행 상황을 emit 으로 알린다.
//
// 첫 실패에서 멈추지 않고 끝까지 채점한다 — 학습자에게는 "몇 개를 통과했는가"가
// 중요한 정보이기 때문이다. 다만 컴파일 실패는 이후 케이스가 전부 같은 결과이므로
// 즉시 종료한다.
func (j *StdioJudge) Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome {
	total := len(job.Testcases)
	emit(contract.Event{Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: total})

	accumulator := NewAccumulator(total)
	for _, testcase := range job.Testcases {
		result := j.runTestcase(ctx, job, testcase)
		verdict := VerdictOf(result, testcase.ExpectedOutput, job.ComparisonOf(), job.Epsilon)
		accumulator.Add(verdict, result.RuntimeMs, result.MemoryKb)

		emit(contract.Event{
			Type:          contract.EventTestcase,
			SubmissionID:  job.SubmissionID,
			Seq:           testcase.Seq,
			Verdict:       verdict,
			RuntimeMs:     result.RuntimeMs,
			MemoryKb:      result.MemoryKb,
			StderrExcerpt: excerpt(result.Stderr),
		})

		if verdict == contract.VerdictCompileError {
			return Outcome{Summary: accumulator.Summarize(), CompileError: excerpt(result.Stderr)}
		}
	}
	return Outcome{Summary: accumulator.Summarize()}
}

func (j *StdioJudge) runTestcase(
	ctx context.Context,
	job contract.JudgeJob,
	testcase contract.JudgeTestcase,
) contract.ExecResult {
	result, err := j.executor.Run(ctx, contract.ExecJob{
		RuntimeID:     job.RuntimeID,
		SourceCode:    job.SourceCode,
		Stdin:         testcase.Input,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
	})
	if err != nil {
		// 실행기가 응답하지 않아도 제출을 미결 상태로 두지 않는다.
		j.log.Error("실행 요청 실패",
			"submissionId", job.SubmissionID, "seq", testcase.Seq, "error", err)
		return contract.ExecResult{Status: contract.StatusSystemError, Stderr: err.Error()}
	}
	return result
}
