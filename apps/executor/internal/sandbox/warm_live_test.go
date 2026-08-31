package sandbox

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"
)

/*
미리 받기가 **진짜 런타임에 닿는지** 본다 (#725).

`warm_test.go` 는 가짜 샌드박스를 넣는다 — 순서·취소·접두사를 보는 데는 맞지만, `Warm`
이 컨테이너 런타임에 무엇을 넘기는지는 보지 않는다. 그래서 운영에서 열넷이 전부

	namespace is required: failed precondition

으로 떨어지는 동안 CI 는 초록이었다. containerd 는 모든 호출에 네임스페이스를 요구하고,
`Run` 경로는 씌우는데 `Warm` 은 안 씌웠다.

**이 자리는 샌드박스 조건에서만 드러난다** — #709·#715·#716 과 같다.
*/
func TestLiveWarmPullsImage(t *testing.T) {
	box := newLiveSandbox(t)

	// 이 시험이 쓰는 다른 이미지와 같은 것을 고른다. 이미 있으면 `pull` 이 그것을
	// 그대로 쓰므로, **여기서 보는 것은 "받았는가" 가 아니라 "말이 통했는가" 다.**
	if err := box.Warm(context.Background(), "python:3.12-alpine"); err != nil {
		if strings.Contains(err.Error(), "namespace is required") {
			t.Fatalf("네임스페이스를 안 씌우고 불렀습니다 (#725): %v", err)
		}
		t.Fatalf("미리 받기 실패: %v", err)
	}
}

func TestLiveWarmReportsUnknownImage(t *testing.T) {
	// **없는 이미지는 오류여야 한다.** 조용히 성공하면 기동 로그가 "전부 받았다" 고
	// 말하고, 그 거짓말은 첫 제출이 느릴 때까지 드러나지 않는다.
	box := newLiveSandbox(t)

	started := time.Now()
	err := box.Warm(context.Background(), "codekr-이런-이미지는-없다:0")
	if err == nil {
		t.Fatal("없는 이미지인데 성공했다고 답했습니다")
	}
	if strings.Contains(err.Error(), "namespace is required") {
		t.Fatalf("없어서가 아니라 네임스페이스 때문에 실패했습니다 (#725): %v", err)
	}
	/*
		**빨리 말해야 한다** (#743).

		운영에서 레지스트리가 0초 만에 401 을 답했는데 실행기는 미리 받기 예산 30분을
		다 쓰고 `context deadline exceeded` 로 끝냈다 — 그 문구는 왜 못 받았는지
		아무것도 말하지 않는다. 그리고 미리 받기는 한 번에 하나씩이라, 한 장이 30분을
		붙들면 뒤엣것이 그만큼 늦어진다.
	*/
	if elapsed := time.Since(started); elapsed > probeTimeout+30*time.Second {
		t.Fatalf("못 받는다는 것을 아는 데 %s 걸렸습니다", elapsed.Round(time.Second))
	}
	// **빨리 끝난 것만으로는 부족하다.** 미리 물어보는 자리를 지나왔는지까지 본다 —
	// 그 자리가 빠져도 시간만 보는 검사는 통과할 수 있다.
	if !errors.Is(err, ErrPullRefused) {
		t.Fatalf("거부로 분류되지 않았습니다: %v", err)
	}
}
