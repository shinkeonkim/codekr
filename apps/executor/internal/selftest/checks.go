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

// Check 는 방어 하나를 확인한다.
type Check struct {
	Name string
	// Code 는 샌드박스 안에서 돌릴 파이썬 코드다.
	Code string
	// Assert 는 관찰 결과가 기대한 방어를 보이는지 판단한다. 실패 사유를 error 로 돌려준다.
	Assert func(sandbox.Outcome) error
	// Spec 은 기본 스펙을 조정한다. 필요 없으면 nil.
	Spec func(*sandbox.Spec)
	/*
		RuntimeID 는 이 검사를 실어 나를 런타임이다. 비면 파이썬(ProbeRuntimeID)이다.

		**셸은 다른 언어와 위험이 같지 않다** (#456). 파이썬으로도 프로세스를 만들 수
		있지만 셸은 **그것이 언어의 본체**다 — `|` 하나가 프로세스 둘이고, 한 줄이
		fork 폭탄이 된다. 막히는 것은 같아도 **막히는 것을 확인할 필요**가 더 크다.
	*/
	RuntimeID string
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
		shellStaysInTheSameBox(),
		shellProcessCountLimited(),
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

/*
셸도 같은 상자 안이다 (#456).

전에는 검사가 전부 파이썬이었다. 파이썬이 막힌다고 셸이 막힌다는 보장은 없다 — 상자는
이미지와 무관하게 걸리지만, **그 사실을 확인한 적이 없으면 믿을 근거가 없다.**
*/
func shellStaysInTheSameBox() Check {
	return Check{
		Name:      "셸도 같은 상자 안이다",
		RuntimeID: ShellRuntimeID,
		Code: `
echo "uid $(id -u)"
echo "write $(echo x > /etc/codekr-probe 2>/dev/null && echo WRITABLE || echo denied)"
for path in /var/run/docker.sock /run/containerd/containerd.sock; do
  [ -e "$path" ] && echo "EXPOSED $path" || echo "absent $path"
done
# 셸에서 가장 먼저 해 보는 것들. 없거나 막혀야 한다.
for tool in sudo su curl wget nc; do
  command -v "$tool" >/dev/null 2>&1 && echo "TOOL $tool" || echo "no-tool $tool"
done
`,
		Assert: func(o sandbox.Outcome) error {
			return allOf(
				mustContain(o, "uid 10001", "셸이 non-root 로 실행되지 않았습니다"),
				mustContain(o, "write denied", "셸에서 루트 파일 시스템에 쓸 수 있습니다"),
				mustNotContain(o, "EXPOSED", "런타임 소켓이 셸에 노출되었습니다"),
				mustNotContain(o, "TOOL sudo", "이미지에 sudo 가 들어 있습니다"),
				mustNotContain(o, "TOOL curl", "이미지에 curl 이 들어 있습니다"),
			)
		},
	}
}

/*
셸에서 프로세스를 여럿 띄워도 제한이 걸린다 (#456).

셸은 프로세스를 **쉽게** 여럿 만든다. 파이썬 판(processCountLimited)은 `os.fork` 를
부르지만, 셸에서는 파이프 한 줄이나 `&` 하나가 같은 일을 한다.
*/
func shellProcessCountLimited() Check {
	return Check{
		Name:      "셸의 프로세스 수 제한",
		RuntimeID: ShellRuntimeID,
		Code: `
spawned=0
for i in $(seq 1 500); do
  sleep 5 &
  spawned=$((spawned + 1))
  [ $((spawned % 32)) -eq 0 ] && echo "spawned $spawned"
done
echo "spawned-all $spawned"
`,
		Assert: func(o sandbox.Outcome) error {
			return allOf(
				// **먼저 정말 띄웠는지 본다.** 첫 줄에서 죽어도 "500 이 없다" 는 참이 되므로,
				// 그것만 보면 아무것도 확인하지 않고 통과할 수 있다.
				mustContain(o, "spawned 32", "셸이 프로세스를 만들지도 못했습니다"),
				// 제한이 걸리면 여기까지 오지 못한다. 실측: 128 제한에서 96 까지 찍히고 멈춘다.
				mustNotContain(o, "spawned-all", "셸에서 프로세스 수 제한이 걸리지 않았습니다"),
			)
		},
		// 500번 도는 동안 기본 5초를 넘길 수 있다. 제한을 확인하는 것이 목적이다.
		Spec: func(s *sandbox.Spec) { s.TimeLimitMs = 15000 },
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
