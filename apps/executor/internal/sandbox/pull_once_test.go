package sandbox

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/containerd/containerd/v2/client"
)

/*
같은 이미지를 동시에 두 번 받지 않는다 (#732).

**재시도가 서로를 느리게 만들고 있었다.** 채점기가 60초에 포기해 사용자가 다시 내면,
같은 파드가 같은 이미지를 또 받기 시작했다 — 대역을 나눠 쓰니 둘 다 예산을 넘겨
취소되고 이미지는 여전히 없었다. 운영에서 세 번 다 그렇게 실패했다.
*/
// 받는 척하는 함수. 이 시험들이 보는 것은 **몇 번 받았는가**이지 무엇을 받았는가가
// 아니라서, 돌려주는 이미지는 비어 있고 결과는 부르는 쪽이 정한다.
func countingFetch(calls *atomic.Int32, gate <-chan struct{}, err error) func(context.Context) (client.Image, error) {
	return func(context.Context) (client.Image, error) {
		calls.Add(1)
		if gate != nil {
			<-gate
		}
		var image client.Image
		return image, err
	}
}

func TestPullGroupFetchesOncePerImage(t *testing.T) {
	group := newPullGroup()
	var calls atomic.Int32
	release := make(chan struct{})

	fetch := countingFetch(&calls, release, nil)

	var wg sync.WaitGroup
	for range 5 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, _ = group.Do(context.Background(), "gcc:13", fetch)
		}()
	}

	// 다섯이 다 들어갈 때까지 기다린다. 하나만 받고 있어야 한다.
	waitUntil(t, func() bool { return calls.Load() == 1 })
	time.Sleep(20 * time.Millisecond)
	if got := calls.Load(); got != 1 {
		t.Fatalf("같은 이미지를 %d 번 받았습니다. 한 번이어야 합니다", got)
	}

	close(release)
	wg.Wait()
}

func TestPullGroupSeparatesDifferentImages(t *testing.T) {
	// 다른 이미지끼리는 서로 기다릴 이유가 없다.
	group := newPullGroup()
	var calls atomic.Int32
	fetch := countingFetch(&calls, nil, nil)

	for _, ref := range []string{"gcc:13", "python:3.12-alpine"} {
		if _, err := group.Do(context.Background(), ref, fetch); err != nil {
			t.Fatalf("받기 실패: %v", err)
		}
	}
	if got := calls.Load(); got != 2 {
		t.Fatalf("서로 다른 이미지 둘인데 %d 번 받았습니다", got)
	}
}

func TestPullGroupKeepsFetchingWhenWaiterGivesUp(t *testing.T) {
	/*
		**기다리던 사람이 포기해도 받기는 끝까지 간다.**

		함께 취소하면 다음 제출이 처음부터 다시 받게 되고, 그것이 바로 수렴하지 않던
		이유다 — 사용자가 낼수록 이미지에서 멀어진다.
	*/
	group := newPullGroup()
	started := make(chan struct{})
	finished := make(chan struct{})
	var stopped atomic.Int32

	// 부른 사람의 취소가 여기로 오면 안 된다 — 오면 `finished` 가 닫힌다.
	fetch := func(ctx context.Context) (client.Image, error) {
		close(started)
		<-ctx.Done()
		close(finished)
		return countingFetch(&stopped, nil, ctx.Err())(ctx)
	}

	ctx, cancel := context.WithCancel(context.Background())
	go func() { _, _ = group.Do(ctx, "gcc:13", fetch) }()
	<-started
	cancel()

	select {
	case <-finished:
		t.Fatal("기다리던 사람이 포기하자 받기까지 멈췄습니다")
	case <-time.After(50 * time.Millisecond):
	}
}

func TestPullGroupSharesFailure(t *testing.T) {
	// 실패도 나눠 준다. 안 그러면 뒤에 선 사람이 이유 없이 성공한 줄 안다.
	group := newPullGroup()
	want := errors.New("레지스트리에 닿지 못했습니다")

	var tries atomic.Int32
	if _, err := group.Do(context.Background(), "gcc:13", countingFetch(&tries, nil, want)); !errors.Is(err, want) {
		t.Fatalf("받기 실패가 그대로 와야 합니다: %v", err)
	}

	// 끝난 뒤에는 자리를 비운다 — 안 그러면 실패한 결과가 영영 남는다.
	var second error
	var none atomic.Int32
	_, second = group.Do(context.Background(), "gcc:13", countingFetch(&none, nil, nil))
	if second != nil {
		t.Fatalf("다음 시도는 새로 받아야 합니다: %v", second)
	}
}

func waitUntil(t *testing.T, done func() bool) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for !done() {
		if time.Now().After(deadline) {
			t.Fatal("기다리던 상태가 되지 않았습니다")
		}
		time.Sleep(time.Millisecond)
	}
}
