package sandbox

// 이미지를 받고, 주소를 풀고, 이미지가 정한 환경 변수를 읽는 부분 (#68).

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/containerd/containerd/v2/client"
	"github.com/containerd/containerd/v2/core/content"
	"github.com/containerd/containerd/v2/core/remotes/docker"
	ocispec "github.com/opencontainers/image-spec/specs-go/v1"
)

/*
pull 은 이미지를 준비한다.

**이미 있으면 레지스트리에 묻지 않는다.** `client.Pull` 은 이미지가 로컬에 있어도 매번
매니페스트를 다시 해석하러 나간다 — 제출마다 레지스트리 왕복이 붙고, 익명 pull 제한에
걸리고, 레지스트리가 잠깐 흔들리면 채점이 멈춘다.

노드에 미리 받아 두는 것이 전제이므로(#96) 있는 것을 쓰는 쪽이 정상 경로다. 미러가
비공개라 자격증명이 필요한 환경에서도, 미리 받아 둔 노드는 그것 없이 돈다.
*/
func (s *containerdSandbox) pull(ctx context.Context, ref string) (client.Image, error) {
	full := normalizeRef(ref)

	if image, err := s.localImage(ctx, full); err != nil {
		return nil, err
	} else if image != nil {
		return image, nil
	}

	opts := []client.RemoteOpt{
		client.WithPlatformMatcher(targetPlatform()),
		client.WithPullUnpack,
		client.WithPullSnapshotter(defaultSnapshotter),
	}
	// 자격증명이 있을 때만 resolver 를 갈아 끼운다. 없으면 containerd 기본값(익명)이
	// 그대로 돌아 공개 이미지 경로가 바뀌지 않는다.
	if len(s.credentials) > 0 {
		opts = append(opts, client.WithResolver(docker.NewResolver(docker.ResolverOptions{
			Hosts: docker.ConfigureDefaultRegistries(
				docker.WithAuthorizer(docker.NewDockerAuthorizer(
					docker.WithAuthCreds(s.credentials.lookup),
				)),
			),
		})))
	}

	image, err := s.cli.Pull(ctx, full, opts...)
	if err != nil {
		return nil, fmt.Errorf("이미지를 받지 못했습니다 (%s, 자격증명 %s): %w",
			full, credentialState(s.credentials, full), err)
	}
	return image, nil
}

/*
normalizeRef 는 이미지 주소를 완전한 형태로 만든다.

**containerd 는 엔진 API 와 달리 축약을 풀어 주지 않는다.** `python:3.12-alpine` 을
그대로 주면 `python` 을 호스트로 읽고 `:3.12-alpine` 을 포트로 읽어 실패한다.

정의 파일(runtimes.yaml)은 사람이 읽는 축약형을 쓰므로 여기서 푼다 — 두 구현이 같은
정의 파일을 쓰게 하기 위함이다.
*/
func normalizeRef(ref string) string {
	if strings.Contains(ref, "@") && strings.HasPrefix(ref, "sha256:") {
		return ref
	}
	head, _, hasSlash := strings.Cut(ref, "/")
	switch {
	case !hasSlash:
		// python:3.12-alpine → docker.io/library/python:3.12-alpine
		return "docker.io/library/" + ref
	case !strings.Contains(head, ".") && !strings.Contains(head, ":") && head != "localhost":
		// shinkeonkim/foo → docker.io/shinkeonkim/foo
		// 호스트로 볼 만한 조각(점·포트·localhost)이 없으면 사용자 이름이다.
		return "docker.io/" + ref
	default:
		return ref
	}
}

/*
imageEnv 는 이미지가 정한 환경 변수를 읽는다.

**oci.WithImageConfig 를 쓰지 않고 설정 블롭만 직접 읽는다.** 그 옵션은 이미지의 USER 를
풀려고 rootfs 를 임시 마운트하는데, 권한 없이 도는 실행기에서 거기서 막힌다. 블롭을 읽는
것은 마운트하지 않는다.

**이미지의 PATH 는 반드시 필요하다.** 처음에는 이미지 설정을 통째로 버리고 우리 PATH 만
줬는데, 툴체인을 표준 경로 밖에 두는 이미지(go·java·rust)에서 `go: not found` 로 전부
컴파일이 깨졌다 — 런타임 매트릭스가 잡아냈다.

이미지를 믿어도 되는 이유: 실행 이미지는 우리가 만들어 다이제스트로 고정한 것이고(#96),
믿을 수 없는 것은 이미지가 아니라 **제출된 소스**다. 그래도 진입점·사용자·작업 디렉터리는
그대로 우리 값으로 덮어쓴다 — 그것들은 방어에 직접 걸리는 값이다.
*/
func (s *containerdSandbox) imageEnv(ctx context.Context, image client.Image) []string {
	desc, err := image.Config(ctx)
	if err != nil {
		return []string{sandboxPath}
	}
	blob, err := content.ReadBlob(ctx, s.cli.ContentStore(), desc)
	if err != nil {
		return []string{sandboxPath}
	}
	var config ocispec.Image
	if err := json.Unmarshal(blob, &config); err != nil {
		return []string{sandboxPath}
	}
	for _, entry := range config.Config.Env {
		if strings.HasPrefix(entry, "PATH=") {
			return config.Config.Env
		}
	}
	// PATH 가 없는 이미지도 있다. 그때만 우리 기본값을 앞에 붙인다.
	return append([]string{sandboxPath}, config.Config.Env...)
}

/*
localImage 는 이미 받아 둔 이미지를 찾는다. 없으면 (nil, nil) 이다.

**풀려 있는지까지 확인한다.** 이미지 레코드만 있고 스냅샷이 없는 상태가 있는데
(예: 다른 스냅샷터로 받아 둔 경우), 그대로 쓰면 컨테이너 생성에서 "parent snapshot does
not exist" 로 실패한다 — 원인이 이미지 쪽이라는 것을 알기 어려운 형태다.
*/
func (s *containerdSandbox) localImage(ctx context.Context, ref string) (client.Image, error) {
	image, err := s.cli.GetImage(ctx, ref)
	if err != nil {
		// 없는 것은 오류가 아니다. 받으면 된다.
		return nil, nil
	}
	unpacked, err := image.IsUnpacked(ctx, defaultSnapshotter)
	if err != nil {
		return nil, fmt.Errorf("이미지 상태를 확인하지 못했습니다 (%s): %w", ref, err)
	}
	if !unpacked {
		if err := image.Unpack(ctx, defaultSnapshotter); err != nil {
			return nil, fmt.Errorf("이미지를 펼치지 못했습니다 (%s): %w", ref, err)
		}
	}
	return image, nil
}

/*
credentialState 는 오류 메시지에 "자격증명이 있었는지"를 적는다.

403 을 받았을 때 가장 먼저 알아야 할 것이 이것이다. **비밀번호는 적지 않는다** — 로그에
남는다.
*/
func credentialState(creds registryCredentials, ref string) string {
	host, _, _ := strings.Cut(ref, "/")
	if _, ok := creds[normalizeAuthHost(host)]; ok {
		return "있음"
	}
	if len(creds) == 0 {
		return "없음"
	}
	return "이 호스트에는 없음"
}
