package sandbox

import (
	"context"
	"os"
	"strings"
	"testing"
)

// 실제 컨테이너 런타임과 런타임 이미지가 필요한 테스트다.
// 기본 `go test ./...` 에서는 건너뛰고, CODEKR_SANDBOX_TEST=1 일 때만 실행한다.
func newLiveSandbox(t *testing.T) Sandbox {
	t.Helper()
	if os.Getenv("CODEKR_SANDBOX_TEST") != "1" {
		t.Skip("CODEKR_SANDBOX_TEST=1 일 때만 실행한다 (컨테이너 런타임 필요)")
	}
	// **어느 구현을 쓸지도 환경이 정한다** (#68). 통과 기준이 같아야 두 구현을 견줄 수 있다.
	// 프로파일 경로도 환경에서 받는다. 비우면 런타임 기본 프로파일로 돈다 (#48).
	box, err := New(os.Getenv("CODEKR_SANDBOX_RUNTIME"), os.Getenv("CODEKR_SECCOMP_PROFILE"))
	if err != nil {
		t.Fatalf("샌드박스 생성 실패: %v", err)
	}
	t.Cleanup(func() { _ = box.Close() })
	return box
}

func pythonSpec(code string) Spec {
	return Spec{
		Image:            "python:3.12-alpine",
		SourceFile:       "main.py",
		SourceCode:       code,
		Run:              []string{"python3", "main.py"},
		TimeLimitMs:      2000,
		MemoryLimitMb:    256,
		CompileTimeoutMs: 15000,
		MaxOutputBytes:   65536,
	}
}

func TestLiveRunsPythonAndCapturesStdout(t *testing.T) {
	box := newLiveSandbox(t)
	spec := pythonSpec("import sys\nprint(sum(map(int, sys.stdin.read().split())))")
	spec.Stdin = "1 2\n"

	outcome, err := box.Run(context.Background(), spec)

	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if strings.TrimSpace(outcome.Stdout) != "3" {
		t.Fatalf("stdout 이 기대와 다릅니다: %q (stderr=%q)", outcome.Stdout, outcome.Stderr)
	}
	if outcome.ExitCode != 0 {
		t.Fatalf("종료 코드가 0 이 아닙니다: %d", outcome.ExitCode)
	}
}

/*
계측이 실제로 채워지는지 (#259).

**0 은 "안 썼다" 가 아니라 "못 읽었다" 였다.** 홈랩에서 모든 제출의 메모리가 0 으로
기록됐는데, 컨테이너가 자기 cgroup 이 아니라 호스트 루트를 보고 있어서 읽을 파일이
없었던 것이다. 값이 화면에 보이는 항목이므로(#34, #84) 여기서 못 박는다.

시간도 함께 본다 — 같은 줄에서 오고, 하나만 살아 있는 경우를 구분할 수 있어야 한다.
*/
func TestLiveReportsRuntimeAndMemory(t *testing.T) {
	box := newLiveSandbox(t)
	// 눈에 띄게 쓰도록 한 번에 여러 MB 를 잡는다. 너무 적으면 측정 잡음에 묻힌다.
	spec := pythonSpec("data = bytearray(24 * 1024 * 1024)\nprint(len(data))")

	outcome, err := box.Run(context.Background(), spec)

	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if outcome.MemoryKb <= 0 {
		// **0 이면 왜 0 인지까지 남긴다.** 계측이 실패하는 이유는 "안 썼다" 가 아니라
		// "읽을 파일이 없다" 이고, 그것은 컨테이너가 보는 cgroup 이 무엇이냐에 달렸다.
		// 그 사실을 함께 찍지 않으면 CI 로그만 보고 다음 수를 정할 수 없다.
		t.Errorf("메모리 사용량이 기록되지 않았습니다: %dKB\n컨테이너가 본 것:\n%s",
			outcome.MemoryKb, cgroupView(t, box))
	}
	if outcome.RuntimeMs <= 0 {
		t.Errorf("실행 시간이 기록되지 않았습니다: %dms", outcome.RuntimeMs)
	}
}

func TestLiveDetectsTimeLimitExceeded(t *testing.T) {
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), pythonSpec("while True:\n    pass"))

	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if !outcome.TimedOut {
		t.Fatalf("무한 루프가 시간 초과로 판정되지 않았습니다: %+v", outcome)
	}
}

func TestLiveDetectsMemoryLimitExceeded(t *testing.T) {
	box := newLiveSandbox(t)
	spec := pythonSpec("x = bytearray(400 * 1024 * 1024)\nprint(len(x))")
	spec.MemoryLimitMb = 64
	spec.TimeLimitMs = 5000

	outcome, err := box.Run(context.Background(), spec)

	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if !outcome.OutOfMemory {
		t.Fatalf("메모리 초과로 판정되지 않았습니다: %+v", outcome)
	}
}

func TestLiveDetectsRuntimeError(t *testing.T) {
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), pythonSpec("raise ValueError('boom')"))

	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if outcome.ExitCode == 0 || outcome.TimedOut {
		t.Fatalf("런타임 오류로 판정되지 않았습니다: %+v", outcome)
	}
	if !strings.Contains(outcome.Stderr, "ValueError") {
		t.Fatalf("stderr 가 수집되지 않았습니다: %q", outcome.Stderr)
	}
}

func TestLiveDetectsCompileError(t *testing.T) {
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), Spec{
		Image:            "gcc:13",
		SourceFile:       "main.cpp",
		SourceCode:       "int main() { return notdefined; }",
		Compile:          []string{"g++", "-O2", "-std=c++17", "-o", "main", "main.cpp"},
		Run:              []string{"./main"},
		TimeLimitMs:      2000,
		MemoryLimitMb:    256,
		CompileTimeoutMs: 15000,
		MaxOutputBytes:   65536,
	})

	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if !outcome.CompileFailed {
		t.Fatalf("컴파일 실패로 판정되지 않았습니다: %+v", outcome)
	}
}

func TestLiveBlocksNetworkAccess(t *testing.T) {
	box := newLiveSandbox(t)
	code := "import socket\nsocket.create_connection(('1.1.1.1', 53), timeout=1)\nprint('connected')"

	outcome, err := box.Run(context.Background(), pythonSpec(code))

	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if strings.Contains(outcome.Stdout, "connected") {
		t.Fatalf("샌드박스에서 네트워크가 차단되지 않았습니다: %+v", outcome)
	}
}

// cgroupView 는 컨테이너 안에서 cgroup 이 어떻게 보이는지 그대로 찍어 온다 (#259).
func cgroupView(t *testing.T, box Sandbox) string {
	t.Helper()
	probe := pythonSpec(`
import os
print("cgroup:", open("/proc/self/cgroup").read().strip())
try:
    names = sorted(os.listdir("/sys/fs/cgroup"))
    print("entries:", len(names), names[:12])
    for name in ("memory.peak", "memory.current", "memory.max"):
        path = "/sys/fs/cgroup/" + name
        print(name, os.path.exists(path), os.access(path, os.R_OK))
except OSError as error:
    print("listdir 실패:", error)
`)
	outcome, err := box.Run(context.Background(), probe)
	if err != nil {
		return "진단 실행 실패: " + err.Error()
	}
	return outcome.Stdout + outcome.Stderr
}
