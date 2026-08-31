package sandbox

// 받기 전에 "읽을 수 있는가" 만 짧게 묻는다 (#743).

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/containerd/containerd/v2/core/remotes/docker"
	remoteerrors "github.com/containerd/containerd/v2/core/remotes/errors"
	"github.com/containerd/errdefs"
)

/*
**거부당했는데도 30분을 썼다.**

레지스트리는 0초 만에 401 을 답했는데(`401 HEAD /v2/mongo/manifests/7  latency=0s`)
실행기는 미리 받기 예산 30분을 다 쓰고 `context deadline exceeded` 로 끝냈다. 그 문구는
**왜 못 받았는지 아무것도 말하지 않는다** — 진짜 이유는 레지스트리 쪽 로그에만 남고,
그것을 볼 생각을 하려면 이미 원인을 짐작하고 있어야 한다.

그래서 받기를 시작하기 전에 **매니페스트만 짧게** 물어본다. 401·403·404 는 30분을 더
기다린다고 통하게 되지 않으므로 그 자리에서 끊고 이유를 말한다.

**애매하면 그냥 받으러 간다.** 물어보다 시간이 지나거나 네트워크가 흔들린 것은 받기가
실패할 이유가 아니다 — 레지스트리가 잠깐 죽은 경우(#734)가 그렇다. 이 검사는 **막는
자리가 아니라 빨리 알려 주는 자리**다.
*/
const probeTimeout = 20 * time.Second

func (s *containerdSandbox) probe(ctx context.Context, ref string) error {
	ctx, cancel := context.WithTimeout(ctx, probeTimeout)
	defer cancel()

	resolver := docker.NewResolver(docker.ResolverOptions{
		Hosts: docker.ConfigureDefaultRegistries(s.registryOptions()...),
	})
	_, _, err := resolver.Resolve(ctx, ref)
	return err
}

/*
permanentPullFailure 는 **다시 시도해도 같은 답이 오는** 실패인가.

레지스트리가 잠깐 죽은 것과 자격증명이 안 통하는 것은 다르다. 앞엣것은 기다릴 값어치가
있고 뒤엣것은 없다 — 그런데 지금까지는 둘을 구분하지 않아 **둘 다 예산을 다 썼다.**
*/
func permanentPullFailure(err error) bool {
	if err == nil {
		return false
	}
	// containerd 가 뜻으로 분류해 준 것이 있으면 그것이 가장 정확하다.
	if errdefs.IsNotFound(err) || errdefs.IsUnauthorized(err) || errdefs.IsPermissionDenied(err) {
		return true
	}
	// 분류되지 않았으면 응답 코드를 본다. **문자열로 보지 않는다** — 레지스트리마다
	// 문구가 다르고, 문구는 판마다 바뀐다.
	var status remoteerrors.ErrUnexpectedStatus
	if errors.As(err, &status) {
		switch status.StatusCode {
		case 401, 403, 404:
			return true
		}
	}
	return false
}

// pullRefused 는 사람이 읽을 이유를 붙인다. `context deadline exceeded` 로 덮이지 않게.
func pullRefused(ref string, err error) error {
	return fmt.Errorf("%w (%s): %w", ErrPullRefused, ref, err)
}

/*
ErrPullRefused 는 **다시 받아도 소용없다**는 표시다 (#743).

미리 받기가 실패한 것을 몇 번 다시 시도하는데(#734), 그 재시도는 레지스트리가 잠깐
죽은 경우를 위한 것이다. 자격증명이 안 통하는 것을 세 번 더 물어볼 이유는 없다 —
그리고 그 사실이 로그에 남아야 사람이 무엇을 고칠지 안다.
*/
var ErrPullRefused = errors.New("레지스트리가 이미지를 내주지 않습니다")
