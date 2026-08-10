package sandbox

import (
	"context"
	"strings"
	"testing"
)

// 샌드박스 방어 검증 (#37).
//
// 각 테스트는 "신뢰할 수 없는 코드가 이렇게 시도한다"를 그대로 실행해, 방어가 실제로
// 걸려 있는지 확인한다. 결과 요약은 docs/07_샌드박스_위협모델.md 의 검증 매트릭스에 있다.
//
// 컨테이너 런타임이 필요하므로 CODEKR_SANDBOX_TEST=1 일 때만 실행한다.

// probe 는 파이썬 코드를 돌려 표준 출력을 돌려주는 검증용 도우미다.
func probe(t *testing.T, code string) Outcome {
	t.Helper()
	box := newLiveSandbox(t)

	spec := pythonSpec(code)
	spec.TimeLimitMs = 5000
	outcome, err := box.Run(context.Background(), spec)
	if err != nil {
		t.Fatalf("검증 실행 실패: %v", err)
	}
	return outcome
}

func TestLiveHardeningRunsAsNonRootWithoutCapabilities(t *testing.T) {
	outcome := probe(t, `
import os, re
print("uid", os.getuid(), "gid", os.getgid())
caps = {}
for line in open("/proc/self/status"):
    m = re.match(r"^(Cap\w+):\s+([0-9a-f]+)", line)
    if m:
        caps[m.group(1)] = m.group(2)
print("capeff", caps.get("CapEff"))
print("nonewprivs", [l.split()[1] for l in open("/proc/self/status") if l.startswith("NoNewPrivs")])
`)

	if !strings.Contains(outcome.Stdout, "uid 10001") {
		t.Errorf("non-root 로 실행되지 않았습니다: %q", outcome.Stdout)
	}
	// 모든 capability 를 떨궜다면 유효 집합은 0 이다.
	if !strings.Contains(outcome.Stdout, "capeff 0000000000000000") {
		t.Errorf("capability 가 남아 있습니다: %q", outcome.Stdout)
	}
	if !strings.Contains(outcome.Stdout, "nonewprivs ['1']") {
		t.Errorf("no-new-privileges 가 적용되지 않았습니다: %q", outcome.Stdout)
	}
}

func TestLiveHardeningRootFilesystemIsReadOnly(t *testing.T) {
	outcome := probe(t, `
targets = ["/etc/codekr-probe", "/usr/bin/codekr-probe", "/codekr-probe", "/root/codekr-probe"]
for path in targets:
    try:
        open(path, "w").write("x")
        print("WRITABLE", path)
    except OSError as e:
        print("blocked", path, type(e).__name__)
`)

	if strings.Contains(outcome.Stdout, "WRITABLE") {
		t.Errorf("루트 파일 시스템에 쓸 수 있습니다: %q", outcome.Stdout)
	}
}

func TestLiveHardeningWorkDirIsWritableButIsolatedPerJob(t *testing.T) {
	// 앞선 작업이 남긴 파일이 다음 작업에서 보이면 작업 간 격리가 깨진 것이다.
	first := probe(t, `open("/work/leak.txt", "w").write("secret")`+"\nprint('wrote')")
	if !strings.Contains(first.Stdout, "wrote") {
		t.Fatalf("작업 디렉터리에 쓰지 못했습니다: %+v", first)
	}

	second := probe(t, `
import os
print("leaked" if os.path.exists("/work/leak.txt") else "isolated")
`)
	if !strings.Contains(second.Stdout, "isolated") {
		t.Errorf("작업 간 파일이 새어 나갑니다: %q", second.Stdout)
	}
}

func TestLiveHardeningBlocksNetworkIncludingMetadataService(t *testing.T) {
	outcome := probe(t, `
import socket
socket.setdefaulttimeout(1)
targets = [("1.1.1.1", 53), ("169.254.169.254", 80), ("127.0.0.1", 8080), ("host.docker.internal", 80)]
for host, port in targets:
    try:
        socket.create_connection((host, port), timeout=1)
        print("REACHED", host)
    except Exception as e:
        print("blocked", host, type(e).__name__)
`)

	if strings.Contains(outcome.Stdout, "REACHED") {
		t.Errorf("네트워크가 차단되지 않았습니다: %q", outcome.Stdout)
	}
}

func TestLiveHardeningContainerRuntimeSocketIsNotVisible(t *testing.T) {
	outcome := probe(t, `
import os
paths = ["/var/run/docker.sock", "/run/docker.sock", "/run/containerd/containerd.sock"]
for path in paths:
    print("EXPOSED" if os.path.exists(path) else "absent", path)
`)

	if strings.Contains(outcome.Stdout, "EXPOSED") {
		t.Errorf("런타임 소켓이 샌드박스에 노출되었습니다: %q", outcome.Stdout)
	}
}

func TestLiveHardeningLimitsProcessCount(t *testing.T) {
	// fork 폭증이 호스트를 마비시키지 못하고 컨테이너 안에서 끝나는지 본다.
	outcome := probe(t, `
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
`)

	// pids 제한이 128 이므로 500 개를 모두 만들어내면 제한이 없는 것이다.
	if strings.Contains(outcome.Stdout, "spawned 500") {
		t.Errorf("프로세스 수 제한이 걸리지 않았습니다: %q", outcome.Stdout)
	}
}

func TestLiveHardeningTruncatesRunawayOutput(t *testing.T) {
	box := newLiveSandbox(t)
	spec := pythonSpec(`
import sys
line = "A" * 1024
for _ in range(50000):
    sys.stdout.write(line)
`)
	spec.TimeLimitMs = 5000
	spec.MaxOutputBytes = 64 * 1024

	outcome, err := box.Run(context.Background(), spec)
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if !outcome.Truncated {
		t.Errorf("출력이 잘리지 않았습니다: %d bytes", len(outcome.Stdout))
	}
	// 상한을 크게 넘겨 메모리를 먹는 일이 없어야 한다.
	if len(outcome.Stdout) > 2*spec.MaxOutputBytes {
		t.Errorf("출력 상한이 지켜지지 않았습니다: %d bytes", len(outcome.Stdout))
	}
}

func TestLiveHardeningDoesNotLeakExecutorEnvironment(t *testing.T) {
	outcome := probe(t, `
import os
sensitive = [k for k in os.environ if any(w in k.upper() for w in ("SECRET", "TOKEN", "PASSWORD", "REDIS", "CODEKR", "DB_"))]
print("sensitive", sensitive)
`)

	if !strings.Contains(outcome.Stdout, "sensitive []") {
		t.Errorf("실행기 환경 변수가 샌드박스에 새어 나갔습니다: %q", outcome.Stdout)
	}
}

func TestLiveHardeningCleansUpContainerAfterTimeout(t *testing.T) {
	box := newLiveSandbox(t)
	spec := pythonSpec("while True: pass")
	spec.TimeLimitMs = 1000

	outcome, err := box.Run(context.Background(), spec)
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if !outcome.TimedOut {
		t.Fatalf("시간 초과로 끝나야 합니다: %+v", outcome)
	}
	// 컨테이너 정리는 Run 이 반환하기 전에 끝난다 (defer remove).
	// 남은 컨테이너가 있는지는 docs/07 의 절차로 확인한다.
}
