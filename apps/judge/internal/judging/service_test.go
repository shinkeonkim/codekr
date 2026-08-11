package judging

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

type stubExecutor struct {
	results []contract.ExecResult
	err     error
	calls   int
}

func (s *stubExecutor) Run(context.Context, contract.ExecJob) (contract.ExecResult, error) {
	if s.err != nil {
		return contract.ExecResult{}, s.err
	}
	result := s.results[min(s.calls, len(s.results)-1)]
	s.calls++
	return result, nil
}

type recordingSink struct {
	events []contract.Event
}

func (r *recordingSink) Publish(_ context.Context, event contract.Event) error {
	r.events = append(r.events, event)
	return nil
}

func (r *recordingSink) last() contract.Event { return r.events[len(r.events)-1] }

func newJob(cases int) contract.JudgeJob {
	job := contract.JudgeJob{SubmissionID: 1, RuntimeID: "python:3.12", TimeLimitMs: 2000, MemoryLimitMb: 256}
	for i := 1; i <= cases; i++ {
		job.Testcases = append(job.Testcases, contract.JudgeTestcase{ID: int64(i), Seq: i, Input: "1 2\n", ExpectedOutput: "3\n"})
	}
	return job
}

func newTestService(executor ExecutorClient, sink EventSink) *Service {
	return NewService(executor, sink, slog.New(slog.NewTextHandler(io.Discard, nil)))
}

func TestJudgeEmitsProgressForEveryTestcase(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: "3\n"}}}

	newTestService(executor, sink).Judge(context.Background(), newJob(3))

	// JUDGING 1개 + TESTCASE 3개 + COMPLETED 1개
	if len(sink.events) != 5 {
		t.Fatalf("이벤트 수가 기대와 다릅니다: %d", len(sink.events))
	}
	if sink.events[0].Type != contract.EventJudging || sink.events[0].TotalCount != 3 {
		t.Errorf("첫 이벤트는 전체 개수를 담은 JUDGING 이어야 합니다: %+v", sink.events[0])
	}
	if sink.last().Type != contract.EventCompleted || sink.last().Verdict != contract.VerdictAccepted {
		t.Errorf("마지막 이벤트가 올바르지 않습니다: %+v", sink.last())
	}
}

func TestJudgeContinuesAfterWrongAnswer(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusOK, Stdout: "9\n"},
		{Status: contract.StatusOK, Stdout: "3\n"},
		{Status: contract.StatusOK, Stdout: "3\n"},
	}}

	newTestService(executor, sink).Judge(context.Background(), newJob(3))

	if executor.calls != 3 {
		t.Fatalf("오답 이후에도 남은 테스트케이스를 채점해야 합니다: %d 회 실행", executor.calls)
	}
	if sink.last().PassedCount != 2 || sink.last().Verdict != contract.VerdictWrongAnswer {
		t.Errorf("집계가 올바르지 않습니다: %+v", sink.last())
	}
}

func TestJudgeStopsImmediatelyOnCompileError(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{
		{Status: contract.StatusCompileError, Stderr: "syntax error"},
	}}

	newTestService(executor, sink).Judge(context.Background(), newJob(5))

	if executor.calls != 1 {
		t.Fatalf("컴파일 실패 후에는 더 실행하지 않아야 합니다: %d 회 실행", executor.calls)
	}
	if sink.last().Verdict != contract.VerdictCompileError || sink.last().CompileError == "" {
		t.Errorf("컴파일 오류 정보가 전달되지 않았습니다: %+v", sink.last())
	}
}

func TestJudgeCompletesSubmissionWhenExecutorIsUnavailable(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{err: errors.New("실행기 응답 없음")}

	newTestService(executor, sink).Judge(context.Background(), newJob(2))

	// 실행기가 죽어도 제출이 영원히 대기 상태로 남지 않아야 한다.
	if sink.last().Type != contract.EventCompleted || sink.last().Verdict != contract.VerdictSystemError {
		t.Errorf("인프라 장애가 SYSTEM_ERROR 로 종결되지 않았습니다: %+v", sink.last())
	}
}

func TestJudgeRejectsUnknownProblemKind(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: "3\n"}}}

	job := newJob(2)
	job.Kind = contract.KindQuiz // 아직 구현이 없는 유형이다.
	newTestService(executor, sink).Judge(context.Background(), job)

	// stdin/stdout 으로 짐작해 채점하면 엉뚱한 판정이 사용자 기록에 남는다.
	if executor.calls != 0 {
		t.Fatalf("실행기를 부르지 않아야 합니다: calls=%d", executor.calls)
	}
	if last := sink.last(); last.Verdict != contract.VerdictSystemError {
		t.Fatalf("시스템 오류로 종결해야 합니다: %s", last.Verdict)
	}
}

func TestJudgeTreatsEmptyKindAsStdio(t *testing.T) {
	sink := &recordingSink{}
	executor := &stubExecutor{results: []contract.ExecResult{{Status: contract.StatusOK, Stdout: "3\n"}}}

	// 유형 필드가 없던 시절에 큐에 들어간 작업이 남아 있을 수 있다.
	job := newJob(2)
	job.Kind = ""
	newTestService(executor, sink).Judge(context.Background(), job)

	if last := sink.last(); last.Verdict != contract.VerdictAccepted {
		t.Fatalf("stdin/stdout 으로 채점해야 합니다: %s", last.Verdict)
	}
}
