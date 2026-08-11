package sandbox

import (
	"context"
	"fmt"
	"time"

	"github.com/containerd/containerd/v2/client"
	"github.com/containerd/containerd/v2/pkg/namespaces"
)

// 우리가 만드는 컨테이너를 담을 containerd 네임스페이스.
//
// 기본 네임스페이스를 쓰지 않는 이유: 같은 노드의 다른 도구(빌드킷 등)와 섞이면
// **무엇이 우리 것인지** 구분할 수 없다. 정리할 때도 남의 것을 지울 위험이 생긴다.
const containerdNamespace = "codekr"

// 기동 시 연결 확인에 주는 시간.
const connectTimeout = 5 * time.Second

// 스냅샷터. containerd 의 기본값이고, 노드마다 다르면 이미지가 풀리지 않는다.
const defaultSnapshotter = "overlayfs"

// OCI 런타임. containerd 의 기본값이다.
const defaultRuntime = "io.containerd.runc.v2"

// 이미지가 PATH 를 주지 않을 때 쓸 기본값.
const sandboxPath = "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

// containerdSandbox 는 containerd 에 직접 붙는 구현이다 (#68).
//
// 엔진 API 구현(container.go)과 **같은 방어를 건다** — 검사 기준(internal/selftest)이
// 같아야 두 구현을 견줄 수 있기 때문이다.
type containerdSandbox struct {
	cli            *client.Client
	seccompProfile string
}

// NewContainerdSandbox 는 containerd 기반 샌드박스를 만든다.
//
// address 가 비어 있으면 기본 소켓 경로를 쓴다. macOS 에서는 colima 가 노출하는
// 호스트 경로를 준다 (docs/05, #70).
func NewContainerdSandbox(address, seccompProfilePath string) (Sandbox, error) {
	profile, err := readSeccompProfile(seccompProfilePath)
	if err != nil {
		return nil, err
	}
	if address == "" {
		address = "/run/containerd/containerd.sock"
	}
	// **클라이언트의 기본 플랫폼을 linux 로 못 박는다.**
	//
	// 이것이 핵심이다. `WithNewSnapshot` 은 이미지의 rootfs 를 이 플랫폼으로 골라
	// 부모 스냅샷(chainID)을 계산한다. macOS 에서 개발하면 기본값이 darwin/arm64 라,
	// pull 만 linux 로 고정해도 스냅샷은 다른 매니페스트를 보고 부모를 찾지 못한다
	// ("parent snapshot ... does not exist").
	//
	// 대상은 언제나 **containerd 가 도는 곳**이지 클라이언트가 도는 곳이 아니다.
	cli, err := client.New(address, client.WithDefaultPlatform(targetPlatform()))
	if err != nil {
		return nil, fmt.Errorf("containerd 에 연결하지 못했습니다 (%s): %w", address, err)
	}

	// **여기서 한 번 말을 걸어 본다.** client.New 는 실제로 붙지 않아서, 확인하지 않으면
	// 소켓이 없어도 조용히 뜬 뒤 첫 제출에서 실패한다.
	sandbox := &containerdSandbox{cli: cli, seccompProfile: profile}
	ctx, cancel := context.WithTimeout(context.Background(), connectTimeout)
	defer cancel()
	if err := sandbox.Preflight(ctx); err != nil {
		_ = cli.Close()
		return nil, err
	}
	return sandbox, nil
}

func (s *containerdSandbox) Close() error { return s.cli.Close() }

// Preflight 는 containerd 에 닿는지 확인한다.
//
// **기동 시점에 확인한다.** 첫 제출에서야 드러나면 그 제출이 실패로 기록된다.
func (s *containerdSandbox) Preflight(ctx context.Context) error {
	if _, err := s.cli.Version(s.withNamespace(ctx)); err != nil {
		return fmt.Errorf("containerd 에 닿지 못했습니다: %w", err)
	}
	return nil
}

func (s *containerdSandbox) Run(ctx context.Context, spec Spec) (Outcome, error) {
	budget := lifetime(spec)
	ctx, cancel := context.WithTimeout(s.withNamespace(ctx), budget)
	defer cancel()

	image, err := s.pull(ctx, spec.Image)
	if err != nil {
		return Outcome{}, err
	}

	env := s.imageEnv(ctx, image)

	id := fmt.Sprintf("codekr-%d-%s", time.Now().UnixNano(), randomSuffix())
	container, err := s.create(ctx, id, image, spec, env, budget)
	if err != nil {
		return Outcome{}, err
	}
	// 컨테이너와 스냅샷을 함께 지운다. 남기면 노드의 디스크가 찬다.
	defer func() {
		_ = container.Delete(context.WithoutCancel(ctx), client.WithSnapshotCleanup)
	}()

	return s.runInside(ctx, container, spec, env)
}

func (s *containerdSandbox) withNamespace(ctx context.Context) context.Context {
	return namespaces.WithNamespace(ctx, containerdNamespace)
}

// 같은 나노초에 두 개가 겹치지 않게 이름 뒤에 붙이는 조각.
func randomSuffix() string {
	return fmt.Sprintf("%x", time.Now().UnixNano()%0xffffff)
}
