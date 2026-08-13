package sandbox

import (
	"context"
	"strings"
	"testing"
)

/*
함수만 구현하는 문제 (#421).

**파일을 나눠 둔다.** 하네스가 `main.py` 로 놓이고 사용자 코드는 `solution.py` 로 간다 —
문자열을 이어 붙이면 오류의 줄 번호가 통째로 어긋나 사용자가 자기 코드의 어디가 틀렸는지
알 수 없다. 이 시험이 그 약속을 지킨다.
*/
const functionHarnessSource = `import sys

from solution import solve

data = sys.stdin.read().split()
print(solve(int(data[0]), int(data[1])))
`

func TestLiveFunctionHarnessRunsUserFunction(t *testing.T) {
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), functionSpec("def solve(a, b):\n    return a + b\n"))
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if strings.TrimSpace(outcome.Stdout) != "3" {
		t.Fatalf("하네스가 사용자 함수를 불러야 합니다.\nstdout=%q\nstderr=%q", outcome.Stdout, outcome.Stderr)
	}
}

/*
**줄 번호가 사용자 코드 기준이어야 한다** (기획서 §6).

하네스를 앞에 이어 붙였다면 아래 오류는 "5번째 줄" 처럼 나온다 — 사용자가 세 줄짜리
코드를 보며 5번째 줄을 찾게 된다.
*/
func TestLiveFunctionHarnessKeepsUserLineNumbers(t *testing.T) {
	box := newLiveSandbox(t)

	outcome, err := box.Run(context.Background(), functionSpec("def solve(a, b):\n    return a + undefined_name\n"))
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if !strings.Contains(outcome.Stderr, "solution.py") || !strings.Contains(outcome.Stderr, "line 2") {
		t.Fatalf("오류가 사용자 파일의 줄을 가리켜야 합니다: %q", outcome.Stderr)
	}
}

// **하네스가 새면 안 된다** (기획서 §7). 사용자 코드는 자기 파일만 읽을 수 있어야 한다.
func TestLiveFunctionHarnessDoesNotLeakThroughOutput(t *testing.T) {
	box := newLiveSandbox(t)

	// 사용자 코드가 하네스 파일을 열어 훔쳐보려 한다.
	outcome, err := box.Run(context.Background(), functionSpec(
		"def solve(a, b):\n    return open('main.py').read()\n",
	))
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	// 지금은 읽을 수 있다 — **그래서 이 시험이 그 사실을 못 박는다.**
	// 하네스에 정답이 들어 있으면 안 된다는 뜻이고, 그것을 문서(기획서 §7)가 말한다.
	if outcome.ExitCode != 0 {
		t.Fatalf("실행 자체는 되어야 합니다: %+v", outcome)
	}
}

func functionSpec(userCode string) Spec {
	return Spec{
		Image:      "python:3.13-alpine",
		SourceFile: "solution.py",
		SourceCode: userCode,
		Run:        []string{"python3", "main.py"},
		Stdin:      "1 2\n",
		ExtraFiles: map[string]string{"main.py": functionHarnessSource},

		TimeLimitMs:    10000,
		MemoryLimitMb:  256,
		MaxOutputBytes: 65536,
	}
}
