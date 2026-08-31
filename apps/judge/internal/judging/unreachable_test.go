package judging

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

/*
실행기에 못 닿았을 때 사용자에게 무엇을 보여 주는가 (#741).

**`redis: nil` 이 화면에 그대로 떴다.** Redis 클라이언트가 "아무것도 없다" 를 뜻하는
값이라 사용자가 알 수 있는 것도 할 수 있는 것도 없는데, 자기 제출 옆에 붙어 있으니
자기 코드가 잘못된 줄 안다.
*/

func TestUnreachableMessageTellsWhatToDo(t *testing.T) {
	result := executorUnreachable()

	if result.Status != contract.StatusSystemError {
		t.Fatalf("실행기에 못 닿은 것은 시스템 오류다: %s", result.Status)
	}
	// **원인이 아니라 할 일을 적는다.** 사용자가 고칠 수 있는 것은 "다시 내기" 뿐이다.
	if !strings.Contains(result.Stderr, "다시 제출") {
		t.Fatalf("무엇을 하라는 말이 없습니다: %q", result.Stderr)
	}
	for _, leak := range []string{"redis", "nil", "context", "err", "timeout"} {
		if strings.Contains(strings.ToLower(result.Stderr), leak) {
			t.Fatalf("우리 인프라의 말이 새어 나갑니다(%q): %q", leak, result.Stderr)
		}
	}
}

/*
**다음에 유형이 늘 때 또 새지 않게 한다.**

여덟이 같은 모양으로 쓰고 있었다 — 하나를 고쳐도 아홉째가 생기면 그대로 돌아간다.
#681 이 제출 번호를 아홉 군데에서 한 곳으로 모은 것과 같은 자리다.
*/
func TestNoJudgePutsRawErrorOnTheScreen(t *testing.T) {
	files, err := filepath.Glob("*.go")
	if err != nil {
		t.Fatalf("파일 목록 실패: %v", err)
	}

	checked := 0
	for _, name := range files {
		if strings.HasSuffix(name, "_test.go") {
			continue
		}
		body, err := os.ReadFile(name)
		if err != nil {
			t.Fatalf("%s 읽기 실패: %v", name, err)
		}
		checked++
		// `Stderr` 는 화면까지 간다 (`stderrExcerpt`). 거기에 `err.Error()` 를 담으면
		// 우리 인프라의 말이 사용자에게 그대로 나간다.
		if strings.Contains(string(body), "Stderr: err.Error()") {
			t.Fatalf("%s 가 오류를 그대로 화면에 보냅니다. executorUnreachable() 을 쓰십시오", name)
		}
	}
	if checked < 5 {
		t.Fatalf("본 파일이 %d 개뿐입니다. 이 시험이 아무것도 안 보고 있습니다", checked)
	}
}
