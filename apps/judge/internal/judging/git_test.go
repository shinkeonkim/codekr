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
Git 채점 (#654).

**Redis 와 모델이 같지만 조용히 틀리는 자리가 하나 더 있다**: 커밋 해시는 신원과
시각을 함께 해싱하므로, 그것을 고정하지 않으면 **같은 답이 때에 따라 틀린다**.
고정은 하네스가 하고 여기서는 그 결과를 견주는 규칙만 본다.
*/

func gitJob(commands string) contract.JudgeJob {
	return contract.JudgeJob{
		SubmissionID:  31,
		Kind:          contract.KindJudgeGit,
		RuntimeID:     defaultGitRuntimeID,
		SourceCode:    commands,
		TimeLimitMs:   10000,
		MemoryLimitMb: 512,
		Git: &contract.JudgeGitSpec{
			Seed:   "git commit -q --allow-empty -m base",
			Answer: "git commit -q --allow-empty -m fix",
			Verify: "git log --format='%T %s'",
		},
	}
}

func TestGitJudgeAcceptsMatchingState(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("t1 fix\nt0 base", "t1 fix\nt0 base")},
	}}

	newTestService(executor, sink).Judge(context.Background(), gitJob("git commit --allow-empty -m fix"))

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("정답이어야 합니다: %s", last.Verdict)
	}
}

func TestGitJudgeRejectsDifferentHistory(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("t1 fix\nt0 base", "t0 base")},
	}}

	newTestService(executor, sink).Judge(context.Background(), gitJob("git status"))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("오답이어야 합니다: %s", last.Verdict)
	}
}

/*
**순서를 무시하지 않는다.**

확인 명령이 대개 `git log` 이고 거기서 줄 순서는 곧 커밋 순서다. 무시하면
**순서가 뒤집힌 히스토리**가 정답이 된다 — 되돌리기·재배치 문제에서 그것이 곧 답이다.
*/
func TestGitJudgeKeepsCommitOrder(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("t1 fix\nt0 base", "t0 base\nt1 fix")},
	}}

	newTestService(executor, sink).Judge(context.Background(), gitJob("git rebase …"))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("순서가 뒤집히면 오답이어야 합니다: %s", last.Verdict)
	}
}

/*
막힌 명령은 오답이 아니라 **무엇이 막혔는지 보여야 한다.**

네트워크 명령이 여기로 온다 — 하네스가 `protocol.allow=never` 로 **즉시** 거부하므로,
시간 제한을 다 쓰고 "느리다" 로 보이지 않는다.
*/
func TestGitJudgeReportsBlockedCommandAsCompileError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{
			Status:   contract.StatusRuntimeError,
			ExitCode: 1,
			Stdout:   harnessOutput("t1 fix\nt0 base", ""),
			Stderr:   "fatal: transport 'https' not allowed",
		},
	}}

	newTestService(executor, sink).Judge(context.Background(), gitJob("git clone https://…"))

	if last := sink.last(); last.Verdict != contract.VerdictCompileError {
		t.Fatalf("막힌 명령은 컴파일 오류로 대응한다: %s", last.Verdict)
	}
}

func TestGitJudgeTreatsMissingExpectedAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "구분선 없는 출력", Stderr: "시드가 실패했습니다"},
	}}

	newTestService(executor, sink).Judge(context.Background(), gitJob("git status"))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

func TestGitJudgeTreatsExecutorFailureAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{err: errors.New("실행기가 응답하지 않습니다")}

	newTestService(executor, sink).Judge(context.Background(), gitJob("git status"))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

func TestGitJudgeSendsSpecFilesToExecutor(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("t0", "t0"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))

	NewGitJudge(captured, log).Judge(context.Background(), gitJob("git status"), func(contract.Event) {})

	for _, name := range []string{"answer.git", "verify.git", "seed.git"} {
		if captured.job.ExtraFiles[name] == "" {
			t.Fatalf("%s 를 실어야 합니다: %+v", name, captured.job.ExtraFiles)
		}
	}
}

// 시드는 없어도 된다 — 빈 저장소에서 시작하는 문제가 있다.
func TestGitJudgeOmitsSeedFileWhenAbsent(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("t0", "t0"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := gitJob("git status")
	job.Git.Seed = ""

	NewGitJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if _, found := captured.job.ExtraFiles["seed.git"]; found {
		t.Fatalf("시드가 없으면 파일도 없어야 합니다: %+v", captured.job.ExtraFiles)
	}
}

func TestGitJudgeFallsBackToDefaultRuntime(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("t0", "t0"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := gitJob("git status")
	job.RuntimeID = ""

	NewGitJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if captured.job.RuntimeID != defaultGitRuntimeID {
		t.Fatalf("기본 런타임으로 보내야 합니다: %q", captured.job.RuntimeID)
	}
}

func TestGitJudgeRefusesJobWithoutSpec(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK}}}
	job := gitJob("git status")
	job.Git = nil

	newTestService(executor, sink).Judge(context.Background(), job)

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
	if executor.calls != 0 {
		t.Fatalf("실행기를 부르지 않아야 합니다: %d회", executor.calls)
	}
}
