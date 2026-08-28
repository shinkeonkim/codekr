package sandbox

import (
	"context"
	"strings"
	"testing"
)

/*
Git 문제가 **샌드박스 안에서** 도는지 본다 (#709).

**하네스만 따로 돌리면 통과한다.** root 로, 쓸 수 있는 rootfs 에서 돌기 때문이다.
그래서 이 유형은 만들어진 뒤로 한 번도 실제로 돌아 본 적이 없었고, 정답을 내도
`SYSTEM_ERROR` 가 났다 — `git config --global` 이 읽기 전용 `/` 에 `.gitconfig` 를
못 만들고 `set -eu` 로 스크립트가 끝났다.

	error: could not lock config file //.gitconfig: Read-only file system

그래서 이 시험은 **하네스가 아니라 샌드박스를 통과시킨다.** 여기서만 드러난다.
*/
func TestLiveGitHarnessRunsUnderSandbox(t *testing.T) {
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), Spec{
		Image:      "gcc:13",
		SourceFile: "commands.git",
		SourceCode: "git commit --allow-empty -q -m \"첫 커밋\"\n",
		Harness:    "git",
		Run:        []string{"sh", "run-git.sh"},
		ExtraFiles: map[string]string{
			"answer.git": "git commit --allow-empty -q -m \"정답\"\n",
			"verify.git": "git rev-list --count HEAD\n",
		},
		TimeLimitMs:    20000,
		MemoryLimitMb:  512,
		MaxOutputBytes: 65536,
	})
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}

	// **하네스가 죽으면 여기 그 오류가 담긴다.** 그것을 먼저 본다 — 출력만 견주면
	// "둘 다 비어서 같다" 로 통과할 수 있다.
	if strings.Contains(outcome.Stderr, "could not lock config file") {
		t.Fatalf("전역 git 설정을 못 씁니다. HOME 이 읽기 전용입니다: %s", outcome.Stderr)
	}
	if outcome.ExitCode != 0 {
		t.Fatalf("하네스가 %d 로 끝났습니다: %s", outcome.ExitCode, outcome.Stderr)
	}

	expected, actual, ok := strings.Cut(outcome.Stdout, "--- codekr:actual")
	if !ok {
		t.Fatalf("기대/실제 구분이 없습니다: %q", outcome.Stdout)
	}
	if strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(expected), "--- codekr:expected")) != "1" {
		t.Errorf("정답 쪽 커밋 수가 1이 아닙니다: %q", expected)
	}
	if strings.TrimSpace(actual) != "1" {
		t.Errorf("제출 쪽 커밋 수가 1이 아닙니다: %q", actual)
	}
}
