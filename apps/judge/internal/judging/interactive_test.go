package judging

import (
	"context"
	"io"
	"log/slog"
	"strings"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
인터랙티브 문제 (#474).

**도는 중에 주고받는다.** 판정은 채점 코드의 종료 코드가 말하고, 거기에 스페셜
저지(#452)에는 없던 값이 하나 더 있다 — **교착**.
*/
func interactiveJob() contract.JudgeJob {
	return contract.JudgeJob{
		SubmissionID:  5,
		Kind:          contract.KindJudgeInteractive,
		RuntimeID:     defaultInteractiveRuntimeID,
		SourceCode:    "print(1, flush=True)",
		Interactor:    "import sys; sys.exit(0)",
		TimeLimitMs:   5000,
		MemoryLimitMb: 256,
		Testcases:     []contract.JudgeTestcase{{Seq: 1, Input: "42\n"}},
	}
}

func TestInteractiveAcceptsWhenInteractorExitsZero(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, ExitCode: 0}}}

	newTestService(executor, sink).Judge(context.Background(), interactiveJob())

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("정답이어야 합니다: %s", last.Verdict)
	}
}

func TestInteractiveRejectsWhenInteractorSaysWrong(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusRuntimeError, ExitCode: 1, Stderr: "질의 횟수를 넘겼습니다"},
	}}

	newTestService(executor, sink).Judge(context.Background(), interactiveJob())

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("오답이어야 합니다: %s", last.Verdict)
	}
}

/*
**교착과 시간 초과를 가른다** (#474).

가장 흔한 원인은 출력을 flush 하지 않은 것이고, 사용자 잘못이지만 사용자가 알기 어렵다.
"시간 초과" 로 보이면 엉뚱한 곳(알고리즘)을 고치게 된다.
*/
func TestInteractiveTellsAboutFlushOnDeadlock(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusRuntimeError, ExitCode: 3},
	}}

	newTestService(executor, sink).Judge(context.Background(), interactiveJob())

	events := sink.events
	var hint string
	for _, event := range events {
		if event.StderrExcerpt != "" {
			hint = event.StderrExcerpt
		}
	}
	if hint == "" || !strings.Contains(hint, "flush") {
		t.Fatalf("flush 를 짚어 줘야 합니다: %q", hint)
	}
}

// 채점 코드가 죽은 것은 **사용자 잘못이 아니다.** 오답으로 처리하면 출제자의 실수가
// 사용자 기록에 남는다.
func TestInteractiveTreatsBrokenInteractorAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusRuntimeError, ExitCode: 9, Stderr: "Traceback…"},
	}}

	newTestService(executor, sink).Judge(context.Background(), interactiveJob())

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

// 채점 코드 없이 들어온 작업을 짐작해 채점하지 않는다.
func TestInteractiveRefusesJobWithoutInteractor(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK}}}
	job := interactiveJob()
	job.Interactor = ""

	newTestService(executor, sink).Judge(context.Background(), job)

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

func TestInteractiveCarriesInteractorAndCase(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{Status: contract.StatusOK, ExitCode: 0}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewInteractiveJudge(captured, log).Judge(context.Background(), interactiveJob(), func(contract.Event) {})

	if captured.job.ExtraFiles["interactor.py"] == "" || captured.job.ExtraFiles["case.txt"] == "" {
		t.Fatalf("채점 코드와 숨은 값을 실어야 합니다: %+v", captured.job.ExtraFiles)
	}
}
