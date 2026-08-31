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
	// **여기서 씌운다.** containerd 는 모든 호출에 네임스페이스를 요구하는데, 전에는
	// 부르는 쪽이 씌우고 있었다 — 그래서 새로 생긴 부르는 쪽(#712 의 미리 받기)이
	// 그것을 몰랐고, 운영에서 열넷이 전부 `namespace is required` 로 떨어졌다.
	//
	// 두 번 씌워도 안전하다(뒤엣것이 이긴다). 안 씌우는 길이 없는 편이 낫다.
	ctx = s.withNamespace(ctx)
	full := normalizeRef(ref)

	if image, err := s.localImage(ctx, full); err != nil {
		return nil, err
	} else if image != nil {
		return image, nil
	}

	// **받기 전에 짧게 물어본다** (#743). 401·403·404 는 기다린다고 달라지지 않는데,
	// 전에는 그것을 모르고 예산(미리 받기 30분)을 통째로 썼다.
	if err := s.probe(ctx, full); permanentPullFailure(err) {
		return nil, pullRefused(full, err)
	}

	// 여기부터가 실제로 받는 길이다. **같은 이미지를 동시에 두 번 받지 않는다** (#732) —
	// 노드에 이미 있으면 위에서 돌아가므로 이 잠금은 정말 받을 때만 걸린다.
	return s.pulls.Do(ctx, full, func(ctx context.Context) (client.Image, error) {
		return s.fetch(ctx, full)
	})
}

// fetch 는 레지스트리에서 실제로 받아 온다. 부르는 쪽은 `pull` 하나다.
func (s *containerdSandbox) fetch(ctx context.Context, full string) (client.Image, error) {

	opts := []client.RemoteOpt{
		client.WithPlatformMatcher(targetPlatform()),
		client.WithPullUnpack,
		client.WithPullSnapshotter(defaultSnapshotter),
	}
	// 자격증명이나 평문 호스트가 있을 때만 resolver 를 갈아 끼운다. 둘 다 없으면
	// containerd 기본값(익명·TLS)이 그대로 돌아 공개 이미지 경로가 바뀌지 않는다.
	if registryOpts := s.registryOptions(); len(registryOpts) > 0 {
		opts = append(opts, client.WithResolver(docker.NewResolver(docker.ResolverOptions{
			Hosts: docker.ConfigureDefaultRegistries(registryOpts...),
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
registryOptions 는 레지스트리를 부르는 방법을 정한다 (#171, #251).

자격증명과 평문 여부는 **호스트마다 다르다.** 공개 이미지는 익명·TLS 로, 홈랩 미러는
자격증명과 평문으로 부를 수 있어야 한다 — 하나의 설정으로 뭉뚱그리면 둘 중 하나가 깨진다.
*/
func (s *containerdSandbox) registryOptions() []docker.RegistryOpt {
	var opts []docker.RegistryOpt

	if len(s.credentials) > 0 {
		opts = append(opts, docker.WithAuthorizer(docker.NewDockerAuthorizer(
			docker.WithAuthCreds(s.credentials.lookup),
		)))
	}

	if hosts := plainHTTPHosts(); len(hosts) > 0 {
		opts = append(opts, docker.WithPlainHTTP(func(host string) (bool, error) {
			_, ok := hosts[host]
			return ok, nil
		}))
	}

	return opts
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

// Warm 은 이미지를 미리 받아 둔다 (#712). 인터페이스 주석에 이유가 있다.
//
// **한 장에 시간 제한을 둔다.** 없으면 안 받아지는 이미지 하나가 기동 뒤의 미리 받기를
// 통째로 멈춰 세우고, 뒤에 선 것들은 영영 차례가 오지 않는다 — 한 번에 하나씩 받기
// 때문이다.
//
// **제출 경로보다 훨씬 길다** (#737). 여기는 아무도 안 기다린다 — 5분을 넘긴다고
// 취소하면 큰 이미지는 영영 준비되지 않는다.
func (s *containerdSandbox) Warm(ctx context.Context, image string) error {
	ctx, cancel := context.WithTimeout(ctx, warmPullTimeout)
	defer cancel()

	_, err := s.pull(ctx, image)
	return err
}
