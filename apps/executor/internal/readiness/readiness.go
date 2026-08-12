// Package readiness 는 정의 파일의 런타임이 이 노드에서 실제로 도는지 확인한다 (#218).
//
// **격리 검사(selftest)와 성격이 다르다.** 저쪽은 "신뢰할 수 없는 코드가 빠져나가는가"를
// 보고, 이쪽은 "정의 파일이 가리키는 것이 여기 준비돼 있는가"를 본다. 그래서 명령을
// 나눴다 — 하나로 합치면 실패했을 때 무엇이 잘못됐는지가 흐려진다.
//
// 이것이 없어서 놓친 적이 있다: 다이제스트를 고정한 뒤(#96) 채점이 전부 SYSTEM_ERROR
// 가 됐는데, 격리 검사 7개는 전부 통과했다 (PR #217).
package readiness

import (
	"context"
	"fmt"
	"io"
	"sort"

	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
)

// probeStdin 은 검사에 넣을 입력이다.
//
// 정의 파일의 template 은 모든 언어에서 "표준 입력의 정수를 더해 출력한다" 로 같다.
// 그래서 같은 입력을 주면 언어와 무관하게 같은 답이 나와야 한다 — 이미지가 있는지만이
// 아니라 컴파일러·인터프리터가 실제로 동작하는지까지 확인된다.
const probeStdin = "1 2 3\n"

// Result 는 런타임 하나의 준비 상태다.
type Result struct {
	RuntimeID string
	Image     string
	Err       error
}

// Passed 는 이 런타임으로 채점할 수 있음을 뜻한다.
func (r Result) Passed() bool { return r.Err == nil }

// Check 는 정의 파일의 모든 런타임을 하나씩 돌려 본다.
//
// 하나가 실패해도 나머지를 계속한다 — 어느 런타임이 준비되지 않았는지 한 번에 다
// 알아야 배포 여부를 판단할 수 있다.
func Check(
	ctx context.Context,
	box sandbox.Sandbox,
	registry *runtimes.Registry,
	registryPrefix string,
) []Result {
	definitions := registry.All()
	sort.Slice(definitions, func(i, j int) bool { return definitions[i].ID < definitions[j].ID })

	results := make([]Result, 0, len(definitions))
	for _, definition := range definitions {
		image := definition.ImageRef(registryPrefix)
		results = append(results, Result{
			RuntimeID: definition.ID,
			Image:     image,
			Err:       runOne(ctx, box, definition, image),
		})
	}
	return results
}

func runOne(
	ctx context.Context,
	box sandbox.Sandbox,
	definition runtimes.Definition,
	image string,
) error {
	outcome, err := box.Run(ctx, sandbox.Spec{
		Image:            image,
		SourceFile:       definition.SourceFile,
		SourceCode:       definition.Template,
		Stdin:            probeStdin,
		Compile:          definition.Compile,
		Run:              definition.Run,
		Harness:          definition.Harness,
		User:             definition.User,
		TimeLimitMs:      20000,
		MemoryLimitMb:    512,
		CompileTimeoutMs: 60000,
		MaxOutputBytes:   64 * 1024,
	})
	// 이미지를 못 받거나 컨테이너를 못 띄운 경우다. 정의 파일과 노드가 어긋나면 여기서 걸린다.
	if err != nil {
		return fmt.Errorf("실행 자체가 실패했습니다: %w", err)
	}
	switch {
	case outcome.CompileFailed:
		return fmt.Errorf("컴파일 실패: %s", firstLine(outcome.Stderr))
	case outcome.TimedOut:
		return fmt.Errorf("시간 제한(20초)을 넘겼습니다")
	case outcome.OutOfMemory:
		return fmt.Errorf("메모리 제한(512MB)을 넘겼습니다")
	case outcome.ExitCode != 0:
		return fmt.Errorf("종료 코드 %d: %s", outcome.ExitCode, firstLine(outcome.Stderr))
	}
	return nil
}

func firstLine(text string) string {
	for index, r := range text {
		if r == '\n' {
			return text[:index]
		}
	}
	if text == "" {
		return "(출력 없음)"
	}
	return text
}

// Report 는 결과를 사람이 읽을 수 있게 쓰고, 하나라도 실패했는지 돌려준다.
func Report(w io.Writer, results []Result) bool {
	failed := false
	for _, result := range results {
		if result.Passed() {
			_, _ = fmt.Fprintf(w, "  ok    %s\n", result.RuntimeID)
			continue
		}
		failed = true
		// 이미지 참조를 같이 적는다 — 어긋남을 고치려면 무엇을 받으려 했는지 알아야 한다.
		_, _ = fmt.Fprintf(w, "  FAIL  %s (%s)\n        %v\n", result.RuntimeID, result.Image, result.Err)
	}
	return failed
}
