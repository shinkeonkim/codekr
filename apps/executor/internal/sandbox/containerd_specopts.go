package sandbox

// OCI spec 에 거는 개별 옵션들 (#68).
//
// containerd 의 기본 스펙은 "돌아가는 컨테이너" 를 만들어 줄 뿐이다. 채점에 필요한
// 방어와 계측은 여기서 한 겹씩 얹는다.

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/containerd/containerd/v2/core/containers"
	"github.com/containerd/containerd/v2/pkg/oci"
	"github.com/opencontainers/runtime-spec/specs-go"
)

/*
withNumericUser 는 UID/GID 를 spec 에 바로 넣는다.

이미지의 /etc/passwd 를 보지 않는다 — 우리는 이미지에 없어도 되는 계정을 쓴다 (ADR-0003).
*/
func withNumericUser(uid, gid uint32) oci.SpecOpts {
	return func(_ context.Context, _ oci.Client, _ *containers.Container, s *oci.Spec) error {
		if s.Process == nil {
			s.Process = &specs.Process{}
		}
		s.Process.User = specs.User{UID: uid, GID: gid}
		return nil
	}
}

/*
withTmpfs 는 쓰기 가능한 tmpfs 를 붙인다.

읽기 전용 rootfs 위에서 작업 디렉터리만 열어 두는 방식이다 — 컴파일 산출물과 임시
파일이 여기 쌓이고, 컨테이너와 함께 사라진다.
*/
func withTmpfs(target string, options ...string) oci.SpecOpts {
	return func(_ context.Context, _ oci.Client, _ *containers.Container, s *oci.Spec) error {
		s.Mounts = append(s.Mounts, specs.Mount{
			Destination: target,
			Type:        "tmpfs",
			Source:      "tmpfs",
			Options:     append([]string{"nosuid", "nodev", "rw", "exec"}, options...),
		})
		return nil
	}
}

/*
withSeccompProfile 은 좁힌 프로파일을 건다 (#48).

**엔진 API 구현과 같은 파일을 쓴다.** 두 구현이 다른 프로파일로 돌면 한쪽에서 통과한
검증이 다른 쪽에서 뜻을 잃는다.
*/
func withSeccompProfile(profileJSON string) oci.SpecOpts {
	return func(_ context.Context, _ oci.Client, _ *containers.Container, s *oci.Spec) error {
		var profile specs.LinuxSeccomp
		if err := json.Unmarshal([]byte(profileJSON), &profile); err != nil {
			return fmt.Errorf("seccomp 프로파일을 읽지 못했습니다: %w", err)
		}
		if s.Linux == nil {
			s.Linux = &specs.Linux{}
		}
		s.Linux.Seccomp = &profile
		return nil
	}
}

/*
withCgroupfs 는 `/sys/fs/cgroup` 을 붙인다 (#259).

**이것이 메모리가 0 이던 진짜 이유다.** containerd 의 기본 스펙(`oci.defaultMounts`)에는
`/proc`, `/dev`, `/sys`, `/run` 만 있고 **cgroup 마운트가 아예 없다.** 도커는 넣어
주고 쿠버네티스도 CRI 플러그인이 따로 넣는데, 저수준 클라이언트로 컨테이너를 만드는
우리는 아무도 넣어 주지 않는다. 그래서 컨테이너 안의 `/sys/fs/cgroup` 은 sysfs 위의
**빈 디렉터리**였고, 래퍼 스크립트가 읽는 `memory.peak` 은 존재하지도 않았다.

CI 진단이 그대로 보여 줬다 — `cgroup: 0::/` (네임스페이스는 걸려 있다),
`entries: 0` (마운트가 없다).

타입은 `cgroup2` 가 아니라 `cgroup` 으로 둔다. runc 가 호스트가 통합 계층인지 보고
알맞게 붙여 준다 — v1 호스트에서도 같은 스펙이 돈다.

`ro` 다. 우리는 읽기만 한다. 쓰기가 열리면 컨테이너가 **자기 한도를 풀 수 있다.**
*/
func withCgroupfs() oci.SpecOpts {
	return func(_ context.Context, _ oci.Client, _ *containers.Container, s *oci.Spec) error {
		for _, m := range s.Mounts {
			if m.Destination == "/sys/fs/cgroup" {
				return nil
			}
		}
		s.Mounts = append(s.Mounts, specs.Mount{
			Destination: "/sys/fs/cgroup",
			Type:        "cgroup",
			Source:      "cgroup",
			Options:     []string{"nosuid", "noexec", "nodev", "relatime", "ro"},
		})
		return nil
	}
}

/*
withCgroupNamespace 는 컨테이너가 **자기 cgroup** 을 보게 한다 (#259).

`withCgroupfs` 와 짝이다. 네임스페이스가 없으면 위 마운트가 **호스트의 cgroup 트리
전체**로 보인다 — 컨테이너가 다른 컨테이너의 한도와 사용량을 읽게 되고, 우리가 읽으려는
`memory.peak` 은 루트에 없으므로 목적도 이루지 못한다.

user namespace 안에서 cgroup2 를 새로 마운트하려면 cgroup 네임스페이스가 필요하기도
하다. 우리는 재매핑을 쓰므로(#130) 이쪽 이유로도 있어야 한다.

도커는 cgroup v2 호스트에서 이 네임스페이스를 기본으로 켠다. **두 구현을 같은 잣대로
보려면**(#68) 여기서도 켜야 한다.
*/
func withCgroupNamespace() oci.SpecOpts {
	return func(_ context.Context, _ oci.Client, _ *containers.Container, s *oci.Spec) error {
		if s.Linux == nil {
			s.Linux = &specs.Linux{}
		}
		for _, ns := range s.Linux.Namespaces {
			if ns.Type == specs.CgroupNamespace {
				return nil
			}
		}
		s.Linux.Namespaces = append(s.Linux.Namespaces, specs.LinuxNamespace{Type: specs.CgroupNamespace})
		return nil
	}
}
