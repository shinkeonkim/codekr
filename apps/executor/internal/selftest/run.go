package selftest

import (
	"context"
	"fmt"
	"io"

	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
)

// Result 는 검사 하나의 결과다.
type Result struct {
	Name string
	Err  error
}

// Passed 는 방어가 확인되었음을 뜻한다.
func (r Result) Passed() bool { return r.Err == nil }

// Run 은 모든 검사를 순서대로 돌린다. 하나가 실패해도 나머지를 계속한다 —
// 무엇이 뚫려 있는지 한 번에 다 알아야 판단할 수 있기 때문이다.
func Run(ctx context.Context, box sandbox.Sandbox) []Result {
	results := make([]Result, 0, len(Checks()))
	for _, check := range Checks() {
		results = append(results, Result{Name: check.Name, Err: run(ctx, box, check)})
	}
	return results
}

func run(ctx context.Context, box sandbox.Sandbox, check Check) error {
	spec := ProbeSpec(check)
	outcome, err := box.Run(ctx, spec)
	if err != nil {
		return fmt.Errorf("검사 실행 자체가 실패했습니다: %w", err)
	}
	return check.Assert(outcome)
}

// ProbeSpec 은 검사를 돌릴 실행 스펙을 만든다. 라이브 테스트도 같은 스펙을 쓴다.
func ProbeSpec(check Check) sandbox.Spec {
	spec := sandbox.Spec{
		Image:            ProbeImage,
		SourceFile:       "main.py",
		SourceCode:       check.Code,
		Run:              []string{"python3", "main.py"},
		TimeLimitMs:      5000,
		MemoryLimitMb:    256,
		CompileTimeoutMs: 15000,
		MaxOutputBytes:   64 * 1024,
	}
	if check.Spec != nil {
		check.Spec(&spec)
	}
	return spec
}

// Report 는 결과를 사람이 읽을 수 있게 쓰고, 하나라도 실패했는지 돌려준다.
func Report(w io.Writer, results []Result) bool {
	failed := false
	for _, result := range results {
		if result.Passed() {
			_, _ = fmt.Fprintf(w, "  ok    %s\n", result.Name)
			continue
		}
		failed = true
		_, _ = fmt.Fprintf(w, "  FAIL  %s\n        %v\n", result.Name, result.Err)
	}
	return failed
}
