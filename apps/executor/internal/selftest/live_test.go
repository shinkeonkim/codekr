package selftest_test

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
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

	// 검사도 정의 파일에서 이미지를 얻는다 (#218) — 실행기 바이너리와 같은 경로다.
	registry, err := runtimes.Load(runtimesPath())
	if err != nil {
		t.Fatalf("런타임 정의 로드 실패: %v", err)
	}
	probes, err := selftest.Probes(registry, os.Getenv("CODEKR_RUNTIME_REGISTRY"))
	if err != nil {
		t.Fatalf("검사용 런타임을 찾지 못했습니다: %v", err)
	}

	box, err := sandbox.New(os.Getenv("CODEKR_SANDBOX_RUNTIME"), os.Getenv("CODEKR_SECCOMP_PROFILE"))
	if err != nil {
		t.Fatalf("샌드박스 생성 실패: %v", err)
	}
	t.Cleanup(func() { _ = box.Close() })

	for _, result := range selftest.Run(context.Background(), box, probes) {
		t.Run(result.Name, func(t *testing.T) {
			if !result.Passed() {
				t.Error(result.Err)
			}
		})
	}
}

// runtimesPath 는 정의 파일 위치다.
//
// 상대 경로로 박아 두지 않는다: `go test ./...` 는 패키지 폴더에서 돌지만, CI 는
// `go test -c` 로 뽑은 바이너리를 apps/executor 에서 돌린다 — 기준 폴더가 다르다.
// 그래서 위로 올라가며 찾는다.
func runtimesPath() string {
	if path := os.Getenv("CODEKR_RUNTIMES_PATH"); path != "" {
		return path
	}
	dir, err := os.Getwd()
	if err != nil {
		return ""
	}
	for {
		candidate := filepath.Join(dir, "infra", "runtimes", "runtimes.yaml")
		if _, err := os.Stat(candidate); err == nil {
			return candidate
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return ""
		}
		dir = parent
	}
}
