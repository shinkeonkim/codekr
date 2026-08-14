package sandbox

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
)

// 등록된 모든 런타임이 실제로 컴파일·실행되는지 확인한다.
//
// 각 런타임의 기본 템플릿은 "두 수의 합" 풀이로 맞춰 두었다. 그래서 이 테스트는
//
//	(1) 이미지·컴파일 명령·실행 명령이 맞는지
//	(2) 사용자에게 처음 보이는 코드가 실제로 돌아가는지
//
// 를 한 번에 검증한다. 깨진 기본 템플릿은 모든 사용자가 처음 마주치는 버그가 된다.
//
// 컨테이너 런타임과 이미지가 필요하므로 CODEKR_SANDBOX_TEST=1 일 때만 실행한다.
func TestLiveEveryRegisteredRuntimeSolvesSumOfTwo(t *testing.T) {
	box := newLiveSandbox(t)
	registry := loadSharedRegistry(t)

	for _, definition := range registry.All() {
		t.Run(definition.ID, func(t *testing.T) {
			t.Parallel()

			outcome, err := box.Run(context.Background(), Spec{
				Image:      definition.Image,
				SourceFile: definition.SourceFile,
				SourceCode: definition.Template,
				Stdin:      "1 2\n",
				Compile:    definition.Compile,
				Run:        definition.Run,
				// 러너와 같은 값을 넘긴다 — 여기서 빠뜨리면 시험이 실제 실행과 달라진다.
				Harness: definition.Harness,
				User:    definition.User,
				// **하네스 런타임은 DB 를 띄우고 시작한다** (#454). 제한은 컨테이너
				// 전체에 걸리므로 기동도 그 안이다 — MySQL 은 그것만 3초다.
				TimeLimitMs:      timeLimitFor(definition),
				MemoryLimitMb:    memoryLimitFor(definition),
				CompileTimeoutMs: 60000,
				MaxOutputBytes:   65536,
			})
			if err != nil {
				t.Fatalf("실행 실패: %v", err)
			}
			if strings.TrimSpace(outcome.Stdout) != "3" {
				t.Fatalf("기본 템플릿이 1 2 → 3 을 내지 못했습니다.\nstdout=%q\nstderr=%q\noutcome=%+v",
					outcome.Stdout, outcome.Stderr, outcome)
			}
		})
	}
}

func timeLimitFor(definition runtimes.Definition) int {
	if definition.Harness != "" {
		return 30000
	}
	return 5000
}

func memoryLimitFor(definition runtimes.Definition) int {
	if definition.Harness != "" {
		return 1024
	}
	return 512
}

// loadSharedRegistry 는 api 와 공유하는 실제 정의 파일을 읽는다 — 테스트 전용 사본을 두지 않는다.
func loadSharedRegistry(t *testing.T) *runtimes.Registry {
	t.Helper()
	path := filepath.Join("..", "..", "..", "..", "infra", "runtimes", "runtimes.yaml")
	if _, err := os.Stat(path); err != nil {
		t.Skipf("런타임 정의 파일을 찾을 수 없습니다: %v", err)
	}
	registry, err := runtimes.Load(path)
	if err != nil {
		t.Fatalf("런타임 정의 로드 실패: %v", err)
	}
	return registry
}
