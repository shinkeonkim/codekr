package selftest_test

import (
	"context"
	"os"
	"testing"

	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
	"github.com/shinkeonkim/codekr/apps/executor/internal/selftest"
)

// 샌드박스 방어 검증 (#37). 실행기 바이너리의 `--self-test` 와 **같은 검사**를 돌린다 —
// 정의가 하나뿐이라 CI 에서 통과한 것과 노드에서 통과한 것이 같은 것임을 보장한다.
//
// 컨테이너 런타임이 필요하므로 CODEKR_SANDBOX_TEST=1 일 때만 실행한다.
func TestLiveSandboxHardening(t *testing.T) {
	if os.Getenv("CODEKR_SANDBOX_TEST") != "1" {
		t.Skip("CODEKR_SANDBOX_TEST=1 일 때만 실행한다 (컨테이너 런타임 필요)")
	}

	box, err := sandbox.New(os.Getenv("CODEKR_SANDBOX_RUNTIME"))
	if err != nil {
		t.Fatalf("샌드박스 생성 실패: %v", err)
	}
	t.Cleanup(func() { _ = box.Close() })

	for _, result := range selftest.Run(context.Background(), box) {
		t.Run(result.Name, func(t *testing.T) {
			if !result.Passed() {
				t.Error(result.Err)
			}
		})
	}
}
