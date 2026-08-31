package worker

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
채점기의 기한 안에서 답한다 (#732).

**전에는 두 쪽이 각자 적은 값을 들고 있었다** — 채점기 60초, 실행기의 이미지 받기 5분.
그래서 이미지가 없는 런타임은 어느 조합에서도 성공할 수 없었다: 실행기가 5분을 다 쓰는
동안 채점기는 이미 1분 전에 포기했고, 운영에서 세 번 다 그렇게 실패했다
(`elapsedMs` 가 정확히 300003).
*/

// 부를 때까지 돌아오지 않는 샌드박스. 이미지를 오래 받는 상황을 세운다.
type stallingSandbox struct{ entered chan struct{} }

func (s *stallingSandbox) Run(ctx context.Context, _ sandbox.Spec) (sandbox.Outcome, error) {
	close(s.entered)
	<-ctx.Done()
	return sandbox.Outcome{}, ctx.Err()
}
func (s *stallingSandbox) Warm(context.Context, string) error { return nil }
func (s *stallingSandbox) Preflight(context.Context) error    { return nil }
func (s *stallingSandbox) Close() error                       { return nil }

func TestRunnerStopsWaitingAtJudgeDeadline(t *testing.T) {
	box := &stallingSandbox{entered: make(chan struct{})}
	runner := newTestRunner(t, box)

	job := contract.ExecJob{
		JobID: "deadline", RuntimeID: "python:3.12", SourceCode: "print(1)",
		TimeLimitMs: 2000, MemoryLimitMb: 256,
	}
	job.DeadlineUnixMs = time.Now().Add(contract.DeadlineMargin + 150*time.Millisecond).UnixMilli()

	started := time.Now()
	result := runner.Run(context.Background(), job)
	elapsed := time.Since(started)

	if elapsed > 3*time.Second {
		t.Fatalf("기한이 지났는데 %s 나 기다렸습니다", elapsed.Round(time.Millisecond))
	}
	if result.Status != contract.StatusSystemError {
		t.Fatalf("상태가 다릅니다: %s", result.Status)
	}
	// **`context deadline exceeded` 를 그대로 보여 주면 자기 코드가 잘못된 줄 안다.**
	if !strings.Contains(result.Stderr, "잠시 뒤 다시 제출") {
		t.Fatalf("사용자가 할 일을 말해야 합니다: %q", result.Stderr)
	}
}

func TestRunnerWithoutDeadlineKeepsWaiting(t *testing.T) {
	// 옛 채점기가 보낸 메시지에는 기한이 없다. 그때는 전처럼 돈다.
	box := &stallingSandbox{entered: make(chan struct{})}
	runner := newTestRunner(t, box)

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan contract.ExecResult, 1)
	go func() {
		done <- runner.Run(ctx, contract.ExecJob{
			JobID: "no-deadline", RuntimeID: "python:3.12", SourceCode: "print(1)",
			TimeLimitMs: 2000, MemoryLimitMb: 256,
		})
	}()

	<-box.entered
	select {
	case <-done:
		t.Fatal("기한이 없는데 스스로 멈췄습니다")
	case <-time.After(100 * time.Millisecond):
	}
	cancel()
	<-done
}
