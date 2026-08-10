package worker

import (
	"context"
	"errors"
	"testing"

	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

type stubSandbox struct {
	outcome  sandbox.Outcome
	err      error
	lastSpec sandbox.Spec
}

func (s *stubSandbox) Run(_ context.Context, spec sandbox.Spec) (sandbox.Outcome, error) {
	s.lastSpec = spec
	return s.outcome, s.err
}

func (s *stubSandbox) Close() error { return nil }

func newTestRunner(t *testing.T, box sandbox.Sandbox) *Runner {
	t.Helper()
	registry, err := runtimes.LoadFromBytes([]byte(`
runtimes:
  - id: "python:3.12"
    label: "Python 3.12"
    image: "python:3.12-alpine"
    sourceFile: "main.py"
    run: ["python3", "main.py"]
`))
	if err != nil {
		t.Fatalf("테스트 레지스트리 준비 실패: %v", err)
	}
	return NewRunner(registry, box, 15000, 65536)
}

func TestRunReturnsSystemErrorForUnknownRuntime(t *testing.T) {
	runner := newTestRunner(t, &stubSandbox{})

	result := runner.Run(context.Background(), contract.ExecJob{JobID: "j1", RuntimeID: "ruby:3.3"})

	if result.Status != contract.StatusSystemError {
		t.Fatalf("알 수 없는 런타임은 SYSTEM_ERROR 여야 합니다: %+v", result)
	}
}

func TestRunPassesProblemLimitsToSandbox(t *testing.T) {
	box := &stubSandbox{}
	runner := newTestRunner(t, box)

	runner.Run(context.Background(), contract.ExecJob{
		JobID: "j1", RuntimeID: "python:3.12", TimeLimitMs: 3000, MemoryLimitMb: 512,
	})

	if box.lastSpec.TimeLimitMs != 3000 || box.lastSpec.MemoryLimitMb != 512 {
		t.Fatalf("문제별 제한이 전달되지 않았습니다: %+v", box.lastSpec)
	}
	if box.lastSpec.Image != "python:3.12-alpine" {
		t.Fatalf("런타임 이미지가 잘못 전달되었습니다: %s", box.lastSpec.Image)
	}
}

func TestRunConvertsSandboxErrorToSystemError(t *testing.T) {
	runner := newTestRunner(t, &stubSandbox{err: errors.New("도커 없음")})

	result := runner.Run(context.Background(), contract.ExecJob{JobID: "j1", RuntimeID: "python:3.12"})

	if result.Status != contract.StatusSystemError {
		t.Fatalf("샌드박스 오류는 SYSTEM_ERROR 로 표현되어야 합니다: %+v", result)
	}
}

func TestStatusOfPrefersCompileThenTimeThenMemory(t *testing.T) {
	cases := []struct {
		outcome  sandbox.Outcome
		expected contract.ExecStatus
	}{
		{sandbox.Outcome{CompileFailed: true, TimedOut: true}, contract.StatusCompileError},
		{sandbox.Outcome{TimedOut: true, OutOfMemory: true}, contract.StatusTimeLimitExceeded},
		{sandbox.Outcome{OutOfMemory: true, ExitCode: 137}, contract.StatusMemoryLimitExceeded},
		{sandbox.Outcome{Truncated: true}, contract.StatusOutputLimitExceeded},
		{sandbox.Outcome{ExitCode: 1}, contract.StatusRuntimeError},
		{sandbox.Outcome{ExitCode: 0}, contract.StatusOK},
	}

	for _, c := range cases {
		if got := statusOf(c.outcome); got != c.expected {
			t.Errorf("statusOf(%+v) = %s, 기대값 %s", c.outcome, got, c.expected)
		}
	}
}
