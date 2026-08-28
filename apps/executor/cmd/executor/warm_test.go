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

	warmImages(context.Background(), spy, []string{"a", "b", "c"}, "", quietLogger())

	if len(spy.seen) != 3 {
		t.Fatalf("실패한 뒤에도 나머지를 받아야 합니다: %v", spy.seen)
	}
}

// 레지스트리 미러를 쓰면 그 주소로 받아야 한다 (#251). 안 붙이면 미러가 아니라
// 도커허브로 나가고, 실행기의 egress 는 그쪽이 막혀 있다.
func TestWarmImagesUsesRegistryPrefix(t *testing.T) {
	spy := &warmSpy{}

	warmImages(context.Background(), spy, []string{"gcc:13"}, "zot.registry.svc:5000", quietLogger())

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

	warmImages(ctx, spy, []string{"a", "b", "c"}, "", quietLogger())

	if len(spy.seen) != 0 {
		t.Fatalf("이미 끊긴 문맥에서는 받지 말아야 합니다: %v", spy.seen)
	}
}
