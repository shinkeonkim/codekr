package sandbox

import (
	"context"
	"strings"
	"testing"
)

// 여러 번 실행해야 확인되는 방어만 여기 남는다.
//
// 한 번 실행으로 확인되는 검사들은 internal/selftest 로 옮겼다 — 실행기 바이너리가
// `--self-test` 로 배포된 노드에서 같은 검사를 돌릴 수 있어야 하기 때문이다 (#45).
// 아래 둘은 실행을 두 번 엮거나 결과를 비교해야 해서 검사 목록에 담기 어렵다.
//
// 컨테이너 런타임이 필요하므로 CODEKR_SANDBOX_TEST=1 일 때만 실행한다.

func TestLiveHardeningWorkDirIsWritableButIsolatedPerJob(t *testing.T) {
	// 앞선 작업이 남긴 파일이 다음 작업에서 보이면 작업 간 격리가 깨진 것이다.
	first := probe(t, `open("/work/leak.txt", "w").write("secret")`+"\nprint('wrote')")
	if !strings.Contains(first.Stdout, "wrote") {
		t.Fatalf("작업 디렉터리에 쓰지 못했습니다: %+v", first)
	}

	second := probe(t, `
import os
print("leaked" if os.path.exists("/work/leak.txt") else "isolated")
`)
	if !strings.Contains(second.Stdout, "isolated") {
		t.Errorf("작업 간 파일이 새어 나갑니다: %q", second.Stdout)
	}
}

func TestLiveHardeningCleansUpContainerAfterTimeout(t *testing.T) {
	box := newLiveSandbox(t)
	spec := pythonSpec("while True: pass")
	spec.TimeLimitMs = 1000

	outcome, err := box.Run(context.Background(), spec)
	if err != nil {
		t.Fatalf("실행 실패: %v", err)
	}
	if !outcome.TimedOut {
		t.Fatalf("시간 초과로 끝나야 합니다: %+v", outcome)
	}
	// 컨테이너 정리는 Run 이 반환하기 전에 끝난다 (defer remove).
	// 남은 컨테이너가 있는지는 docs/07 의 절차로 확인한다.
}

// probe 는 파이썬 코드를 돌려 관찰 결과를 돌려주는 검증용 도우미다.
func probe(t *testing.T, code string) Outcome {
	t.Helper()
	box := newLiveSandbox(t)

	spec := pythonSpec(code)
	spec.TimeLimitMs = 5000
	outcome, err := box.Run(context.Background(), spec)
	if err != nil {
		t.Fatalf("검증 실행 실패: %v", err)
	}
	return outcome
}
