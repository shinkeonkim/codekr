package sandbox

// 같은 이미지를 동시에 두 번 받지 않는다 (#732).

import (
	"context"
	"sync"

	"github.com/containerd/containerd/v2/client"
)

/*
**재시도가 서로를 느리게 만들고 있었다.**

이미지가 없는 런타임에 제출이 들어오면 받기가 시작되는데, 채점기는 60초에 포기하고
사용자에게 `SYSTEM_ERROR` 를 보여 준다. 사용자는 실행기가 아직 받고 있다는 것을 모르고
다시 낸다 — 그러면 **같은 파드에서 같은 이미지를 또 받기 시작한다.**

대역을 나눠 쓰니 둘 다 받기 예산(5분)을 넘겨 취소되고, 이미지는 여전히 없다. 낼수록
느려진다. 운영에서 세 번 다 그렇게 실패했다(`elapsedMs` 가 정확히 300003).

그래서 **먼저 온 하나만 받고 나머지는 그 결과를 기다린다.** 기다리는 쪽이 취소되어도
받는 쪽은 계속 간다 — 그것이 다음 제출을 살린다.
*/
type pullGroup struct {
	mu    sync.Mutex
	calls map[string]*pullCall
}

type pullCall struct {
	done  chan struct{}
	image client.Image
	err   error
}

func newPullGroup() *pullGroup {
	return &pullGroup{calls: make(map[string]*pullCall)}
}

// Do 는 `ref` 하나당 한 번만 `fetch` 를 부르고, 같은 ref 를 기다리는 쪽에는 그 결과를 나눠 준다.
func (g *pullGroup) Do(ctx context.Context, ref string, fetch func(context.Context) (client.Image, error)) (client.Image, error) {
	g.mu.Lock()
	if call, running := g.calls[ref]; running {
		g.mu.Unlock()
		return call.wait(ctx)
	}
	call := &pullCall{done: make(chan struct{})}
	g.calls[ref] = call
	g.mu.Unlock()

	/*
		**받는 쪽은 부른 사람의 ctx 를 쓰지 않는다.**

		그 사람이 포기해도 받기는 끝까지 가야 한다 — 여기서 함께 취소하면 다음 제출이
		처음부터 다시 받게 되고, 그것이 바로 수렴하지 않던 이유다.

		그렇다고 무기한은 아니다: `pull` 이 자기 예산을 씌운다.
	*/
	go func() {
		call.image, call.err = fetch(context.WithoutCancel(ctx))
		g.mu.Lock()
		delete(g.calls, ref)
		g.mu.Unlock()
		close(call.done)
	}()

	return call.wait(ctx)
}

// wait 는 결과를 기다리되, 부른 사람이 포기하면 **받기는 남겨 두고** 먼저 빠진다.
func (c *pullCall) wait(ctx context.Context) (client.Image, error) {
	select {
	case <-c.done:
		return c.image, c.err
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}
