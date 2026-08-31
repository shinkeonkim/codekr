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
/*
Git 문제가 쓰는 이미지. **정의 파일과 같아야 한다** (#739).

전에는 `gcc:13` 이 박혀 있었다 — 시험이 운영과 다른 이미지를 확인하면, 이미지를 바꾼
날 그 시험은 **아무것도 안 지킨다.**
*/
const gitImage = "codekr-runtime-git:2.49"

func TestLiveGitHarnessRunsUnderSandbox(t *testing.T) {
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), Spec{
		Image:      gitImage,
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

/*
**제출이 실패하는 것은 오답이지 채점 오류가 아니다** (#715).

전에는 제출의 명령 하나가 실패하면 하네스가 통째로 끝나 `--- codekr:actual` 이 아예
안 나왔다. 견줄 것이 없으니 채점기는 판정을 못 내고 사용자는 "채점 실패" 를 봤다 —
`--allow-empty` 를 안 붙인 것뿐인데.
*/
func TestLiveGitFailedSubmissionIsWrongAnswerNotError(t *testing.T) {
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), Spec{
		Image:      gitImage,
		SourceFile: "commands.git",
		// 빈 커밋이라 git 이 거부한다. 사용자가 흔히 겪는 실패다.
		SourceCode: "git commit --amend -q -m \"고친 메시지\"\n",
		Harness:    "git",
		Run:        []string{"sh", "run-git.sh"},
		ExtraFiles: map[string]string{
			"seed.git":   "git commit --allow-empty -q -m \"첫\"\n",
			"answer.git": "git commit --amend -q --allow-empty -m \"고친 메시지\"\n",
			"verify.git": "git log -1 --format=%s\n",
		},
		TimeLimitMs:    20000,
		MemoryLimitMb:  512,
		MaxOutputBytes: 65536,
	})
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}

	if outcome.ExitCode != 0 {
		t.Fatalf("제출이 실패해도 하네스는 0 으로 끝나야 합니다 (그래야 판정이 난다): %d", outcome.ExitCode)
	}
	expected, actual, ok := strings.Cut(outcome.Stdout, "--- codekr:actual")
	if !ok {
		t.Fatalf("제출이 실패해도 actual 이 나와야 합니다: %q", outcome.Stdout)
	}
	if strings.TrimSpace(actual) == strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(expected), "--- codekr:expected")) {
		t.Errorf("틀린 제출인데 기대값과 같습니다: %q", outcome.Stdout)
	}
	// **왜 실패했는지가 보여야 한다.** 그것 말고는 사용자가 알 방법이 없다.
	if !strings.Contains(outcome.Stderr, "명령이 실패했습니다") {
		t.Errorf("실패한 명령이 stderr 에 없습니다: %q", outcome.Stderr)
	}
}

/*
**시드는 셸을 쓰고, 제출은 못 쓴다** (#716).

시드까지 `git` 만 받으면 작업 트리에 파일을 만들 수 없어 스테이징·복원 같은 것을
낼 수 없다. 그런데 제한이 필요한 쪽은 제출뿐이다 — 시드는 문제가 소유한다.
*/
func TestLiveGitSeedMayUseShellButSubmissionMayNot(t *testing.T) {
	box := newLiveSandbox(t)

	// **`\\n` 이다.** Go 에서 `\n` 을 쓰면 진짜 개행이 되어 `printf 'hello` 로 잘리고,
	// 따옴표가 안 닫혀 하네스가 문법 오류로 죽는다(실제로 그랬다).
	seed := `printf 'hello\n' > a.txt` + "\n" +
		"git add a.txt\n" +
		`git commit -q -m "처음"` + "\n" +
		`printf 'changed\n' > a.txt` + "\n" +
		"git add a.txt\n"

	run := func(submission string) Outcome {
		t.Helper()
		outcome, err := box.Run(context.Background(), Spec{
			Image:      gitImage,
			SourceFile: "commands.git",
			SourceCode: submission,
			Harness:    "git",
			Run:        []string{"sh", "run-git.sh"},
			ExtraFiles: map[string]string{
				"seed.git":   seed,
				"answer.git": "git restore --staged a.txt\n",
				"verify.git": "git status --porcelain\n",
			},
			TimeLimitMs:    20000,
			MemoryLimitMb:  512,
			MaxOutputBytes: 65536,
		})
		if err != nil {
			t.Fatalf("실행 실패: %v", err)
		}
		return outcome
	}

	// 시드가 파일을 만들고 스테이징했으므로 상태를 물을 수 있다.
	right := run("git restore --staged a.txt\n")
	expected, actual, ok := strings.Cut(right.Stdout, "--- codekr:actual")
	if !ok {
		t.Fatalf("구분이 없습니다: %q", right.Stdout)
	}
	want := strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(expected), "--- codekr:expected"))
	if want == "" {
		t.Fatalf("시드가 파일 상태를 못 만들었습니다 — 기대값이 비었습니다: %q", right.Stdout)
	}
	if strings.TrimSpace(actual) != want {
		t.Errorf("정답인데 기대값과 다릅니다: 기대 %q 실제 %q", want, strings.TrimSpace(actual))
	}

	// 제출에 셸 명령을 쓰면 거부된다 — 그리고 그것은 오답이지 채점 오류가 아니다.
	blocked := run("rm -rf /work\n")
	if blocked.ExitCode != 0 {
		t.Fatalf("거부해도 하네스는 0 으로 끝나야 합니다: %d", blocked.ExitCode)
	}
	if !strings.Contains(blocked.Stderr, "git 명령만 쓸 수 있습니다") {
		t.Errorf("셸 명령이 거부되지 않았습니다: %q", blocked.Stderr)
	}
}
