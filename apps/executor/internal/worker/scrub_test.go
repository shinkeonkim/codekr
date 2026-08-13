package worker

import (
	"strings"
	"testing"
)

/*
하네스가 새면 안 된다 (#445, #421).

**샌드박스가 막아 주지 않는다** — 사용자 코드와 하네스는 같은 프로세스에서 돈다.
여기서 보는 것은 그중 하나, **오류 출력으로 새는 길**이다.

아래 트레이스백은 실제로 받은 것이다 (python:3.13-alpine, `docker run` 으로 확인).
*/
const realTraceback = `Traceback (most recent call last):
  File "<string>", line 1, in <module>
    import os,sys;p="main.py";src=open(p).read();os.remove(p);exec(compile(src,p,"exec"))
                                                              ~~~~^^^^^^^^^^^^^^^^^^^^^^
  File "main.py", line 5, in <module>
    print(solve(int(data[0]), int(data[1])) == EXPECTED[0])
  File "/work/solution.py", line 3, in solve
    return a + b + undefined_name
                   ^^^^^^^^^^^^^^
NameError: name 'undefined_name' is not defined`

func TestScrubHidesHarnessButKeepsTheCause(t *testing.T) {
	got := scrub(realTraceback, "main.py")

	// **하네스의 코드가 한 조각도 나가면 안 된다.** 정답의 일부나 판정 방식이 들어간다.
	for _, secret := range []string{"EXPECTED", "print(solve(", "os.remove"} {
		if strings.Contains(got, secret) {
			t.Errorf("하네스가 샜다: %q\n%s", secret, got)
		}
	}

	// **원인은 남아야 한다.** 감추기만 하면 "왜 안 되는지 모르는 상태" 가 된다.
	for _, keep := range []string{
		"NameError: name 'undefined_name' is not defined",
		"return a + b + undefined_name",
		`File "solution.py", line 3, in solve`, // 경로는 지우고 줄 번호는 그대로
	} {
		if !strings.Contains(got, keep) {
			t.Errorf("원인이 사라졌다: %q\n%s", keep, got)
		}
	}

	if !strings.Contains(got, "문제의 실행 코드에서 오류가 났습니다") {
		t.Errorf("하네스에서 났다는 사실조차 남지 않았다:\n%s", got)
	}
}

func TestScrubRemovesWorkDirPath(t *testing.T) {
	// 컨테이너 안의 경로는 사용자에게 뜻이 없고, 그 자체가 우리 내부 구조다.
	got := scrub(`  File "/work/solution.py", line 3`, "")

	if strings.Contains(got, "/work/") {
		t.Errorf("경로가 남았다: %s", got)
	}
	if !strings.Contains(got, "solution.py") {
		t.Errorf("파일 이름까지 지웠다: %s", got)
	}
}

func TestScrubKeepsPlainOutputUntouched(t *testing.T) {
	// 하네스가 없는 문제(지금의 stdio 문제)는 아무것도 바뀌지 않아야 한다.
	const plain = "Traceback (most recent call last):\n  File \"main.py\", line 1\nZeroDivisionError"

	if got := scrub(plain, ""); got != plain {
		t.Errorf("건드리지 말아야 할 출력이 바뀌었다:\n%s", got)
	}
}

func TestScrubAnnouncesHarnessOnce(t *testing.T) {
	// 프레임이 여럿이어도 같은 말을 반복하지 않는다 — 읽히지 않는 안내가 된다.
	input := "  File \"main.py\", line 5, in <module>\n    a()\n  File \"main.py\", line 2, in a\n    b()\nValueError: x"

	got := scrub(input, "main.py")

	if count := strings.Count(got, "문제의 실행 코드에서"); count != 1 {
		t.Errorf("안내가 %d번 나왔다:\n%s", count, got)
	}
	if !strings.Contains(got, "ValueError: x") {
		t.Errorf("원인이 사라졌다:\n%s", got)
	}
}
