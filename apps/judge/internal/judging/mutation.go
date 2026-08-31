package judging

import (
	"context"
	"fmt"
	"log/slog"
	"strings"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// 테스트 작성 문제의 실행 환경 (#652). 새 이미지를 만들지 않고 파이썬을 쓴다.
const defaultMutationRuntimeID = "mutation:python"

/*
MutationJudge 는 테스트 작성 문제를 채점한다 (#652).

**채점이 뒤집혀 있다.** 다른 유형은 우리가 시험을 숨기고 사용자가 구현을 내지만,
여기서는 사용자가 시험을 내고 우리가 구현을 숨긴다.

	올바른 구현   → 통과해야 한다 (안 그러면 시험이 틀린 것이다)
	버그 심은 구현 → 실패해야 한다 (그것을 잡는 것이 시험의 일이다)

이 방식이 좋은 이유는 **"시험을 잘 썼다" 를 취향 없이 잴 수 있기 때문**이다.
아무것도 확인하지 않는 시험은 올바른 구현을 통과시키지만 버그를 하나도 못 잡고,
모든 것을 실패시키는 시험은 올바른 구현에서 걸린다.
*/
type MutationJudge struct {
	executor ExecutorClient
	log      *slog.Logger
}

// NewMutationJudge 는 테스트 작성 채점기를 만든다.
func NewMutationJudge(executor ExecutorClient, log *slog.Logger) *MutationJudge {
	return &MutationJudge{executor: executor, log: log}
}

// Judge 는 제출한 시험을 구현들에 돌리고 갈리는 모양을 기대와 비교한다.
func (j *MutationJudge) Judge(ctx context.Context, job contract.JudgeJob, emit Emitter) Outcome {
	emit(contract.Event{Type: contract.EventJudging, SubmissionID: job.SubmissionID, TotalCount: 1})

	if job.Mutation == nil || job.Mutation.Reference == "" {
		// 올바른 구현 없이 이 유형이 큐에 들어왔다. 짐작해 채점하지 않는다.
		j.log.Error("테스트 작성 문제인데 구현이 없습니다", "submissionId", job.SubmissionID)
		return Outcome{Summary: Summary{Verdict: contract.VerdictSystemError, TotalCount: 1}}
	}

	result, err := j.executor.Run(ctx, contract.ExecJob{
		RuntimeID:     mutationRuntimeOf(job),
		SourceCode:    job.SourceCode,
		TimeLimitMs:   job.TimeLimitMs,
		MemoryLimitMb: job.MemoryLimitMb,
		ExtraFiles:    mutationFiles(job.Mutation),
	})
	if err != nil {
		j.log.Error("실행 요청 실패", "submissionId", job.SubmissionID, "error", err)
		result = executorUnreachable()
	}

	verdict, detail := j.verdictOf(result)
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
	if verdict == contract.VerdictCompileError {
		return Outcome{Summary: summary, CompileError: excerpt(detail)}
	}
	return Outcome{Summary: summary}
}

func (j *MutationJudge) verdictOf(result contract.ExecResult) (verdict contract.Verdict, detail string) {
	if result.Status != contract.StatusOK && result.Status != contract.StatusRuntimeError {
		/*
			**시간 초과가 이 유형에서는 흔하다.** 시험 하나가 구현 수만큼 돌기 때문이다 —
			그것은 고장이 아니라 요구다: 사용자의 시험은 N번 돌 만큼 빨라야 한다.
		*/
		return VerdictOf(result, "", contract.CompareExact, 0), result.Stderr
	}

	expected, actual, found := contract.SplitSQLResults(result.Stdout)
	if !found {
		// 하네스가 기대 판정까지 내지 못했다 — 문제의 구현이 잘못됐다. 사용자 잘못이 아니다.
		return contract.VerdictSystemError, result.Stderr
	}
	if result.ExitCode != 0 {
		return contract.VerdictCompileError, result.Stderr
	}
	// **순서를 무시하지 않는다.** 줄 순서가 곧 "어느 구현의 판정인가" 다.
	if strings.TrimSpace(expected) != strings.TrimSpace(actual) {
		return contract.VerdictWrongAnswer, ""
	}
	return contract.VerdictAccepted, ""
}

/*
mutationFiles 는 하네스가 읽을 파일을 만든다 (#652).

**뮤턴트 이름에 번호를 붙인다.** 하네스가 `sort -V` 로 그 순서대로 돌리므로
기대값의 줄 순서와 맞는다.
*/
func mutationFiles(spec *contract.JudgeMutationSpec) map[string]string {
	files := map[string]string{"reference.py": spec.Reference}
	for index, mutant := range spec.Mutants {
		files[fmt.Sprintf("mutant_%d.py", index+1)] = mutant
	}
	return files
}

func mutationRuntimeOf(job contract.JudgeJob) string {
	if job.RuntimeID == "" {
		return defaultMutationRuntimeID
	}
	return job.RuntimeID
}
