// Package worker 는 실행 큐를 소비해 샌드박스 실행 결과를 응답 스트림으로 돌려준다.
package worker

import (
	"context"
	"fmt"

	"github.com/shinkeonkim/codekr/apps/executor/internal/runtimes"
	"github.com/shinkeonkim/codekr/apps/executor/internal/sandbox"
	contract "github.com/shinkeonkim/codekr/libs/gocontract"
)

// Runner 는 실행 작업 하나를 결과로 바꾼다. 큐/전송과 무관하게 단독으로 테스트할 수 있다.
type Runner struct {
	registry *runtimes.Registry
	// runtimeRegistry 는 런타임 이미지를 받아올 레지스트리다 (#96). 비면 원본에서 받는다.
	runtimeRegistry      string
	box                  sandbox.Sandbox
	compileTimeoutMs     int
	compileMemoryLimitMb int
	maxOutputBytes       int
}

// NewRunner 는 실행 러너를 만든다.
func NewRunner(
	registry *runtimes.Registry,
	box sandbox.Sandbox,
	compileTimeoutMs, compileMemoryLimitMb, maxOutputBytes int,
	runtimeRegistry string,
) *Runner {
	return &Runner{
		registry:             registry,
		runtimeRegistry:      runtimeRegistry,
		box:                  box,
		compileTimeoutMs:     compileTimeoutMs,
		compileMemoryLimitMb: compileMemoryLimitMb,
		maxOutputBytes:       maxOutputBytes,
	}
}

// Run 은 작업을 실행하고 결과를 만든다. 인프라 오류도 결과(SYSTEM_ERROR)로 표현해
// 호출자가 항상 응답을 돌려줄 수 있게 한다.
func (r *Runner) Run(ctx context.Context, job contract.ExecJob) contract.ExecResult {
	definition, found := r.registry.Find(job.RuntimeID)
	if !found {
		return systemError(job, fmt.Sprintf("지원하지 않는 런타임입니다: %s", job.RuntimeID))
	}
	// 손상되거나 버전이 다른 메시지가 그대로 샌드박스 설정이 되지 않게 막는다.
	if err := contract.ValidateLimits(job.TimeLimitMs, job.MemoryLimitMb); err != nil {
		return systemError(job, err.Error())
	}

	/*
		함수만 구현하는 문제 (#421).

		**파일을 나눠 놓는다.** 사용자 코드는 `solution.py` 로, 하네스는 `main.py` 로
		간다 — 한 파일로 합치면 오류의 줄 번호가 통째로 어긋나 사용자가 자기 코드의
		어디가 틀렸는지 알 수 없다.

		하네스를 지원하지 않는 런타임에 하네스가 실려 오면 **돌리지 않는다.** 짐작해서
		합치면 그 순간 위의 약속이 깨진다.
	*/
	sourceFile, runCommand := definition.SourceFile, definition.Run
	extraFiles := job.ExtraFiles
	if job.HarnessSource != "" {
		harness := definition.FunctionHarness
		if harness == nil {
			return systemError(job, fmt.Sprintf("이 런타임은 함수형 문제를 지원하지 않습니다: %s", job.RuntimeID))
		}
		sourceFile, runCommand = harness.SourceFile, harness.Run
		extraFiles = withHarness(extraFiles, harness.File, job.HarnessSource)
	}

	outcome, err := r.box.Run(ctx, sandbox.Spec{
		Image:      definition.ImageRef(r.runtimeRegistry),
		SourceFile: sourceFile,
		SourceCode: job.SourceCode,
		// 여러 파일로 낸 제출 (#457). 비면 SourceCode 하나로 돈다.
		SourceFiles:          job.SourceFiles,
		Stdin:                job.Stdin,
		Compile:              definition.Compile,
		Run:                  runCommand,
		Harness:              definition.Harness,
		User:                 definition.User,
		ExtraFiles:           extraFiles,
		TimeLimitMs:          job.TimeLimitMs,
		MemoryLimitMb:        job.MemoryLimitMb,
		CompileTimeoutMs:     r.compileTimeoutMs,
		CompileMemoryLimitMb: r.compileMemoryLimitMb,
		MaxOutputBytes:       r.maxOutputBytes,
	})
	if err != nil {
		return systemError(job, err.Error())
	}

	return contract.ExecResult{
		JobID:     job.JobID,
		Status:    statusOf(outcome),
		ExitCode:  outcome.ExitCode,
		Stdout:    outcome.Stdout,
		Stderr:    outcome.Stderr,
		RuntimeMs: outcome.RuntimeMs,
		MemoryKb:  outcome.MemoryKb,
		Truncated: outcome.Truncated,
	}
}

// statusOf 는 샌드박스 관찰 결과를 실행 상태로 옮긴다.
// 판정 우선순위는 컴파일 → 시간 → 메모리 → 런타임 오류 순이다.
func statusOf(outcome sandbox.Outcome) contract.ExecStatus {
	switch {
	case outcome.CompileFailed:
		return contract.StatusCompileError
	case outcome.TimedOut:
		return contract.StatusTimeLimitExceeded
	case outcome.OutOfMemory:
		return contract.StatusMemoryLimitExceeded
	case outcome.Truncated:
		return contract.StatusOutputLimitExceeded
	case outcome.ExitCode != 0:
		return contract.StatusRuntimeError
	default:
		return contract.StatusOK
	}
}

func systemError(job contract.ExecJob, message string) contract.ExecResult {
	return contract.ExecResult{
		JobID:  job.JobID,
		Status: contract.StatusSystemError,
		Stderr: message,
	}
}

/*
withHarness 는 하네스를 문제 자료 옆에 놓는다 (#421).

**원본 map 을 고치지 않는다.** 작업 메시지에서 온 값이라, 여기서 바꾸면 같은 작업을
다시 쓰는 자리(재시도 등)에서 무엇이 원본인지 알 수 없게 된다.
*/
func withHarness(files map[string]string, name, source string) map[string]string {
	merged := make(map[string]string, len(files)+1)
	for key, value := range files {
		merged[key] = value
	}
	merged[name] = source
	return merged
}
