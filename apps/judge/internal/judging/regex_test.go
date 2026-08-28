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
정규식 채점 (#653).

**이 유형은 다른 것보다 조용히 틀리기 쉽다.** 하네스가 기대와 실제를 줄 단위로 내는데,
줄 하나가 밀리면 **모든 판정이 한 칸씩 어긋난 채** 그럴듯한 결과가 나온다.
그래서 정답만이 아니라 **틀린 답과 어긋난 경우**를 함께 넣는다 (#605 의 교훈).
*/

func regexJob(pattern string) contract.JudgeJob {
	return contract.JudgeJob{
		SubmissionID:  21,
		Kind:          contract.KindJudgeRegex,
		RuntimeID:     defaultRegexRuntimeID,
		SourceCode:    pattern,
		TimeLimitMs:   5000,
		MemoryLimitMb: 256,
		Regex: &contract.JudgeRegexSpec{
			Cases:     "+abc123\n-abc\n",
			FullMatch: true,
		},
	}
}

func TestRegexJudgeAcceptsMatchingVerdicts(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("MATCH\nNO", "MATCH\nNO")},
	}}

	newTestService(executor, sink).Judge(context.Background(), regexJob(`^[a-z]+\d+$`))

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("정답이어야 합니다: %s", last.Verdict)
	}
}

// 너무 넓은 패턴은 **맞으면 안 되는 것까지 맞힌다.**
func TestRegexJudgeRejectsOverlyBroadPattern(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("MATCH\nNO", "MATCH\nMATCH")},
	}}

	newTestService(executor, sink).Judge(context.Background(), regexJob(".*"))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("오답이어야 합니다: %s", last.Verdict)
	}
}

/*
**순서를 무시하지 않는다.**

줄 순서가 곧 "어느 문자열의 판정인가" 다. 여기서 순서를 무시하면 **맞아야 할 것과
아닌 것을 정확히 뒤집은 패턴**이 정답이 된다 — 개수만 같기 때문이다.
*/
func TestRegexJudgeKeepsLineOrder(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: harnessOutput("MATCH\nNO", "NO\nMATCH")},
	}}

	newTestService(executor, sink).Judge(context.Background(), regexJob("뒤집힌 패턴"))

	if last := sink.last(); last.Verdict != contract.VerdictWrongAnswer {
		t.Fatalf("뒤집힌 판정은 오답이어야 합니다: %s", last.Verdict)
	}
}

// 문법이 틀린 것은 오답이 아니라 **무엇이 틀렸는지 보여야 한다.**
func TestRegexJudgeReportsBadSyntaxAsCompileError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{
			Status:   contract.StatusRuntimeError,
			ExitCode: 1,
			Stdout:   harnessOutput("MATCH\nNO", ""),
			Stderr:   "정규식 문법이 올바르지 않습니다: missing ), unterminated subpattern",
		},
	}}

	newTestService(executor, sink).Judge(context.Background(), regexJob("(abc"))

	if last := sink.last(); last.Verdict != contract.VerdictCompileError {
		t.Fatalf("문법 오류는 컴파일 오류로 대응한다: %s", last.Verdict)
	}
}

/*
**재앙적 백트래킹은 시간 초과로 온다** (#653).

`(a+)+$` 는 실제로 매달리고 샌드박스가 컨테이너째로 끊는다. 그것이 정확한 답이다 —
그 패턴은 실제로 느리다. 위험한 패턴을 우리가 판정해 막는 쪽은 택하지 않았다:
그 판정은 엔진마다 다르다.
*/
func TestRegexJudgeMapsCatastrophicBacktrackingToTimeLimit(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusTimeLimitExceeded},
	}}

	newTestService(executor, sink).Judge(context.Background(), regexJob("(a+)+$"))

	if last := sink.last(); last.Verdict != contract.VerdictTimeLimitExceeded {
		t.Fatalf("시간 초과여야 합니다: %s", last.Verdict)
	}
}

// 하네스가 기대 판정을 못 냈으면 **출제자의 실수**다 — 오답으로 남기지 않는다.
func TestRegexJudgeTreatsMissingExpectedAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "구분선 없는 출력", Stderr: "확인할 문자열이 없습니다"},
	}}

	newTestService(executor, sink).Judge(context.Background(), regexJob("^a$"))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

func TestRegexJudgeTreatsExecutorFailureAsSystemError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{err: errors.New("실행기가 응답하지 않습니다")}

	newTestService(executor, sink).Judge(context.Background(), regexJob("^a$"))

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
}

func TestRegexJudgeSendsCasesAndModeToExecutor(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("MATCH", "MATCH"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := regexJob("^a$")
	job.Regex.IgnoreCase = true

	NewRegexJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if got := captured.job.ExtraFiles["cases.txt"]; got != job.Regex.Cases {
		t.Fatalf("확인 문자열을 그대로 실어야 합니다: %q", got)
	}
	// **전체 일치와 대소문자 무시가 함께 전달돼야 한다** — 하나만 가면 판정이 조용히 바뀐다.
	if got := captured.job.ExtraFiles["mode.txt"]; got != "full:i" {
		t.Fatalf("모드가 어긋납니다: %q", got)
	}
	// **패턴은 소스 자리로 간다** — 하네스가 그것을 파일에서 읽어 자료로 쓴다.
	if captured.job.SourceCode != "^a$" {
		t.Fatalf("패턴을 그대로 보내야 합니다: %q", captured.job.SourceCode)
	}
}

func TestRegexJudgeUsesSearchModeByDefault(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("MATCH", "MATCH"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := regexJob("a")
	job.Regex.FullMatch = false

	NewRegexJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if got := captured.job.ExtraFiles["mode.txt"]; got != "search" {
		t.Fatalf("기본은 부분 일치여야 합니다: %q", got)
	}
}

func TestRegexJudgeFallsBackToDefaultRuntime(t *testing.T) {
	captured := &capturingExecutor{result: contract.ExecResult{
		Status: contract.StatusOK, Stdout: harnessOutput("MATCH", "MATCH"),
	}}
	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	job := regexJob("^a$")
	job.RuntimeID = ""

	NewRegexJudge(captured, log).Judge(context.Background(), job, func(contract.Event) {})

	if captured.job.RuntimeID != defaultRegexRuntimeID {
		t.Fatalf("기본 런타임으로 보내야 합니다: %q", captured.job.RuntimeID)
	}
}

// 스펙 없이 들어온 작업을 짐작해 채점하지 않는다 — 그것은 출제자의 실수다.
func TestRegexJudgeRefusesJobWithoutSpec(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK}}}
	job := regexJob("^a$")
	job.Regex = nil

	newTestService(executor, sink).Judge(context.Background(), job)

	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류여야 합니다: %s", last.Verdict)
	}
	if executor.calls != 0 {
		t.Fatalf("실행기를 부르지 않아야 합니다: %d회", executor.calls)
	}
}
