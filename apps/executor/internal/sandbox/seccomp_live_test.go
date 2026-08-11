package sandbox

import (
	"context"
	"os"
	"strings"
	"testing"
)

// 좁힌 seccomp 프로파일이 실제로 막는지 확인한다 (#48).
//
// **프로파일이 회귀하면 여기서 드러난다.** 허용 목록을 넓히다 보면 어느 순간
// 기본 프로파일과 다를 바 없어지는데, 그 사실은 아무도 눈치채지 못한다.
//
// 런타임 이미지와 컨테이너 런타임이 필요하므로 CODEKR_SANDBOX_TEST=1 일 때만 돈다.
func TestLiveNarrowedSeccompBlocksDangerousSyscalls(t *testing.T) {
	profile := os.Getenv("CODEKR_SECCOMP_PROFILE")
	if profile == "" {
		t.Skip("CODEKR_SECCOMP_PROFILE 이 있어야 의미가 있는 시험이다")
	}
	if os.Getenv("CODEKR_SANDBOX_TEST") != "1" {
		t.Skip("CODEKR_SANDBOX_TEST=1 일 때만 실행한다 (컨테이너 런타임 필요)")
	}

	box, err := New(os.Getenv("CODEKR_SANDBOX_RUNTIME"), profile)
	if err != nil {
		t.Fatalf("샌드박스 생성 실패: %v", err)
	}
	t.Cleanup(func() { _ = box.Close() })

	// ctypes 로 직접 syscall 을 부른다. 파이썬 표준 라이브러리에는 이들 대부분에
	// 대한 창구가 없고, 있는 것(os.unshare)은 버전에 따라 없을 수 있다.
	const code = `
import ctypes, errno
libc = ctypes.CDLL(None, use_errno=True)
# (이름, 번호) — aarch64 기준. x86_64 와 번호가 다르므로 os.uname 으로 고른다.
import platform
arm = platform.machine() in ("aarch64", "arm64")
probes = {
    "ptrace":           117 if arm else 101,
    "process_vm_readv": 270 if arm else 310,
    "userfaultfd":      282 if arm else 323,
    "io_uring_setup":   425,
    "keyctl":           219 if arm else 250,
    "bpf":              280 if arm else 321,
    "setns":            268 if arm else 308,
}
for name, nr in probes.items():
    ctypes.set_errno(0)
    libc.syscall(nr, 0, 0, 0, 0, 0, 0)
    print(name, "BLOCKED" if ctypes.get_errno() == errno.EPERM else "ALLOWED")
`
	outcome, err := box.Run(context.Background(), Spec{
		Image:          "python:3.12-alpine",
		SourceFile:     "main.py",
		SourceCode:     code,
		Run:            []string{"python3", "main.py"},
		TimeLimitMs:    10000,
		MemoryLimitMb:  256,
		MaxOutputBytes: 64 * 1024,
	})
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}

	for _, line := range strings.Split(strings.TrimSpace(outcome.Stdout), "\n") {
		if strings.HasSuffix(line, "ALLOWED") {
			t.Errorf("좁힌 프로파일이 막아야 할 syscall 이 열려 있습니다: %s", line)
		}
	}
	if !strings.Contains(outcome.Stdout, "ptrace BLOCKED") {
		t.Fatalf("시험이 실제로 돌지 않았습니다: stdout=%q stderr=%q", outcome.Stdout, outcome.Stderr)
	}
}
