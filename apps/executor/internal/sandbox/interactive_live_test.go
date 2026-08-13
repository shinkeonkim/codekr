package sandbox

import (
	"context"
	"testing"
)

const guessInteractor = `import sys

secret = int(open("/work/case.txt").read().strip())
for _ in range(20):
    line = sys.stdin.readline()
    if not line:
        sys.exit(3)
    guess = int(line.split()[-1])
    if guess == secret:
        print("correct", flush=True)
        sys.exit(0)
    print("higher" if guess < secret else "lower", flush=True)
print("질의 횟수를 넘겼습니다", file=sys.stderr)
sys.exit(1)
`

const binarySearchSolver = `import sys

low, high = 1, 100
while True:
    mid = (low + high) // 2
    print(mid, flush=True)
    answer = sys.stdin.readline().strip()
    if answer == "correct":
        break
    if answer == "higher":
        low = mid + 1
    else:
        high = mid - 1
`

/*
인터랙티브 문제 (#474).

**도는 중에 주고받는다.** 파이프 둘을 파서 채점 코드와 제출을 서로 물리는데, 그 자리에
숨은 함정이 둘 있다 — FIFO 는 **여는 것 자체가 상대를 기다리고**, 자식이 물려받은
파일 서술자를 닫지 않으면 **EOF 가 영원히 오지 않는다.** 둘 다 실제로 겪었다.
*/
func TestLiveInteractiveDialogue(t *testing.T) {
	box := newLiveSandbox(t)

	cases := []struct {
		name     string
		solver   string
		exitCode int
	}{
		{"맞히면 0", binarySearchSolver, 0},
		// 스무 번을 넘기면 채점 코드가 1 로 끝낸다.
		{"질의를 넘기면 1", "import sys\nfor _ in range(30):\n    print(1, flush=True)\n    sys.stdin.readline()\n", 1},
		// **아무것도 안 보내고 끝나면 교착이다** — 시간 초과와 다른 말을 해야 한다.
		{"아무것도 안 보내면 3", "pass\n", 3},
	}

	for _, testcase := range cases {
		t.Run(testcase.name, func(t *testing.T) {
			outcome, err := box.Run(context.Background(), Spec{
				Image:      "python:3.13-alpine",
				SourceFile: "main.py",
				SourceCode: testcase.solver,
				Harness:    "interactive",
				Run:        []string{"sh", "run-interactive.sh"},
				ExtraFiles: map[string]string{
					"interactor.py": guessInteractor,
					"case.txt":      "42\n",
				},
				TimeLimitMs:    15000,
				MemoryLimitMb:  512,
				MaxOutputBytes: 65536,
			})
			if err != nil {
				t.Fatalf("실행 실패: %v", err)
			}
			if outcome.ExitCode != testcase.exitCode {
				t.Fatalf("종료 코드가 판정이다: %d 여야 하는데 %d (stderr=%q)",
					testcase.exitCode, outcome.ExitCode, outcome.Stderr)
			}
		})
	}
}
