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
	// 프로파일 경로를 환경에서 받는다. 비우면 런타임 기본 프로파일로 돈다 (#48).
	box, err := NewContainerSandbox(os.Getenv("CODEKR_SECCOMP_PROFILE"))
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
