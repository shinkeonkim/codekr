package judging

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
테스트 작성 채점 (#652).

**채점이 뒤집혀 있어서 "틀린 답" 의 모양이 둘이다** — 아무것도 못 잡는 시험과
전부를 실패시키는 시험. 둘 다 넣는다.
*/

func mutationJob(testSource string) contract.JudgeJob {
	return contract.JudgeJob{
		SubmissionID:  41,
		Kind:          contract.KindJudgeMutation,
		RuntimeID:     defaultMutationRuntimeID,
		SourceCode:    testSource,
		TimeLimitMs:   20000,
		MemoryLimitMb: 512,
		Mutation: &contract.JudgeMutationSpec{
			Reference: "def average(xs):\n    return sum(xs) / len(xs)\n",
			Mutants: []string{
				"def average(xs):\n    return sum(xs) / len(xs) - 1\n",
				"def average(xs):\n    return sum(xs)\n",
			},
		},
	}
}

func TestMutationJudgeAcceptsTestThatCatchesEveryMutant(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("PASS\nFAIL\nFAIL", "PASS\nFAIL\nFAIL")},
	}}

	newTestService(executor, sink).Judge(context.Background(), mutationJob("좋은 시험"))

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("정답이어야 합니다: %s", last.Verdict)
	}
}

/*
**아무것도 확인하지 않는 시험**은 올바른 구현을 통과시키지만 버그를 하나도 못 잡는다.

`assert(True)` 가 통과하면 이 유형은 아무것도 묻지 않는 것이 된다.
*/
func TestMutationJudgeRejectsTestThatCatchesNothing(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("PASS\nFAIL\nFAIL", "PASS\nPASS\nPASS")},
	}}

	newTestService(executor, sink).Judge(context.Background(), mutationJob("빈 시험"))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("아무것도 못 잡는 시험은 오답이어야 합니다: %s", last.Verdict)
	}
}

/** 모든 것을 실패시키는 시험은 **올바른 구현에서 걸린다.** */
func TestMutationJudgeRejectsTestThatFailsEverything(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("PASS\nFAIL\nFAIL", "FAIL\nFAIL\nFAIL")},
	}}

	newTestService(executor, sink).Judge(context.Background(), mutationJob("항상 실패하는 시험"))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("올바른 구현을 떨어뜨리면 오답이어야 합니다: %s", last.Verdict)
	}
}

// 하나만 놓쳐도 오답이다 — 99개를 잡은 것이 100개를 잡은 것과 같지 않다.
func TestMutationJudgeRejectsTestThatMissesOneMutant(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("PASS\nFAIL\nFAIL", "PASS\nFAIL\nPASS")},
	}}

	newTestService(executor, sink).Judge(context.Background(), mutationJob("절반만 잡는 시험"))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("하나라도 놓치면 오답이어야 합니다: %s", last.Verdict)
	}
}

/*
**시간 초과가 이 유형에서는 흔하다.**

시험 하나가 구현 수만큼 돌기 때문이다 — 그것은 고장이 아니라 요구다.
*/
func TestMutationJudgeMapsTimeLimit(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusTimeLimitExceeded}}}

	newTestService(executor, sink).Judge(context.Background(), mutationJob("느린 시험"))

	if last := sink.last(); last.Verdict != contract.VerdictTimeLimitExceeded {
		t.Fatalf("시간 초과여야 합니다: %s", last.Verdict)
	}
}

func TestMutationJudgeTreatsMissingExpectedAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "구분선 없는 출력", Stderr: "구현이 없습니다"},
	}}

	newTestService(executor, sink).Judge(context.Background(), mutationJob("시험"))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

func TestMutationJudgeTreatsExecutorFailureAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{err: errors.New("실행기가 응답하지 않습니다")}

	newTestService(executor, sink).Judge(context.Background(), mutationJob("시험"))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

/** 뮤턴트 이름에 번호가 붙어야 한다 — 하네스가 그 순서로 돌린다. */
func TestMutationJudgeNumbersMutantsInOrder(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("PASS", "PASS"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := mutationJob("시험")

	NewMutationJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if got := captured.job.ExtraFiles["reference.py"]; got != job.Mutation.Reference {
		t.Fatalf("올바른 구현을 실어야 합니다: %q", got)
	}
	if got := captured.job.ExtraFiles["mutant_1.py"]; got != job.Mutation.Mutants[0] {
		t.Fatalf("첫 뮤턴트가 어긋납니다: %q", got)
	}
	if got := captured.job.ExtraFiles["mutant_2.py"]; got != job.Mutation.Mutants[1] {
		t.Fatalf("둘째 뮤턴트가 어긋납니다: %q", got)
	}
	// **제출은 시험 코드다** — 구현이 아니다.
	if captured.job.SourceCode != "시험" {
		t.Fatalf("제출을 그대로 보내야 합니다: %q", captured.job.SourceCode)
	}
}

// 뮤턴트가 없는 문제는 **아무것도 묻지 않는다** — 등록에서 막지만 여기서도 안 죽는다.
func TestMutationJudgeHandlesNoMutants(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("PASS", "PASS"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := mutationJob("시험")
	job.Mutation.Mutants = nil

	NewMutationJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if _, found := captured.job.ExtraFiles["mutant_1.py"]; found {
		t.Fatalf("뮤턴트가 없으면 파일도 없어야 합니다: %+v", captured.job.ExtraFiles)
	}
}

func TestMutationJudgeRefusesJobWithoutSpec(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK}}}
	job := mutationJob("시험")
	job.Mutation = nil

	newTestService(executor, sink).Judge(context.Background(), job)

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
	if executor.calls != 0 {
		t.Fatalf("실행기를 부르지 않아야 합니다: %d회", executor.calls)
	}
}
