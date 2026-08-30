package main

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"sync"
	"testing"

	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
)

/*
미리 받기 (#712).

**이 시험이 지키는 것은 "하나가 실패해도 멈추지 않는다" 다.** 열아홉 개 중 하나가
레지스트리에 없을 수 있고, 그때 나머지를 포기하면 그 런타임들의 첫 제출이 전부
이미지 받기를 기다린다 — 고치려던 것이 그대로 남는다.
*/
type warmSpy struct {
	mu     sync.Mutex
	seen   []string
	failOn string
}

func (s *warmSpy) Preflight(context.Context) error { return nil }
func (s *warmSpy) Close() error                    { return nil }

func (s *warmSpy) Run(context.Context, sandbox.Spec) (sandbox.Outcome, error) {
	return sandbox.Outcome{}, nil
}

func (s *warmSpy) Warm(_ context.Context, image string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.seen = append(s.seen, image)
	if image == s.failOn {
		return errors.New("레지스트리에 없습니다")
	}
	return nil
}

func quietLogger() *slog.Logger { return slog.New(slog.NewTextHandler(io.Discard, nil)) }

func TestWarmImagesKeepsGoingAfterFailure(t *testing.T) {
	spy := &warmSpy{failOn: "b"}

	warmImages(context.Background(), spy, []string{"a", "b", "c"}, "", 0, 0, quietLogger())

	if len(spy.seen) != 3 {
		t.Fatalf("실패한 뒤에도 나머지를 받아야 합니다: %v", spy.seen)
	}
}

// 레지스트리 미러를 쓰면 그 주소로 받아야 한다 (#251). 안 붙이면 미러가 아니라
// 도커허브로 나가고, 실행기의 egress 는 그쪽이 막혀 있다.
func TestWarmImagesUsesRegistryPrefix(t *testing.T) {
	spy := &warmSpy{}

	warmImages(context.Background(), spy, []string{"gcc:13"}, "zot.registry.svc:5000", 0, 0, quietLogger())

	if len(spy.seen) != 1 || spy.seen[0] != "zot.registry.svc:5000/gcc:13" {
		t.Fatalf("미러 주소가 안 붙었습니다: %v", spy.seen)
	}
}

/*
**종료 신호가 오면 멈춘다.**

배포는 파드를 자주 갈아 끼운다. 그때마다 남은 이미지를 끝까지 받고 있으면 종료가
늦어지고, 유예 시간을 넘기면 하던 채점이 끊긴다 (#415 가 지킨 것).
*/
func TestWarmImagesStopsWhenCancelled(t *testing.T) {
	spy := &warmSpy{}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	warmImages(ctx, spy, []string{"a", "b", "c"}, "", 0, 0, quietLogger())

	if len(spy.seen) != 0 {
		t.Fatalf("이미 끊긴 문맥에서는 받지 말아야 합니다: %v", spy.seen)
	}
}

/*
다시 받기 (#734).

**전에는 기동 때 한 번이 전부였다.** 그때 레지스트리가 잠깐 죽어 있으면 그 런타임들은
영영 준비되지 않았다 — 운영에서 열아홉 중 넷만 받은 채 파드가 몇 시간을 그대로 돌았고,
그 사실을 아는 방법은 기동 로그의 WARN 을 사람이 읽는 것뿐이었다.
*/

// 처음 몇 번은 실패하고 그 뒤로 성공하는 샌드박스. 레지스트리 재시작을 흉내 낸다.
type flakySpy struct {
	mu        sync.Mutex
	attempts  map[string]int
	failUntil int
}

func newFlakySpy(failUntil int) *flakySpy {
	return &flakySpy{attempts: map[string]int{}, failUntil: failUntil}
}

func (s *flakySpy) Preflight(context.Context) error { return nil }
func (s *flakySpy) Close() error                    { return nil }
func (s *flakySpy) Run(context.Context, sandbox.Spec) (sandbox.Outcome, error) {
	return sandbox.Outcome{}, nil
}

func (s *flakySpy) Warm(_ context.Context, image string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.attempts[image]++
	if s.attempts[image] <= s.failUntil {
		return errors.New("connection refused")
	}
	return nil
}

func (s *flakySpy) countOf(image string) int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.attempts[image]
}

func TestWarmRetriesOnlyWhatFailed(t *testing.T) {
	// 첫 회차에 "b" 만 실패한다. **성공한 것을 다시 받을 이유는 없다.**
	spy := &warmSpy{failOn: "b"}
	warmImages(context.Background(), spy, []string{"a", "b", "c"}, "", 1, 0, quietLogger())

	counts := map[string]int{}
	for _, image := range spy.seen {
		counts[image]++
	}
	if counts["a"] != 1 || counts["c"] != 1 {
		t.Fatalf("성공한 것을 다시 받았습니다: %v", counts)
	}
	if counts["b"] != 2 {
		t.Fatalf("실패한 것을 다시 받지 않았습니다: %v", counts)
	}
}

func TestWarmStopsRetryingOnceItSucceeds(t *testing.T) {
	// 레지스트리가 한 번 실패한 뒤 살아난다. 더 두드릴 이유가 없다.
	spy := newFlakySpy(1)
	warmImages(context.Background(), spy, []string{"gcc:13"}, "", 3, 0, quietLogger())

	if got := spy.countOf("gcc:13"); got != 2 {
		t.Fatalf("살아난 뒤에도 계속 두드렸습니다: %d 번", got)
	}
}

func TestWarmGivesUpAfterRetries(t *testing.T) {
	// **무한히 두드리지 않는다.** 오래 죽은 레지스트리는 사람이 볼 일이다.
	spy := newFlakySpy(100)
	warmImages(context.Background(), spy, []string{"gcc:13"}, "", 2, 0, quietLogger())

	if got := spy.countOf("gcc:13"); got != 3 {
		t.Fatalf("처음 한 번 + 다시 두 번이어야 합니다: %d 번", got)
	}
}

func TestWarmRetryStopsWhenShuttingDown(t *testing.T) {
	// 종료 중에는 다시 받지 않는다. 파드가 내려가는데 이미지를 당길 이유가 없다.
	spy := newFlakySpy(100)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	warmImages(ctx, spy, []string{"gcc:13"}, "", 3, 0, quietLogger())

	if got := spy.countOf("gcc:13"); got != 0 {
		t.Fatalf("종료 중인데 받았습니다: %d 번", got)
	}
}
