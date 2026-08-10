package sandbox

import (
	"context"
	"os"
	"strings"
	"testing"
)

/*
재매핑이 실제로 걸렸는지 확인한다 (#130).

**방어가 걸렸다고 적는 것과 걸린 것을 보는 것은 다르다.** 나머지 검사는 재매핑을 켜도
그대로 통과하므로, 그것만으로는 켜졌는지 알 수 없다 — 환경 변수를 잘못 읽어 조용히
꺼져 있어도 전부 초록이다.

`/proc/self/uid_map` 은 그 프로세스가 보는 매핑을 그대로 적어 준다. 재매핑이 없으면
`0 0 4294967295`(항등 매핑) 한 줄이다.
*/
func TestLiveUserNamespaceRemapping(t *testing.T) {
	offset := os.Getenv(usernsOffsetEnv)
	if offset == "" {
		t.Skipf("%s 가 설정된 환경에서만 실행한다", usernsOffsetEnv)
	}
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), Spec{
		Image:          "python:3.12-alpine",
		SourceFile:     "main.py",
		SourceCode:     "print(open('/proc/self/uid_map').read() + open('/proc/self/gid_map').read(), end='')\n",
		Run:            []string{"python3", "main.py"},
		TimeLimitMs:    5000,
		MemoryLimitMb:  256,
		MaxOutputBytes: 4096,
	})
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}

	// "0 100000 65536" 형태여야 한다. 공백 개수는 커널이 정렬해 주므로 필드로 나눠 본다.
	lines := strings.Split(strings.TrimSpace(outcome.Stdout), "\n")
	if len(lines) < 2 {
		t.Fatalf("uid_map/gid_map 을 읽지 못했습니다: %q (stderr=%q)", outcome.Stdout, outcome.Stderr)
	}
	for _, line := range lines {
		fields := strings.Fields(line)
		if len(fields) != 3 {
			t.Fatalf("매핑 형식이 예상과 다릅니다: %q", line)
		}
		if fields[0] != "0" || fields[1] != offset {
			t.Errorf("재매핑이 걸리지 않았습니다: %q (기대: 컨테이너 0 → 호스트 %s)", line, offset)
		}
	}
}
