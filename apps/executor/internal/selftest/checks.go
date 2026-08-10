// Package selftest 는 샌드박스 방어가 실제로 걸려 있는지 확인하는 검사 모음이다.
//
// 왜 테스트 파일이 아니라 패키지인가: 이 검사들은 **배포된 노드에서** 돌아야 의미가 있다
// (#45). 로컬 개발 환경과 운영 노드는 컨테이너 런타임이 달라서, 로컬에서 통과했다는
// 사실이 운영에서 통과한다는 보장이 되지 못한다. 그래서 실행기 바이너리가 `--self-test`
// 로 같은 검사를 돌릴 수 있게 패키지로 뺐다. 라이브 테스트도 이 정의를 그대로 쓴다.
//
// 각 검사는 "신뢰할 수 없는 코드가 이렇게 시도한다"를 그대로 실행한다.
// 결과 요약은 docs/07_샌드박스_위협모델.md 의 검증 매트릭스에 있다.
package selftest

import (
	"fmt"
	"strings"

	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
)

// ProbeImage 는 검사에 쓰는 이미지다. 파이썬은 표준 라이브러리만으로 커널 경계를
// 들여다볼 수 있어서 검사 코드가 짧아진다.
const ProbeImage = "python:3.12-alpine"

// Check 는 방어 하나를 확인한다.
type Check struct {
	Name string
	// Code 는 샌드박스 안에서 돌릴 파이썬 코드다.
	Code string
	// Assert 는 관찰 결과가 기대한 방어를 보이는지 판단한다. 실패 사유를 error 로 돌려준다.
	Assert func(sandbox.Outcome) error
	// Spec 은 기본 스펙을 조정한다. 필요 없으면 nil.
	Spec func(*sandbox.Spec)
}

// Checks 는 모든 검사를 순서대로 돌려준다.
func Checks() []Check {
	return []Check{
		nonRootWithoutCapabilities(),
		readOnlyRootFilesystem(),
		networkBlocked(),
		runtimeSocketHidden(),
		processCountLimited(),
		executorEnvironmentHidden(),
		runawayOutputTruncated(),
	}
}

func nonRootWithoutCapabilities() Check {
	return Check{
		Name: "non-root 실행과 capability 제거",
		Code: `
import os, re
print("uid", os.getuid())
caps = dict(re.findall(r"^(Cap\w+):\s+([0-9a-f]+)", open("/proc/self/status").read(), re.M))
print("capeff", caps.get("CapEff"))
print("nonewprivs", [l.split()[1] for l in open("/proc/self/status") if l.startswith("NoNewPrivs")])
`,
		Assert: func(o sandbox.Outcome) error {
			return allOf(
				mustContain(o, "uid 10001", "non-root 로 실행되지 않았습니다"),
				// 모든 capability 를 떨궜다면 유효 집합은 0 이다.
				mustContain(o, "capeff 0000000000000000", "capability 가 남아 있습니다"),
				mustContain(o, "nonewprivs ['1']", "no-new-privileges 가 적용되지 않았습니다"),
			)
		},
	}
}

func readOnlyRootFilesystem() Check {
	return Check{
		Name: "루트 파일 시스템 읽기 전용",
		Code: `
for path in ["/etc/codekr-probe", "/usr/bin/codekr-probe", "/codekr-probe", "/root/codekr-probe"]:
    try:
        open(path, "w").write("x")
        print("WRITABLE", path)
    except OSError as e:
        print("blocked", path, type(e).__name__)
`,
		Assert: func(o sandbox.Outcome) error {
			return mustNotContain(o, "WRITABLE", "루트 파일 시스템에 쓸 수 있습니다")
		},
	}
}

func networkBlocked() Check {
	return Check{
		Name: "네트워크 차단(메타데이터 서비스 포함)",
		Code: `
import socket
socket.setdefaulttimeout(1)
for host, port in [("1.1.1.1", 53), ("169.254.169.254", 80), ("127.0.0.1", 8080), ("host.docker.internal", 80)]:
    try:
        socket.create_connection((host, port), timeout=1)
        print("REACHED", host)
    except Exception as e:
        print("blocked", host, type(e).__name__)
`,
		Assert: func(o sandbox.Outcome) error {
			return mustNotContain(o, "REACHED", "네트워크가 차단되지 않았습니다")
		},
		Spec: func(s *sandbox.Spec) { s.TimeLimitMs = 10000 },
	}
}

func runtimeSocketHidden() Check {
	return Check{
		Name: "런타임 소켓 비노출",
		Code: `
import os
for path in ["/var/run/docker.sock", "/run/docker.sock", "/run/containerd/containerd.sock", "/run/crio/crio.sock"]:
    print("EXPOSED" if os.path.exists(path) else "absent", path)
`,
		Assert: func(o sandbox.Outcome) error {
			return mustNotContain(o, "EXPOSED", "런타임 소켓이 샌드박스에 노출되었습니다")
		},
	}
}

func processCountLimited() Check {
	return Check{
		Name: "프로세스 수 제한",
		Code: `
import os
spawned = 0
try:
    for _ in range(500):
        if os.fork() == 0:
            os._exit(0)
        spawned += 1
except OSError:
    pass
print("spawned", spawned)
`,
		Assert: func(o sandbox.Outcome) error {
			// pids 제한이 걸려 있으면 500 개를 모두 만들어낼 수 없다.
			return mustNotContain(o, "spawned 500", "프로세스 수 제한이 걸리지 않았습니다")
		},
	}
}

func executorEnvironmentHidden() Check {
	return Check{
		Name: "실행기 환경 변수 비노출",
		Code: `
import os
words = ("SECRET", "TOKEN", "PASSWORD", "REDIS", "CODEKR", "DB_")
print("sensitive", [k for k in os.environ if any(w in k.upper() for w in words)])
`,
		Assert: func(o sandbox.Outcome) error {
			return mustContain(o, "sensitive []", "실행기 환경 변수가 샌드박스에 새어 나갔습니다")
		},
	}
}

func runawayOutputTruncated() Check {
	return Check{
		Name: "폭주 출력 절단",
		Code: `
import sys
line = "A" * 1024
for _ in range(50000):
    sys.stdout.write(line)
`,
		Assert: func(o sandbox.Outcome) error {
			if !o.Truncated {
				return fmt.Errorf("출력이 잘리지 않았습니다: %d bytes", len(o.Stdout))
			}
			// 상한을 크게 넘겨 메모리를 먹는 일이 없어야 한다.
			if len(o.Stdout) > 2*64*1024 {
				return fmt.Errorf("출력 상한이 지켜지지 않았습니다: %d bytes", len(o.Stdout))
			}
			return nil
		},
		Spec: func(s *sandbox.Spec) { s.TimeLimitMs = 10000 },
	}
}

func mustContain(o sandbox.Outcome, needle, message string) error {
	if strings.Contains(o.Stdout, needle) {
		return nil
	}
	return fmt.Errorf("%s: %q", message, strings.TrimSpace(o.Stdout+o.Stderr))
}

func mustNotContain(o sandbox.Outcome, needle, message string) error {
	if !strings.Contains(o.Stdout, needle) {
		return nil
	}
	return fmt.Errorf("%s: %q", message, strings.TrimSpace(o.Stdout))
}

func allOf(errs ...error) error {
	for _, err := range errs {
		if err != nil {
			return err
		}
	}
	return nil
}
