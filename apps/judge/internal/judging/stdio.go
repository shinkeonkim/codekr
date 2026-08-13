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

	accumulator := NewAccumulator(total).WithGroups(job.Groups)
	for _, testcase := range job.Testcases {
		/*
			**틀린 묶음의 남은 케이스는 건너뛴다** (#473).

			묶음은 하나만 틀려도 0점이므로 더 돌려도 결과가 바뀌지 않는다. 전부 돌리면
			채점 시간이 몇 배가 되고, 그것은 큐를 쓰는 다른 사람에게도 간다.
			**다른 묶음은 그대로 돈다** — 그것이 "어디까지 왔는지" 를 만든다.
		*/
		if accumulator.GroupFailed(testcase.GroupNo) {
			emit(contract.Event{
				Type:         contract.EventTestcase,
				SubmissionID: job.SubmissionID,
				Seq:          testcase.Seq,
				Verdict:      contract.VerdictWrongAnswer,
			})
			accumulator.AddInGroup(testcase.GroupNo, contract.VerdictWrongAnswer, 0, 0)
			continue
		}
		result := j.runTestcase(ctx, job, testcase)
		verdict := j.verdictFor(ctx, job, testcase, result)
		accumulator.AddInGroup(testcase.GroupNo, verdict, result.RuntimeMs, result.MemoryKb)

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

/*
verdictFor 는 판정을 고른다.

**스페셜 저지(#452)면 견주지 않고 물어본다.** 다만 그 전에 프로그램이 제대로 끝났는지는
그대로 본다 — 죽은 프로그램의 출력을 채점 코드에 넘길 이유가 없고, TLE 를 "틀렸다" 로
바꿔 보여 줄 이유도 없다.
*/
func (j *StdioJudge) verdictFor(
	ctx context.Context,
	job contract.JudgeJob,
	testcase contract.JudgeTestcase,
	result contract.ExecResult,
) contract.Verdict {
	if job.ComparisonOf() != contract.CompareChecker {
		return VerdictOf(result, testcase.ExpectedOutput, job.ComparisonOf(), job.Epsilon)
	}
	if result.Status != contract.StatusOK {
		// 실행 자체가 실패한 것은 채점 코드가 판단할 일이 아니다.
		return VerdictOf(result, testcase.ExpectedOutput, contract.CompareExact, 0)
	}
	return CheckWithCode(ctx, j.executor, j.log, job, testcase, result.Stdout)
}

func (j *StdioJudge) runTestcase(
	ctx context.Context,
	job contract.JudgeJob,
	testcase contract.JudgeTestcase,
) contract.ExecResult {
	result, err := j.executor.Run(ctx, contract.ExecJob{
		RuntimeID:  job.RuntimeID,
		SourceCode: job.SourceCode,
		// 여러 파일로 낸 제출 (#457). 비면 SourceCode 하나로 돈다.
		SourceFiles:   job.SourceFiles,
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
