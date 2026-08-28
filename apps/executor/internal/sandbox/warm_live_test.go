package sandbox

import (
	"context"
	"strings"
	"testing"
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

	err := box.Warm(context.Background(), "codekr-이런-이미지는-없다:0")
	if err == nil {
		t.Fatal("없는 이미지인데 성공했다고 답했습니다")
	}
	if strings.Contains(err.Error(), "namespace is required") {
		t.Fatalf("없어서가 아니라 네임스페이스 때문에 실패했습니다 (#725): %v", err)
	}
}
