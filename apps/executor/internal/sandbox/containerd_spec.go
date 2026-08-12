package sandbox

// containerd 컨테이너의 OCI spec 을 짜는 부분 (#68).
//
// 엔진 API 구현이 `HostConfig` 로 거는 방어를 여기서는 spec 에 직접 적는다.

import (
	"context"
	"fmt"
	"runtime"
	"strconv"
	"strings"
	"time"

	"github.com/containerd/containerd/v2/client"
	"github.com/containerd/containerd/v2/core/snapshots"
	"github.com/containerd/containerd/v2/pkg/oci"
	"github.com/containerd/platforms"
	ocispec "github.com/opencontainers/image-spec/specs-go/v1"
)

func (s *containerdSandbox) create(
	ctx context.Context,
	id string,
	image client.Image,
	spec Spec,
	env []string,
	budget time.Duration,
) (client.Container, error) {
	// 컴파일 단계에 필요한 여유를 먼저 열어 둔다. 엔진 API 구현과 같은 규칙이다.
	memoryBytes := int64(startupMemoryLimitMb(spec)) * bytesPerMb
	pids := int64(128)

	opts := []oci.SpecOpts{
		// **기본 spec 도 linux 로 만든다.** 비워 두면 클라이언트의 OS(macOS)로 만들어져
		// "spec does not contain Linux section" 이 된다 — 여기서도 대상은
		// containerd 가 도는 곳이다.
		oci.WithDefaultSpecForPlatform(platformString()),
		// 이미지가 정한 환경 변수 (imageEnv 주석 참고). 툴체인 경로가 여기 들어 있다.
		oci.WithEnv(env),
		// 파일을 심을 시간을 벌기 위해 컨테이너 자체는 대기만 한다.
		oci.WithProcessArgs("sleep", fmt.Sprintf("%d", int(budget.Seconds())+5)),
		oci.WithProcessCwd(workDir),
		// **oci.WithUser 를 쓰지 않는다.** 그것은 /etc/passwd 를 읽으려고 rootfs 를
		// 임시 마운트하는데, 실행기가 권한 없이 돌면 거기서 막힌다.
		// 숫자 UID/GID 는 조회할 것이 없으므로 spec 에 바로 넣는다.
		withNumericUser(uidOf(spec), gidOf(spec)),
		// **권한을 전부 뺀다.** 남겨 둘 이유가 있는 것이 하나도 없다.
		oci.WithCapabilities(nil),
		oci.WithNoNewPrivileges,
		oci.WithRootFSReadonly(),
		oci.WithMemoryLimit(uint64(memoryBytes)),
		// **스왑도 같은 값으로 막는다.** 메모리 한도만 걸면 스왑이 있는 노드에서는
		// 넘겨 쓰고 살아남는다 — GitHub 러너에서 400MB 할당이 통과했다.
		// 엔진 API 구현도 MemorySwap 을 같이 건다.
		oci.WithMemorySwap(memoryBytes),
		oci.WithPidsLimit(pids),
		// 네트워크 네임스페이스를 새로 만든다 — 호스트 네트워크가 보이지 않는다.
		// CNI 를 붙이지 않으므로 루프백만 남는다.
		withTmpfs(workDir, "mode=1777", "size=512m"),
		withTmpfs("/tmp", "mode=1777", "size=256m"),
		withCgroupNamespace(),
		withCgroupfs(),
	}
	if s.seccompProfile != "" {
		opts = append(opts, withSeccompProfile(s.seccompProfile))
	}

	/*
		user namespace 재매핑 (#130).

		**containerd 에서는 컨테이너별로 건다.** 엔진 API 는 daemon 설정(`--userns-remap`)
		이라 파드 스펙으로 켤 수 없지만, 여기서는 spec 의 한 줄이다.

		스냅샷에도 같은 매핑을 라벨로 붙여야 한다. 그래야 rootfs 가 **idmapped mount** 로
		붙는다 — 붙지 않으면 레이어를 통째로 다시 풀어야(chown) 하고, 그것은 이미지마다
		디스크를 한 벌씩 더 먹는다.
	*/
	snapshotOpts := []snapshots.Opt{}
	mapping, remap, err := usernsMapping()
	if err != nil {
		return nil, err
	}
	if remap {
		opts = append(opts, oci.WithUserNamespace(mapping, mapping))
		snapshotOpts = append(snapshotOpts, client.WithUserNSRemapperLabels(mapping, mapping))
	}

	container, err := s.cli.NewContainer(
		ctx,
		id,
		// **스냅샷터를 명시한다.** 비워 두면 서버 기본값으로 풀리는데, 그것이 이미지를
		// 풀어 둔 스냅샷터와 다르면 부모를 찾지 못한다 ("parent snapshot ... does not exist").
		// 부모는 분명히 있는데 못 찾는 형태라 원인을 짚기 어렵다.
		client.WithSnapshotter(defaultSnapshotter),
		// 런타임 이름도 명시한다. 클라이언트가 리눅스가 아니면 기본값이 채워지지 않는다.
		client.WithRuntime(defaultRuntime, nil),
		client.WithNewSnapshot(id+"-snapshot", image, snapshotOpts...),
		client.WithNewSpec(opts...),
	)
	if err != nil {
		return nil, fmt.Errorf("컨테이너 생성 실패: %w", err)
	}
	return container, nil
}

/*
targetPlatform 은 이미지를 고를 플랫폼이다.

**언제나 linux 다.** 아키텍처는 클라이언트와 같다고 본다 — macOS(arm64)에서 개발할 때
containerd 는 arm64 리눅스 VM 안에 있고, 운영 노드도 같은 아키텍처다.
*/
// platformString 은 "linux/arm64" 형태의 문자열이다.
func platformString() string { return "linux/" + runtime.GOARCH }

func targetPlatform() platforms.MatchComparer {
	return platforms.Only(ocispec.Platform{OS: "linux", Architecture: runtime.GOARCH})
}

// uidOf/gidOf 는 "10001:10001" 같은 문자열을 숫자로 나눈다.
func uidOf(spec Spec) uint32 { return splitUser(userOf(spec), 0) }

func gidOf(spec Spec) uint32 { return splitUser(userOf(spec), 1) }

func splitUser(value string, index int) uint32 {
	parts := strings.Split(value, ":")
	if index >= len(parts) {
		return 0
	}
	parsed, err := strconv.ParseUint(parts[index], 10, 32)
	if err != nil {
		return 0
	}
	return uint32(parsed)
}
